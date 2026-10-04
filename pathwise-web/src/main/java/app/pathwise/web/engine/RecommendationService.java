package app.pathwise.web.engine;

import app.pathwise.core.engine.Recommender;
import app.pathwise.core.engine.Snapshot;
import app.pathwise.core.learner.MasteryState;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.NotAuthenticatedException;
import app.pathwise.data.Tx;
import app.pathwise.data.catalog.CatalogDao.SubjectRow;
import app.pathwise.data.catalog.CatalogDao.TopicRow;
import app.pathwise.data.content.ContentDao.ModuleRow;
import app.pathwise.data.content.ProgressDao.ModuleProgress;
import app.pathwise.data.learner.AttemptDao;
import app.pathwise.data.learner.EvidenceDao;
import app.pathwise.data.learner.EvidenceDao.MasteryRow;
import app.pathwise.data.learner.RecommendationDao;
import app.pathwise.data.learner.RecommendationDao.GoalRow;
import app.pathwise.data.learner.RecommendationDao.RecRow;
import app.pathwise.data.questions.QuestionDao;
import app.pathwise.web.catalog.CatalogService;
import app.pathwise.web.catalog.CatalogService.SubjectGraph;
import app.pathwise.web.content.ContentService;
import app.pathwise.web.learner.LearnerService;
import app.pathwise.web.platform.Json;
import app.pathwise.web.practice.PracticeService;
import app.pathwise.web.questions.QuestionService;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the engine snapshot from the catalogue, learner model, progress, goals and the student's own
 * feedback; runs the pure {@link Recommender}; stores the results (with reasons and factor values) so
 * every suggestion can be explained later; and applies the student's choices.
 */
public final class RecommendationService {
    private static final Duration MAX_AGE = Duration.ofHours(6);

    private final Tx tx;
    private final CatalogService catalog;
    private final ContentService content;
    private final LearnerService learner;
    private final QuestionService questions;
    private final PracticeService practice;
    private final Clock clock;
    private final RecommendationDao dao = new RecommendationDao();
    private final AttemptDao attempts = new AttemptDao();

    public RecommendationService(Tx tx, CatalogService catalog, ContentService content, LearnerService learner,
                                 QuestionService questions, PracticeService practice, Clock clock) {
        this.tx = tx;
        this.catalog = catalog;
        this.content = content;
        this.learner = learner;
        this.questions = questions;
        this.practice = practice;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ views

    public record FactorView(String name, String label, String explanation, double contribution) {}

    public record RecView(UUID id, String slot, String slotLabel, int rank, String targetKind, String title,
                          String topicTitle, String subjectKey, String topicKey, String reasonText,
                          List<FactorView> factors, String basedOn, String action, double score) {}

    public record GoalView(UUID id, String topicTitle, String subjectKey, String topicKey, LocalDate targetDate) {}

    public enum Action { START, NOT_NOW, NOT_RELEVANT, ALREADY_KNOW }

    private static final Map<String, String> SLOT_LABELS = Map.of(
            "continue", "Continue", "strengthen", "Strengthen", "challenge", "Challenge",
            "explore", "Explore", "revise", "Revise", "teacher", "From your teacher");

    private static final Map<String, String> FACTOR_LABELS = Map.of(
            "need", "How much it would help", "leverage", "What it unlocks", "goal_fit", "Your goal",
            "curriculum_fit", "Where it sits in the subject", "recency", "Timing", "variety", "Variety",
            "base", "Starting score", "root_cause", "Root cause");

    /** Active recommendations, regenerated first if anything changed since the last run. */
    public List<RecView> current(Actor actor) {
        requireUser(actor);
        if (stale(actor)) {
            regenerate(actor);
        }
        List<RecRow> rows = tx.read(c -> dao.active(c, actor.userId()));
        Map<UUID, TopicRef> topics = topicRefs();
        return rows.stream().map(r -> view(actor, r, topics)).toList();
    }

    /** The single best suggestion right now, if any. */
    public Optional<RecView> top(Actor actor) {
        return current(actor).stream().findFirst();
    }

    public List<RecView> history(Actor actor) {
        requireUser(actor);
        Map<UUID, TopicRef> topics = topicRefs();
        return tx.read(c -> dao.history(c, actor.userId(), 10)).stream().map(r -> view(actor, r, topics)).toList();
    }

    private boolean stale(Actor actor) {
        Optional<Instant> generated = tx.read(c -> dao.latestGeneratedAt(c, actor.userId()));
        if (generated.isEmpty()) {
            return true;
        }
        Instant g = generated.get();
        if (g.plus(MAX_AGE).isBefore(clock.instant())) {
            return true;
        }
        Instant evidence = learner.latestEvidenceAt(actor);
        Instant activity = content.latestActivityAt(actor);
        return (evidence != null && evidence.isAfter(g)) || (activity != null && activity.isAfter(g));
    }

    // ------------------------------------------------------------------ engine run

    public List<Recommender.Recommendation> regenerate(Actor actor) {
        requireUser(actor);
        Instant now = clock.instant();
        Snapshot snapshot = snapshot(actor, now);
        List<Recommender.Recommendation> recs = Recommender.recommend(snapshot);
        Map<String, UUID> skillTopic = new HashMap<>();
        snapshot.skills().forEach(s -> skillTopic.put(s.id(), UUID.fromString(s.topicId())));
        tx.run(c -> {
            dao.expireActive(c, actor.userId());
            for (Recommender.Recommendation r : recs) {
                boolean module = r.targetKind() == Recommender.TargetKind.MODULE;
                List<Map<String, Object>> factors = r.factors().stream().map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", f.name());
                    m.put("value", round(f.value()));
                    m.put("weight", f.weight());
                    m.put("contribution", round(f.contribution()));
                    m.put("explanation", f.explanation());
                    return m;
                }).toList();
                dao.insert(c, actor.userId(), r.slot().name().toLowerCase(Locale.ROOT), r.rank(),
                        r.targetKind().name().toLowerCase(Locale.ROOT),
                        module ? UUID.fromString(r.targetId()) : null, module ? null : UUID.fromString(r.targetId()),
                        UUID.fromString(r.topicId()), r.title(), r.reasonCode(), r.reasonText(),
                        Json.write(Map.of("factors", factors)), r.score(), Recommender.VERSION, now);
            }
        });
        return recs;
    }

    Snapshot snapshot(Actor actor, Instant now) {
        Map<UUID, MasteryRow> mastery = learner.mastery(actor);
        Map<UUID, EvidenceDao.RecentStats> recent = learner.recentStats(actor, now.minus(Duration.ofDays(7)));
        Map<UUID, QuestionDao.SkillSupply> supply = questions.supply();
        Map<UUID, ModuleProgress> progress = content.allProgress(actor);
        Map<UUID, Integer> sectionCounts = content.sectionCounts();
        Map<UUID, Integer> doneCounts = content.completedSectionCounts(actor);

        List<Snapshot.Topic> topics = new ArrayList<>();
        List<Snapshot.Skill> skills = new ArrayList<>();
        List<Snapshot.Prerequisite> prereqs = new ArrayList<>();
        List<Snapshot.ModuleInfo> modules = new ArrayList<>();
        Map<UUID, String> topicTitles = new HashMap<>();
        for (SubjectRow subject : catalog.subjects()) {
            SubjectGraph g = catalog.graph(subject.key()).orElse(null);
            if (g == null) {
                continue;
            }
            for (TopicRow t : g.topics()) {
                topics.add(new Snapshot.Topic(t.id().toString(), t.title(), subject.id().toString(), t.position(),
                        t.optional(), t.advanced()));
                topicTitles.put(t.id(), t.title());
            }
            g.prerequisites().forEach(p -> prereqs.add(new Snapshot.Prerequisite(p.topicId().toString(),
                    p.prerequisiteId().toString(), "required".equals(p.strength()))));
            for (var k : g.skills()) {
                MasteryRow m = mastery.get(k.id());
                EvidenceDao.RecentStats rs = recent.getOrDefault(k.id(), new EvidenceDao.RecentStats(0, 0));
                QuestionDao.SkillSupply sup = supply.getOrDefault(k.id(), new QuestionDao.SkillSupply(k.id(), 0, 0));
                skills.add(new Snapshot.Skill(k.id().toString(), k.title(), k.topicId().toString(), k.core(),
                        m == null ? MasteryState.NOT_STARTED : MasteryState.fromDb(m.state()),
                        m == null ? 0.4 : m.estimate(), m == null ? 0 : m.confidence(), m == null ? 0 : m.evidenceCount(),
                        m == null ? null : m.reviewDueAt(), rs.attempts(), rs.errors(), sup.total(), sup.challenge()));
            }
            for (ModuleRow m : content.modulesOfSubject(subject.id())) {
                ModuleProgress p = progress.get(m.id());
                int total = sectionCounts.getOrDefault(m.id(), 0);
                int done = doneCounts.getOrDefault(m.id(), 0);
                modules.add(new Snapshot.ModuleInfo(m.id().toString(), m.title(), m.topicId().toString(), m.position(),
                        p != null, p != null && p.completed(), total == 0 ? 0 : Math.round(100f * done / total),
                        p == null ? null : p.lastSeenAt()));
            }
        }
        List<GoalRow> goals = tx.read(c -> dao.activeGoals(c, actor.userId()));
        List<Snapshot.Goal> goalList = goals.stream().map(g -> new Snapshot.Goal(g.topicId().toString(),
                topicTitles.getOrDefault(g.topicId(), "a topic"), g.targetDate())).toList();
        List<Snapshot.Suppression> suppressions = tx.read(c -> dao.suppressions(c, actor.userId(), now)).stream()
                .map(r -> {
                    Instant until = "snoozed".equals(r.status()) ? r.snoozedUntil() : r.actedAt().plus(Duration.ofDays(7));
                    Set<String> ids = "not_relevant".equals(r.feedback())
                            ? Set.of(r.targetId().toString(), r.topicId().toString())
                            : Set.of(r.targetId().toString());
                    return new Snapshot.Suppression(ids, until);
                }).toList();
        List<String> kinds = tx.read(c -> attempts.recentKinds(c, actor.userId(), 3)).stream()
                .map(k -> "module_check".equals(k) ? "module" : k).toList();
        return new Snapshot(now, topics, skills, prereqs, modules, goalList, suppressions, kinds);
    }

    // ------------------------------------------------------------------ actions

    /** Applies a choice. Returns where to send the student next (context-relative path). */
    public Optional<String> act(Actor actor, UUID recId, Action action) {
        requireUser(actor);
        Instant now = clock.instant();
        Optional<RecRow> found = tx.read(c -> dao.find(c, recId, actor.userId()));
        if (found.isEmpty() || !"active".equals(found.get().status())) {
            return Optional.empty();
        }
        RecRow r = found.get();
        String next;
        switch (action) {
            case START -> {
                tx.run(c -> dao.act(c, recId, actor.userId(), "accepted", null, null, now));
                next = destination(actor, r);
            }
            case NOT_NOW -> {
                tx.run(c -> dao.act(c, recId, actor.userId(), "snoozed", "later", now.plus(Duration.ofDays(1)), now));
                next = null;
            }
            case NOT_RELEVANT -> {
                tx.run(c -> dao.act(c, recId, actor.userId(), "dismissed", "not_relevant", null, now));
                next = null;
            }
            case ALREADY_KNOW -> {
                // Don't just take the student's word for it, and don't silently overrule them either:
                // offer a short check whose answers become evidence either way.
                tx.run(c -> dao.act(c, recId, actor.userId(), "accepted", "already_know", null, now));
                List<UUID> skills = r.moduleId() != null ? content.skillIdsOfModule(r.moduleId()) : List.of(r.skillId());
                UUID attempt = practice.startForSkills(actor, PracticeService.Mode.REVIEW, skills,
                        skills.size() == 1 ? skills.get(0) : null, recId);
                next = "/app/practice/" + attempt;
            }
            default -> throw new IllegalStateException();
        }
        if (next == null) {
            regenerate(actor);
        }
        return Optional.of(next == null ? "/app/next" : next);
    }

    private String destination(Actor actor, RecRow r) {
        return switch (r.targetKind()) {
            case "module" -> "/app/modules/" + content.moduleKey(r.moduleId()).orElseThrow();
            case "challenge" -> "/app/practice/" + practice.start(actor, PracticeService.Mode.CHALLENGE, r.skillId(), r.id());
            case "review" -> "/app/practice/" + practice.start(actor, PracticeService.Mode.REVIEW, r.skillId(), r.id());
            default -> "/app/practice/" + practice.start(actor, PracticeService.Mode.PRACTICE, r.skillId(), r.id());
        };
    }

    // ------------------------------------------------------------------ goals

    public void setGoal(Actor actor, String subjectKey, String topicKey, LocalDate targetDate) {
        requireUser(actor);
        TopicRow t = catalog.graph(subjectKey).flatMap(g -> g.topic(topicKey)).orElseThrow(() -> new IllegalArgumentException("Unknown topic"));
        tx.run(c -> dao.addGoal(c, actor.userId(), t.id(), targetDate));
        regenerate(actor);
    }

    public boolean endGoal(Actor actor, UUID goalId) {
        requireUser(actor);
        boolean ok = tx.call(c -> dao.endGoal(c, goalId, actor.userId(), "abandoned"));
        if (ok) {
            regenerate(actor);
        }
        return ok;
    }

    public List<GoalView> goals(Actor actor) {
        requireUser(actor);
        Map<UUID, TopicRef> topics = topicRefs();
        return tx.read(c -> dao.activeGoals(c, actor.userId())).stream().map(g -> {
            TopicRef t = topics.get(g.topicId());
            return new GoalView(g.id(), t == null ? "Topic" : t.title(), t == null ? null : t.subjectKey(),
                    t == null ? null : t.key(), g.targetDate());
        }).toList();
    }

    // ------------------------------------------------------------------ helpers

    private record TopicRef(String key, String title, String subjectKey) {}

    private Map<UUID, TopicRef> topicRefs() {
        Map<UUID, TopicRef> out = new HashMap<>();
        for (SubjectRow s : catalog.subjects()) {
            catalog.graph(s.key()).ifPresent(g -> g.topics().forEach(t -> out.put(t.id(), new TopicRef(t.key(), t.title(), s.key()))));
        }
        return out;
    }

    private RecView view(Actor actor, RecRow r, Map<UUID, TopicRef> topics) {
        TopicRef t = topics.get(r.topicId());
        List<FactorView> factors = new ArrayList<>();
        try {
            JsonNode root = Json.mapper().readTree(r.factorsJson());
            for (JsonNode f : root.path("factors")) {
                String name = f.path("name").asText();
                if ("base".equals(name)) {
                    continue;
                }
                factors.add(new FactorView(name, FACTOR_LABELS.getOrDefault(name, name), f.path("explanation").asText(),
                        f.path("contribution").asDouble()));
            }
        } catch (IOException e) {
            // Factors are informational; a malformed snapshot just shows none.
        }
        factors.sort((a, b) -> Double.compare(b.contribution(), a.contribution()));
        String basedOn;
        if (r.skillId() != null) {
            Map<String, Integer> counts = learner.evidenceBySource(actor, r.skillId());
            basedOn = describe(counts);
        } else {
            basedOn = "Based on where you are in the Learning Map and what you've studied.";
        }
        String action = switch (r.targetKind()) {
            case "module" -> "Start";
            case "challenge" -> "Take the challenge";
            case "review" -> "Review now";
            default -> "Practise";
        };
        if ("continue".equals(r.slot())) {
            action = "Continue";
        }
        return new RecView(r.id(), r.slot(), SLOT_LABELS.getOrDefault(r.slot(), r.slot()), r.rank(), r.targetKind(),
                r.title(), t == null ? null : t.title(), t == null ? null : t.subjectKey(), t == null ? null : t.key(),
                r.reasonText(), factors, basedOn, action, r.score());
    }

    static String describe(Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return "No answers recorded for this skill yet.";
        }
        Map<String, String> names = Map.of("practice", "practice answer", "module_check", "module check answer",
                "challenge", "challenge answer", "review", "review answer");
        List<String> parts = new ArrayList<>();
        counts.forEach((k, v) -> parts.add(v + " " + names.getOrDefault(k, k.replace('_', ' ')) + (v == 1 ? "" : "s")));
        parts.sort(String::compareTo);
        return "Based on " + String.join(", ", parts) + ".";
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private static void requireUser(Actor actor) {
        if (actor == null || !actor.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }
    }
}
