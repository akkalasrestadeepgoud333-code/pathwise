package app.pathwise.web.games;

import app.pathwise.core.games.BugHunt;
import app.pathwise.core.games.Game;
import app.pathwise.core.games.Game.GameAction;
import app.pathwise.core.games.Game.GameResult;
import app.pathwise.core.games.GameCatalog;
import app.pathwise.core.games.MatchPairs;
import app.pathwise.core.games.Replay;
import app.pathwise.core.games.TraversalRace;
import app.pathwise.core.learner.MasteryState;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.NotAuthenticatedException;
import app.pathwise.data.Tx;
import app.pathwise.data.catalog.CatalogDao;
import app.pathwise.data.catalog.CatalogDao.SkillRef;
import app.pathwise.data.games.GameDao;
import app.pathwise.data.games.GameDao.AttemptRow;
import app.pathwise.data.games.GameDao.GameRow;
import app.pathwise.data.games.GameDao.StepRow;
import app.pathwise.data.learner.EvidenceDao.MasteryRow;
import app.pathwise.web.catalog.CatalogService;
import app.pathwise.web.learner.LearnerService;
import app.pathwise.web.platform.Json;
import app.pathwise.web.platform.events.EventBus;

import java.io.IOException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Games: start (the server generates the puzzle from a fresh seed), play (in the browser), finish (the server
 * replays the action log with the authoritative rules) and results. Only verified games produce evidence, and
 * game evidence is informal: weight 0.3, capped per skill per day, never official, never part of a grade.
 */
public final class GameService {
    public static final String EVENT_COMPLETED = "game.completed";
    public static final double W_GAME = 0.3;
    /** At most this many game evidence rows per skill in any 24 hours. */
    public static final int DAILY_CAP = 12;

    private final Tx tx;
    private final LearnerService learner;
    private final CatalogService catalog;
    private final EventBus events;
    private final Clock clock;
    private final GameDao dao = new GameDao();
    private final CatalogDao catalogDao = new CatalogDao();
    private final SecureRandom random = new SecureRandom();

    public GameService(Tx tx, LearnerService learner, CatalogService catalog, EventBus events, Clock clock) {
        this.tx = tx;
        this.learner = learner;
        this.catalog = catalog;
        this.events = events;
        this.clock = clock;
    }

    public record Card(String key, String title, String template, String templateTitle, String description, String topicKey,
                       String topicTitle, int difficulty, Integer estMinutes, int plays, String best, UUID lastAttemptId) {}

    public record Intro(Card card, String teaches, List<String> skills) {}

    public record PlayView(UUID attemptId, String key, String title, String template, String puzzleJson, boolean relaxed,
                           long minGapMillis) {}

    public record Step(String skillTitle, boolean correct, int percent, String note) {}

    public record SkillLevel(UUID skillId, String title, String topicTitle, String level) {}

    /** One thing to look back at: a round, a pair or a snippet. {@code code} lines may be empty. */
    public record Review(String heading, List<String> code, Integer highlightLine, String detail) {}

    public record Result(String key, String title, String template, UUID attemptId, String status, int score, int maxScore,
                         int percent, int accuracyPercent, String durationLabel, boolean relaxed, List<Step> steps,
                         List<SkillLevel> skills, List<Review> review, int capped, String rejectReason) {}

    public record FinishOutcome(boolean accepted, String message) {}

    /** The game can't be found, or the attempt isn't this student's. */
    public static final class NotFoundException extends RuntimeException {
        public NotFoundException() {
            super("Not found");
        }
    }

    // ------------------------------------------------------------------ gallery

    public List<Card> list(Actor actor) {
        requireUser(actor);
        Map<UUID, CatalogDao.TopicRow> topics = topics();
        return tx.read(c -> {
            Map<UUID, GameDao.Stats> stats = dao.stats(c, actor.userId());
            return dao.published(c).stream().map(g -> card(g, topics, stats.get(g.id()))).toList();
        });
    }

    public Optional<Intro> intro(Actor actor, String key) {
        requireUser(actor);
        Map<UUID, CatalogDao.TopicRow> topics = topics();
        return tx.read(c -> {
            Optional<GameRow> g = dao.findByKey(c, key).filter(x -> "published".equals(x.status()));
            if (g.isEmpty()) {
                return Optional.<Intro>empty();
            }
            GameDao.Stats stats = dao.stats(c, actor.userId()).get(g.get().id());
            Map<String, CatalogDao.SkillRow> skills = skillsByKey(c, g.get().subjectId());
            List<String> trained = skillKeys(g.get()).stream().map(skills::get).filter(s -> s != null)
                    .map(CatalogDao.SkillRow::title).distinct().toList();
            String teaches = GameCatalog.find(g.get().template()).map(GameCatalog.Template::teaches).orElse("");
            return Optional.of(new Intro(card(g.get(), topics, stats), teaches, trained));
        });
    }

    private Card card(GameRow g, Map<UUID, CatalogDao.TopicRow> topics, GameDao.Stats stats) {
        CatalogDao.TopicRow topic = g.topicId() == null ? null : topics.get(g.topicId());
        String best = stats == null || stats.bestScore() == null ? null : fmt(stats.bestScore()) + " / " + fmt(stats.bestMax());
        return new Card(g.key(), g.title(), g.template(), GameCatalog.find(g.template()).map(GameCatalog.Template::title).orElse(g.template()),
                g.description(), topic == null ? null : topic.key(), topic == null ? null : topic.title(), g.difficulty(), g.estMinutes(),
                stats == null ? 0 : stats.plays(), best, stats == null ? null : stats.lastAttemptId());
    }

    private Map<UUID, CatalogDao.TopicRow> topics() {
        Map<UUID, CatalogDao.TopicRow> out = new HashMap<>();
        for (var s : catalog.subjects()) {
            catalog.graph(s.key()).ifPresent(gr -> gr.topics().forEach(t -> out.put(t.id(), t)));
        }
        return out;
    }

    // ------------------------------------------------------------------ play

    public UUID start(Actor actor, String key, boolean relaxed) {
        requireUser(actor);
        Instant now = clock.instant();
        return tx.call(c -> {
            GameRow g = dao.findByKey(c, key).filter(x -> "published".equals(x.status())).orElseThrow(NotFoundException::new);
            Game<?, ?> game = GameCatalog.find(g.template()).orElseThrow(NotFoundException::new).game();
            long seed = random.nextLong();
            Object puzzle = generate(g, seed);
            return dao.createAttempt(c, g.id(), actor.userId(), seed, game.version(), Json.write(puzzle), relaxed, now);
        });
    }

    public Optional<PlayView> play(Actor actor, UUID attemptId) {
        requireUser(actor);
        return tx.read(c -> {
            Optional<AttemptRow> t = dao.findAttempt(c, attemptId, actor.userId()).filter(x -> "in_progress".equals(x.status()));
            if (t.isEmpty()) {
                return Optional.<PlayView>empty();
            }
            GameRow g = dao.findById(c, t.get().activityId()).orElseThrow();
            long gap = GameCatalog.find(g.template()).map(x -> x.game().minGapMillis()).orElse(300L);
            return Optional.of(new PlayView(t.get().id(), g.key(), g.title(), g.template(), t.get().puzzleJson(), t.get().relaxed(), gap));
        });
    }

    /**
     * Replays and records a finished game, once. Returns whether it was accepted. Calling it again for a
     * finished attempt is harmless and reports the stored outcome.
     */
    public FinishOutcome finish(Actor actor, UUID attemptId, List<GameAction> actions) {
        requireUser(actor);
        Instant now = clock.instant();
        return tx.call(c -> {
            Optional<AttemptRow> locked = dao.lockOpen(c, attemptId, actor.userId());
            if (locked.isEmpty()) {
                AttemptRow done = dao.findAttempt(c, attemptId, actor.userId()).orElseThrow(NotFoundException::new);
                return new FinishOutcome("completed".equals(done.status()), done.rejectReason());
            }
            AttemptRow t = locked.get();
            GameRow g = dao.findById(c, t.activityId()).orElseThrow();
            String log = Json.write(actions == null ? List.of() : actions);
            Replay.Verdict verdict = verify(g.template(), t.puzzleJson(), actions,
                    Duration.between(t.startedAt(), now).toMillis());
            if (!verdict.accepted()) {
                dao.reject(c, t.id(), log, verdict.reason());
                return new FinishOutcome(false, verdict.reason());
            }
            GameResult r = verdict.result();
            dao.complete(c, t.id(), log, r.score(), r.maxScore(), r.accuracy(), verdict.durationMs(), now);
            Map<String, CatalogDao.SkillRow> skills = skillsByKey(c, g.subjectId());
            Set<UUID> touched = new LinkedHashSet<>();
            Instant since = now.minus(Duration.ofHours(24));
            Map<UUID, Integer> todayCount = new HashMap<>();
            int position = 0;
            for (Game.StepEvent e : r.events()) {
                CatalogDao.SkillRow skill = skills.get(e.skillKey());
                if (skill == null) {
                    continue; // the skill was removed from the catalogue since the game started
                }
                long item = dao.insertStep(c, t.id(), position++, skill.id(), e.correct(), e.partial(), e.note(), now);
                int already = todayCount.computeIfAbsent(skill.id(), id -> {
                    try {
                        return dao.gameEvidenceSince(c, actor.userId(), id, since);
                    } catch (SQLException ex) {
                        throw new IllegalStateException(ex);
                    }
                });
                if (already < DAILY_CAP) {
                    learner.addEvidence(c, actor.userId(), skill.id(), "game", item, e.partial(), W_GAME, g.difficulty(), 0, now);
                    todayCount.put(skill.id(), already + 1);
                    touched.add(skill.id());
                }
            }
            learner.recompute(c, actor.userId(), touched, now);
            events.publish(c, EVENT_COMPLETED, Map.of("userId", actor.userId().toString(), "activityId", g.id().toString(),
                    "attemptId", t.id().toString(), "template", g.template(), "score", r.score(), "accuracy", r.accuracy()));
            return new FinishOutcome(true, null);
        });
    }

    // ------------------------------------------------------------------ results

    public Optional<Result> result(Actor actor, UUID attemptId) {
        requireUser(actor);
        return tx.read(c -> {
            Optional<AttemptRow> found = dao.findAttempt(c, attemptId, actor.userId())
                    .filter(x -> "completed".equals(x.status()) || "rejected".equals(x.status()));
            if (found.isEmpty()) {
                return Optional.<Result>empty();
            }
            AttemptRow t = found.get();
            GameRow g = dao.findById(c, t.activityId()).orElseThrow();
            List<StepRow> steps = dao.steps(c, t.id());
            Map<UUID, SkillRef> refs = catalogDao.skillRefs(c, steps.stream().map(StepRow::skillId).collect(Collectors.toSet()))
                    .stream().collect(Collectors.toMap(SkillRef::id, Function.identity()));
            List<Step> stepViews = steps.stream().map(s -> new Step(refs.containsKey(s.skillId()) ? refs.get(s.skillId()).title() : "",
                    s.correct(), (int) Math.round(s.partial() * 100), s.note())).toList();
            Map<UUID, MasteryRow> mastery = learner.mastery(actor);
            List<SkillLevel> levels = new ArrayList<>();
            for (SkillRef s : refs.values()) {
                MasteryRow m = mastery.get(s.id());
                levels.add(new SkillLevel(s.id(), s.title(), s.topicTitle(),
                        m == null ? MasteryState.NOT_STARTED.label() : MasteryState.fromDb(m.state()).label()));
            }
            levels.sort((a, b) -> a.title().compareTo(b.title()));
            int evidence = countEvidence(c, t.id());
            int score = t.score() == null ? 0 : (int) Math.round(t.score());
            int max = t.maxScore() == null ? 0 : (int) Math.round(t.maxScore());
            return Optional.of(new Result(g.key(), g.title(), g.template(), t.id(), t.status(), score, max,
                    max == 0 ? 0 : Math.round(100f * score / max), t.accuracy() == null ? 0 : (int) Math.round(t.accuracy() * 100),
                    duration(t.durationMs()), t.relaxed(), stepViews, levels, review(g.template(), t.puzzleJson()),
                    Math.max(0, steps.size() - evidence), plainReason(t.rejectReason())));
        });
    }

    private static int countEvidence(Connection c, UUID attemptId) throws SQLException {
        try (var ps = c.prepareStatement("""
                select count(*) from learning_evidence where attempt_item_id in (select id from activity_attempt_items where attempt_id = ?)
                """)) {
            ps.setObject(1, attemptId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    static String plainReason(String reason) {
        if (reason == null) {
            return null;
        }
        if (reason.contains("not finished")) {
            return "The game wasn't finished.";
        }
        if (reason.contains("faster") || reason.contains("time")) {
            return "The timing of the moves didn't look like real play.";
        }
        return "The recorded moves didn't match the game's rules.";
    }

    // ------------------------------------------------------------------ per-template plumbing

    static Object generate(GameRow g, long seed) {
        try {
            return switch (g.template()) {
                case TraversalRace.KEY -> new TraversalRace().generate(Json.mapper().readValue(g.configJson(), TraversalRace.Config.class), seed);
                case MatchPairs.KEY -> new MatchPairs().generate(Json.mapper().readValue(g.configJson(), MatchPairs.Config.class), seed);
                case BugHunt.KEY -> new BugHunt().generate(Json.mapper().readValue(g.configJson(), BugHunt.Config.class), seed);
                default -> throw new NotFoundException();
            };
        } catch (IOException e) {
            throw new IllegalStateException("Game config is not readable: " + g.key(), e);
        }
    }

    static Replay.Verdict verify(String template, String puzzleJson, List<GameAction> actions, long elapsedMs) {
        try {
            return switch (template) {
                case TraversalRace.KEY -> Replay.verify(new TraversalRace(), Json.mapper().readValue(puzzleJson, TraversalRace.Puzzle.class), actions, elapsedMs);
                case MatchPairs.KEY -> Replay.verify(new MatchPairs(), Json.mapper().readValue(puzzleJson, MatchPairs.Puzzle.class), actions, elapsedMs);
                case BugHunt.KEY -> Replay.verify(new BugHunt(), Json.mapper().readValue(puzzleJson, BugHunt.Puzzle.class), actions, elapsedMs);
                default -> throw new NotFoundException();
            };
        } catch (IOException e) {
            throw new IllegalStateException("Stored puzzle is not readable", e);
        }
    }

    static List<Review> review(String template, String puzzleJson) {
        try {
            List<Review> out = new ArrayList<>();
            switch (template) {
                case TraversalRace.KEY -> {
                    TraversalRace.Puzzle p = Json.mapper().readValue(puzzleJson, TraversalRace.Puzzle.class);
                    int i = 1;
                    for (TraversalRace.Round r : p.rounds()) {
                        out.add(new Review("Round " + i++ + ": " + orderLabel(r.order()), List.of(), null,
                                "Correct order: " + r.answer().stream().map(String::valueOf).collect(Collectors.joining(", "))
                                        + ". " + orderRule(r.order())));
                    }
                }
                case MatchPairs.KEY -> {
                    MatchPairs.Puzzle p = Json.mapper().readValue(puzzleJson, MatchPairs.Puzzle.class);
                    p.items().forEach(it -> out.add(new Review(it.left(), List.of(), null, it.right())));
                }
                case BugHunt.KEY -> {
                    BugHunt.Puzzle p = Json.mapper().readValue(puzzleJson, BugHunt.Puzzle.class);
                    for (BugHunt.Snippet s : p.rounds()) {
                        out.add(new Review(s.title(), List.of(s.code().split("\n", -1)), s.bugLine(),
                                "Line " + s.bugLine() + ": " + BugHunt.CLASSES.get(s.bugClass()).toLowerCase(Locale.ROOT) + ". " + s.explanation()));
                    }
                }
                default -> { }
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("Stored puzzle is not readable", e);
        }
    }

    private static List<String> skillKeys(GameRow g) {
        try {
            return switch (g.template()) {
                case TraversalRace.KEY -> List.copyOf(Json.mapper().readValue(g.configJson(), TraversalRace.Config.class).skills().values());
                case MatchPairs.KEY -> Json.mapper().readValue(g.configJson(), MatchPairs.Config.class).pairs().stream().map(MatchPairs.Pair::skill).toList();
                case BugHunt.KEY -> Json.mapper().readValue(g.configJson(), BugHunt.Config.class).snippets().stream().map(BugHunt.Snippet::skill).toList();
                default -> List.of();
            };
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, CatalogDao.SkillRow> skillsByKey(Connection c, UUID subjectId) throws SQLException {
        Map<String, CatalogDao.SkillRow> out = new LinkedHashMap<>();
        catalogDao.skillsOfSubject(c, subjectId).forEach(s -> out.put(s.key(), s));
        return out;
    }

    static String orderLabel(String order) {
        return switch (order) {
            case "preorder" -> "Preorder";
            case "inorder" -> "Inorder";
            case "postorder" -> "Postorder";
            default -> "Level order";
        };
    }

    static String orderRule(String order) {
        return switch (order) {
            case "preorder" -> "Node first, then its left subtree, then its right subtree.";
            case "inorder" -> "Left subtree, then the node, then the right subtree (for a BST this is sorted order).";
            case "postorder" -> "Left subtree, then right subtree, then the node itself.";
            default -> "Level by level from the root, left to right on each level.";
        };
    }

    private static String duration(Integer ms) {
        if (ms == null) {
            return null;
        }
        long s = Math.round(ms / 1000.0);
        return s < 60 ? s + " s" : (s / 60) + " min " + (s % 60) + " s";
    }

    private static String fmt(Double v) {
        return v == null ? "0" : Long.toString(Math.round(v));
    }

    private static void requireUser(Actor actor) {
        if (actor == null || !actor.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }
    }
}
