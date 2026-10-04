package app.pathwise.web.practice;

import app.pathwise.core.learner.MasteryState;
import app.pathwise.core.questions.Grader;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.NotAuthenticatedException;
import app.pathwise.data.Tx;
import app.pathwise.data.catalog.CatalogDao.SkillRef;
import app.pathwise.data.learner.AttemptDao;
import app.pathwise.data.learner.AttemptDao.AttemptRow;
import app.pathwise.data.learner.AttemptDao.ItemRow;
import app.pathwise.data.learner.EvidenceDao.MasteryRow;
import app.pathwise.data.learner.RecommendationDao;
import app.pathwise.data.questions.QuestionDao.QuestionRow;
import app.pathwise.web.catalog.CatalogService;
import app.pathwise.web.content.ContentService;
import app.pathwise.web.learner.LearnerService;
import app.pathwise.web.questions.QuestionService;
import app.pathwise.web.questions.QuestionService.Feedback;
import app.pathwise.web.questions.QuestionService.QuestionView;
import app.pathwise.web.questions.QuestionService.Response;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Practice sessions. Questions are chosen near the learner's current level; each answer is graded on
 * the server and appended as learning evidence (never as an official result), and the learner model is
 * updated in the same transaction.
 */
public final class PracticeService {

    public enum Mode {
        PRACTICE("practice", 5), CHALLENGE("challenge", 3), REVIEW("review", 3), QUICK("practice", 5);

        final String kind;
        final int size;

        Mode(String kind, int size) {
            this.kind = kind;
            this.size = size;
        }
    }

    /** Thrown when there is nothing to practise for the requested skills. */
    public static final class NoQuestionsException extends RuntimeException {
        public NoQuestionsException() {
            super("No practice questions are available for this yet.");
        }
    }

    public record QuestionStep(int position, int total, QuestionView question, List<String> hints, boolean moreHints) {}

    public record AnswerResult(int position, int total, Feedback feedback, boolean last) {}

    public record SkillChange(String skillTitle, String topicTitle, String subjectKey, String topicKey,
                              String before, String after, boolean improved, boolean declined) {}

    public record Summary(int correct, int total, double score, List<SkillChange> skills, String kind) {}

    /** One of: the next question, or the finished summary. */
    public record SessionView(UUID attemptId, String kind, String title, QuestionStep step, Summary summary) {}

    private final Tx tx;
    private final QuestionService questions;
    private final LearnerService learner;
    private final CatalogService catalog;
    private final ContentService content;
    private final Clock clock;
    private final AttemptDao attempts = new AttemptDao();
    private final RecommendationDao recommendations = new RecommendationDao();

    public PracticeService(Tx tx, QuestionService questions, LearnerService learner, CatalogService catalog,
                           ContentService content, Clock clock) {
        this.tx = tx;
        this.questions = questions;
        this.learner = learner;
        this.catalog = catalog;
        this.content = content;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ start

    public UUID start(Actor actor, Mode mode, UUID skillId, UUID recommendationId) {
        requireUser(actor);
        List<UUID> skills = mode == Mode.QUICK || skillId == null ? quickSkills(actor) : List.of(skillId);
        return startForSkills(actor, mode, skills, mode == Mode.QUICK ? null : skillId, recommendationId);
    }

    /** Practice across several skills (e.g. "I already know this" for a whole module). */
    public UUID startForSkills(Actor actor, Mode mode, List<UUID> skills, UUID primarySkill, UUID recommendationId) {
        requireUser(actor);
        Instant now = clock.instant();
        if (skills.isEmpty()) {
            throw new NoQuestionsException();
        }
        List<QuestionRow> pool = questions.forSkills(skills);
        if (mode == Mode.CHALLENGE) {
            List<QuestionRow> hard = pool.stream().filter(q -> q.difficulty() >= 4).toList();
            pool = hard.isEmpty() ? pool : hard;
        }
        if (pool.isEmpty()) {
            throw new NoQuestionsException();
        }
        Map<UUID, MasteryRow> mastery = learner.mastery(actor);
        double estimate = skills.stream().mapToDouble(s -> mastery.containsKey(s) ? mastery.get(s).estimate() : 0.4)
                .average().orElse(0.4);
        Map<UUID, Instant> recent = tx.read(c -> attempts.recentlyAnswered(c, actor.userId(), Duration.ofDays(14), now));
        List<UUID> chosen = select(pool, mode, estimate, recent, now, mode.size, actor.userId().hashCode() ^ now.hashCode());
        return tx.call(c -> attempts.create(c, actor.userId(), mode.kind, primarySkill, null, recommendationId, chosen, now));
    }

    /**
     * Picks questions closest to the target difficulty, preferring ones not answered recently, then
     * orders them easiest first for a gentle ramp. Deterministic for a given seed.
     */
    static List<UUID> select(List<QuestionRow> pool, Mode mode, double estimate, Map<UUID, Instant> recent,
                             Instant now, int size, long seed) {
        double target = switch (mode) {
            case CHALLENGE -> 4.5;
            case REVIEW -> estimate >= 0.7 ? 3 : 2;
            default -> estimate < 0.45 ? 1.5 : estimate < 0.7 ? 2.5 : 3.5;
        };
        Comparator<QuestionRow> order = Comparator
                .comparingInt((QuestionRow q) -> recent.containsKey(q.id())
                        && recent.get(q.id()).isAfter(now.minus(Duration.ofHours(24))) ? 1 : 0)
                .thenComparingDouble(q -> Math.abs(q.difficulty() - target))
                .thenComparing(q -> recent.getOrDefault(q.id(), Instant.EPOCH))
                .thenComparingLong(q -> (q.key().hashCode() * 31L) ^ seed);
        return pool.stream().sorted(order).limit(size)
                .sorted(Comparator.comparingInt(QuestionRow::difficulty))
                .map(QuestionRow::id).toList();
    }

    /** Skills that most need work; falls back to the skills of the module studied most recently. */
    List<UUID> quickSkills(Actor actor) {
        Map<UUID, MasteryRow> mastery = learner.mastery(actor);
        List<UUID> weak = mastery.values().stream()
                .filter(m -> List.of("needs_support", "developing", "exploring").contains(m.state()))
                .sorted(Comparator.comparingInt((MasteryRow m) -> stateRank(m.state())).thenComparingDouble(MasteryRow::estimate))
                .limit(3).map(MasteryRow::skillId).toList();
        if (!weak.isEmpty()) {
            return weak;
        }
        return content.mostRecent(actor).map(r -> content.skillIdsOfModule(r.moduleId())).orElse(List.of());
    }

    static int rank(MasteryState s) {
        return switch (s) {
            case DEVELOPING -> 1;
            case PROFICIENT -> 2;
            case MASTERED -> 3;
            default -> 0;
        };
    }

    private static int stateRank(String state) {
        return switch (state) {
            case "needs_support" -> 0;
            case "developing" -> 1;
            default -> 2;
        };
    }

    // ------------------------------------------------------------------ session

    public Optional<SessionView> view(Actor actor, UUID attemptId) {
        requireUser(actor);
        Optional<AttemptRow> attempt = tx.read(c -> attempts.find(c, attemptId, actor.userId()));
        if (attempt.isEmpty() || "module_check".equals(attempt.get().kind())) {
            return Optional.empty();
        }
        List<ItemRow> items = tx.read(c -> attempts.items(c, attemptId));
        Map<UUID, QuestionRow> qs = questions.byIds(items.stream().map(ItemRow::questionId).toList()).stream()
                .collect(Collectors.toMap(QuestionRow::id, Function.identity()));
        String title = title(attempt.get(), qs.values());
        Optional<ItemRow> next = items.stream().filter(i -> i.answeredAt() == null).findFirst();
        if (next.isPresent()) {
            ItemRow item = next.get();
            QuestionRow q = qs.get(item.questionId());
            List<String> hints = QuestionService.hints(q);
            QuestionStep step = new QuestionStep(item.position() + 1, items.size(), QuestionService.view(q, actor.userId()),
                    hints.subList(0, Math.min(item.hintsUsed(), hints.size())), item.hintsUsed() < hints.size());
            return Optional.of(new SessionView(attemptId, attempt.get().kind(), title, step, null));
        }
        return Optional.of(new SessionView(attemptId, attempt.get().kind(), title, null, finish(actor, attempt.get(), items, qs)));
    }

    public boolean revealHint(Actor actor, UUID attemptId, int position) {
        requireUser(actor);
        return tx.call(c -> {
            Optional<AttemptRow> attempt = attempts.find(c, attemptId, actor.userId());
            if (attempt.isEmpty()) {
                return false;
            }
            ItemRow item = item(attempts.items(c, attemptId), position);
            if (item == null) {
                return false;
            }
            int max = QuestionService.hints(questions.byIds(List.of(item.questionId())).get(0)).size();
            return attempts.revealHint(c, item.id(), max) >= 0;
        });
    }

    /** Grades the answer to the question at {@code position} (1-based). Empty if it isn't the user's or is already answered. */
    public Optional<AnswerResult> answer(Actor actor, UUID attemptId, int position, Response response) {
        requireUser(actor);
        Instant now = clock.instant();
        return tx.call(c -> {
            Optional<AttemptRow> attempt = attempts.find(c, attemptId, actor.userId());
            if (attempt.isEmpty() || !"in_progress".equals(attempt.get().status())) {
                return Optional.<AnswerResult>empty();
            }
            List<ItemRow> items = attempts.items(c, attemptId);
            ItemRow item = item(items, position);
            if (item == null || item.answeredAt() != null) {
                return Optional.<AnswerResult>empty();
            }
            QuestionRow q = questions.byIds(List.of(item.questionId())).get(0);
            Grader.Result result = QuestionService.grade(q, response);
            Map<UUID, Instant> recent = attempts.recentlyAnswered(c, actor.userId(), Duration.ofHours(24), now);
            attempts.answer(c, item.id(), response.toJson(), result.correct(), result.partial(), now);
            String kind = attempt.get().kind();
            double weight = switch (kind) {
                case "challenge" -> LearnerService.W_CHALLENGE;
                default -> recent.containsKey(q.id()) ? LearnerService.W_REPEAT : LearnerService.W_PRACTICE;
            };
            for (UUID skill : q.skillIds()) {
                learner.addEvidence(c, actor.userId(), skill, kind, item.id(), result.partial(), weight, q.difficulty(),
                        item.hintsUsed(), now);
            }
            learner.recompute(c, actor.userId(), q.skillIds(), now);
            boolean last = items.stream().filter(i -> i.answeredAt() == null).count() == 1;
            return Optional.of(new AnswerResult(position, items.size(), QuestionService.feedback(q, response, result, actor.userId()), last));
        });
    }

    /** Feedback for an already answered question (for the page shown after answering). */
    public Optional<AnswerResult> answered(Actor actor, UUID attemptId, int position) {
        requireUser(actor);
        Optional<AttemptRow> attempt = tx.read(c -> attempts.find(c, attemptId, actor.userId()));
        if (attempt.isEmpty()) {
            return Optional.empty();
        }
        List<ItemRow> items = tx.read(c -> attempts.items(c, attemptId));
        ItemRow item = item(items, position);
        if (item == null || item.answeredAt() == null) {
            return Optional.empty();
        }
        QuestionRow q = questions.byIds(List.of(item.questionId())).get(0);
        Response r = Response.fromJson(item.responseJson());
        Grader.Result result = new Grader.Result(Boolean.TRUE.equals(item.correct()), item.partial() == null ? 0 : item.partial());
        boolean last = items.stream().noneMatch(i -> i.answeredAt() == null);
        return Optional.of(new AnswerResult(position, items.size(), QuestionService.feedback(q, r, result, actor.userId()), last));
    }

    private Summary finish(Actor actor, AttemptRow attempt, List<ItemRow> items, Map<UUID, QuestionRow> qs) {
        Instant now = clock.instant();
        int correct = (int) items.stream().filter(i -> Boolean.TRUE.equals(i.correct())).count();
        double score = items.stream().mapToDouble(i -> i.partial() == null ? 0 : i.partial()).sum();
        if ("in_progress".equals(attempt.status())) {
            tx.run(c -> {
                attempts.complete(c, attempt.id(), score, items.size(), now);
                if (attempt.recommendationId() != null) {
                    recommendations.markCompleted(c, attempt.recommendationId(), now);
                }
            });
        }
        Set<UUID> skillIds = new LinkedHashSet<>();
        items.forEach(i -> skillIds.addAll(qs.get(i.questionId()).skillIds()));
        Map<UUID, MasteryRow> mastery = learner.mastery(actor);
        List<SkillChange> changes = new ArrayList<>();
        for (SkillRef s : catalog.skillRefs(skillIds)) {
            MasteryState before = learner.stateBefore(actor, s.id(), attempt.startedAt());
            MasteryState after = mastery.containsKey(s.id()) ? MasteryState.fromDb(mastery.get(s.id()).state()) : MasteryState.NOT_STARTED;
            // Rank by meaning: "Needs practice" is a firmer estimate than "Getting started", not an improvement.
            changes.add(new SkillChange(s.title(), s.topicTitle(), s.subjectKey(), s.topicKey(), before.label(), after.label(),
                    rank(after) > rank(before), rank(after) < rank(before)));
        }
        return new Summary(correct, items.size(), Math.round(score * 10) / 10.0, changes, attempt.kind());
    }

    private String title(AttemptRow attempt, java.util.Collection<QuestionRow> qs) {
        String prefix = switch (attempt.kind()) {
            case "challenge" -> "Challenge";
            case "review" -> "Review";
            default -> "Practice";
        };
        if (attempt.skillId() != null) {
            return catalog.skillRefs(List.of(attempt.skillId())).stream().findFirst()
                    .map(s -> prefix + ": " + s.title()).orElse(prefix);
        }
        return "Quick practice";
    }

    private static ItemRow item(List<ItemRow> items, int position) {
        return items.stream().filter(i -> i.position() + 1 == position).findFirst().orElse(null);
    }

    private static void requireUser(Actor actor) {
        if (actor == null || !actor.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }
    }

    // ------------------------------------------------------------------ module checks

    public record CheckItem(QuestionView question, Feedback feedback) {}

    /**
     * Grades a module check section in one go (one attempt, one evidence row per question skill, weight
     * 0.5). Unanswered questions count as incorrect. Returns the attempt id for showing results.
     */
    public UUID submitCheck(Actor actor, UUID sectionId, List<String> questionKeys, Map<UUID, Response> responses) {
        requireUser(actor);
        Instant now = clock.instant();
        List<QuestionRow> qs = orderByKeys(questions.byKeys(questionKeys), questionKeys);
        return tx.call(c -> {
            UUID attemptId = attempts.create(c, actor.userId(), "module_check", null, sectionId, null,
                    qs.stream().map(QuestionRow::id).toList(), now);
            List<ItemRow> items = attempts.items(c, attemptId);
            Set<UUID> skills = new LinkedHashSet<>();
            double score = 0;
            for (ItemRow item : items) {
                QuestionRow q = qs.stream().filter(x -> x.id().equals(item.questionId())).findFirst().orElseThrow();
                Response r = responses.getOrDefault(q.id(), new Response(Set.of(), ""));
                Grader.Result result = QuestionService.grade(q, r);
                attempts.answer(c, item.id(), r.toJson(), result.correct(), result.partial(), now);
                score += result.partial();
                for (UUID skill : q.skillIds()) {
                    learner.addEvidence(c, actor.userId(), skill, "module_check", item.id(), result.partial(),
                            LearnerService.W_MODULE_CHECK, q.difficulty(), 0, now);
                    skills.add(skill);
                }
            }
            attempts.complete(c, attemptId, score, items.size(), now);
            learner.recompute(c, actor.userId(), skills, now);
            return attemptId;
        });
    }

    /** Results of a check attempt, only for its owner. */
    public Optional<List<CheckItem>> checkResults(Actor actor, UUID attemptId) {
        requireUser(actor);
        Optional<AttemptRow> attempt = tx.read(c -> attempts.find(c, attemptId, actor.userId()));
        if (attempt.isEmpty() || !"module_check".equals(attempt.get().kind())) {
            return Optional.empty();
        }
        List<ItemRow> items = tx.read(c -> attempts.items(c, attemptId));
        Map<UUID, QuestionRow> qs = questions.byIds(items.stream().map(ItemRow::questionId).toList()).stream()
                .collect(Collectors.toMap(QuestionRow::id, Function.identity()));
        return Optional.of(items.stream().map(i -> {
            QuestionRow q = qs.get(i.questionId());
            Grader.Result result = new Grader.Result(Boolean.TRUE.equals(i.correct()), i.partial() == null ? 0 : i.partial());
            return new CheckItem(QuestionService.view(q, actor.userId()), QuestionService.feedback(q, Response.fromJson(i.responseJson()), result, actor.userId()));
        }).toList());
    }

    /** The section's questions as student views, in authored order. */
    public List<QuestionView> checkQuestions(Actor actor, List<String> keys) {
        requireUser(actor);
        return orderByKeys(questions.byKeys(keys), keys).stream().map(q -> QuestionService.view(q, actor.userId())).toList();
    }

    private static List<QuestionRow> orderByKeys(List<QuestionRow> rows, List<String> keys) {
        Map<String, QuestionRow> byKey = new HashMap<>();
        rows.forEach(r -> byKey.put(r.key(), r));
        return keys.stream().map(byKey::get).filter(java.util.Objects::nonNull).toList();
    }
}
