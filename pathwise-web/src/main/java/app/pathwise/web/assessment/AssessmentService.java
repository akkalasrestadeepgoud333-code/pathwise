package app.pathwise.web.assessment;

import app.pathwise.core.assessment.AssessmentRules;
import app.pathwise.core.assessment.AssessmentRules.FeedbackPolicy;
import app.pathwise.core.assessment.AssessmentRules.Settings;
import app.pathwise.core.assessment.AssessmentRules.StartDecision;
import app.pathwise.core.assessment.ItemAnalysis;
import app.pathwise.core.learner.MasteryState;
import app.pathwise.core.questions.Grader;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.NotAuthenticatedException;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.core.security.PlatformPolicy;
import app.pathwise.data.Tx;
import app.pathwise.data.assessment.AssessmentDao;
import app.pathwise.data.assessment.AssessmentDao.AssessmentRow;
import app.pathwise.data.assessment.AssessmentDao.AttemptRow;
import app.pathwise.data.assessment.AssessmentDao.ItemRow;
import app.pathwise.data.assessment.AssessmentDao.ResponseRow;
import app.pathwise.data.catalog.CatalogDao.SkillRef;
import app.pathwise.data.learner.EvidenceDao;
import app.pathwise.data.learner.EvidenceDao.MasteryRow;
import app.pathwise.data.questions.QuestionDao.OptionRow;
import app.pathwise.data.questions.QuestionDao.QuestionRow;
import app.pathwise.web.catalog.CatalogService;
import app.pathwise.web.content.Markdown;
import app.pathwise.web.content.SafeHtml;
import app.pathwise.web.learner.LearnerService;
import app.pathwise.web.platform.Json;
import app.pathwise.web.platform.events.EventBus;
import app.pathwise.web.platform.view.TimeLabels;
import app.pathwise.web.questions.QuestionService;
import app.pathwise.web.questions.QuestionService.Feedback;
import app.pathwise.web.questions.QuestionService.QuestionView;
import app.pathwise.web.questions.QuestionService.Response;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Official assessments. Grading happens once, on the server, in a single transaction that locks the
 * attempt; each graded response keeps a snapshot of exactly what was graded and produces official
 * evidence (weight 1.0). Practice and games never write here.
 */
public final class AssessmentService {
    public static final String EVENT_GRADED = "assessment.graded";
    public static final double W_ASSESSMENT = 1.0;

    private final Tx tx;
    private final QuestionService questions;
    private final LearnerService learner;
    private final CatalogService catalog;
    private final EventBus events;
    private final Clock clock;
    private final AssessmentDao dao = new AssessmentDao();
    private final EvidenceDao evidence = new EvidenceDao();
    private final SecureRandom random = new SecureRandom();

    public AssessmentService(Tx tx, QuestionService questions, LearnerService learner, CatalogService catalog,
                             EventBus events, Clock clock) {
        this.tx = tx;
        this.questions = questions;
        this.learner = learner;
        this.catalog = catalog;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ views

    public record Card(String key, String title, String kind, String kindLabel, String subjectTitle, int questions,
                       Integer timeLimitMinutes, String timeLabel, int attemptsUsed, int maxAttempts, String best, String state,
                       String stateLabel, UUID openAttemptId, UUID lastGradedAttemptId) {}

    public record Rules(Card card, String instructions, String aiPolicyText, String feedbackText, String startDecision,
                        String startMessage) {}

    public record AttemptItem(int position, UUID itemId, QuestionView question, List<UUID> savedOptions, String savedText,
                              boolean answered) {}

    public record AttemptView(UUID attemptId, String key, String title, Long deadlineEpochMs, Long secondsLeft,
                              String timeLeftLabel, List<AttemptItem> items, int answered, int total, String aiPolicyText) {}

    public record ResultItem(int position, SafeHtml stem, Feedback feedback, double points, double maxPoints,
                             String pointsLabel) {}

    public record SkillResult(String skillTitle, String topicTitle, int correct, int total, String levelNow) {}

    public record Result(String key, String title, UUID attemptId, int attemptNo, double score, double maxScore,
                         String scoreLabel, int percent, boolean late, boolean autoSubmitted, String submittedLabel, boolean feedbackVisible,
                         List<ResultItem> items, List<SkillResult> skills, int attemptsLeft) {}

    public record AnalysisItem(int position, SafeHtml stem, int responses, double difficulty, Double discrimination,
                               String difficultyLabel, String discriminationLabel, String flag,
                               List<Map<String, Object>> options) {}

    public record Analysis(String key, String title, int gradedAttempts, int averagePercent, List<AnalysisItem> items) {}

    /** Thrown when an action is not allowed right now; the message is shown to the student. */
    public static final class RuleException extends RuntimeException {
        public RuleException(String message) {
            super(message);
        }
    }

    private static final Map<String, String> KIND_LABELS = Map.of("diagnostic", "Diagnostic", "quiz", "Quiz",
            "topic_test", "Topic test", "practice_test", "Practice test", "challenge", "Challenge");

    // ------------------------------------------------------------------ listing and rules

    public List<Card> list(Actor actor) {
        requireUser(actor);
        Map<UUID, String> subjects = new HashMap<>();
        catalog.subjects().forEach(s -> subjects.put(s.id(), s.title()));
        return tx.read(c -> {
            List<Card> out = new ArrayList<>();
            for (AssessmentRow a : dao.published(c)) {
                out.add(card(c, a, actor, subjects.getOrDefault(a.subjectId(), "")));
            }
            return out;
        });
    }

    public Optional<Rules> rules(Actor actor, String key) {
        requireUser(actor);
        return tx.read(c -> {
            Optional<AssessmentRow> a = dao.findByKey(c, key);
            if (a.isEmpty()) {
                return Optional.<Rules>empty();
            }
            String subject = catalog.subjects().stream().filter(s -> s.id().equals(a.get().subjectId()))
                    .map(s -> s.title()).findFirst().orElse("");
            Card card = card(c, a.get(), actor, subject);
            List<AttemptRow> attempts = dao.attempts(c, a.get().id(), actor.userId());
            StartDecision d = AssessmentRules.canStart(settings(a.get()), attempts.size(),
                    attempts.stream().anyMatch(t -> "in_progress".equals(t.status())), clock.instant());
            return Optional.of(new Rules(card, a.get().instructions(), aiText(a.get().aiPolicy()), feedbackText(a.get()),
                    d.name(), startMessage(d)));
        });
    }

    private Card card(java.sql.Connection c, AssessmentRow a, Actor actor, String subjectTitle) throws java.sql.SQLException {
        List<AttemptRow> attempts = dao.attempts(c, a.id(), actor.userId());
        int items = dao.items(c, a.id()).size();
        Optional<AttemptRow> open = attempts.stream().filter(t -> "in_progress".equals(t.status())).findFirst();
        Optional<AttemptRow> best = attempts.stream().filter(t -> "graded".equals(t.status()))
                .max((x, y) -> Double.compare(x.score(), y.score()));
        Optional<AttemptRow> last = attempts.stream().filter(t -> "graded".equals(t.status())).reduce((x, y) -> y);
        StartDecision d = AssessmentRules.canStart(settings(a), attempts.size(), open.isPresent(), clock.instant());
        String state;
        String label;
        if (open.isPresent()) {
            state = "in-progress";
            label = "In progress";
        } else if (d == StartDecision.OK) {
            state = best.isPresent() ? "retake" : "open";
            label = best.isPresent() ? "Retake available" : "Not started";
        } else if (best.isPresent()) {
            state = "done";
            label = "Completed";
        } else {
            state = "closed";
            label = d == StartDecision.NOT_YET_AVAILABLE ? "Opens later" : "Closed";
        }
        return new Card(a.key(), a.title(), a.kind(), KIND_LABELS.getOrDefault(a.kind(), a.kind()), subjectTitle, items,
                a.timeLimitSec() == null ? null : a.timeLimitSec() / 60,
                a.timeLimitSec() == null ? "No time limit" : (a.timeLimitSec() / 60) + " minutes", attempts.size(), a.maxAttempts(),
                best.map(b -> fmt(b.score()) + " / " + fmt(b.maxScore())).orElse(null), state, label,
                open.map(AttemptRow::id).orElse(null), last.map(AttemptRow::id).orElse(null));
    }

    // ------------------------------------------------------------------ attempts

    public UUID start(Actor actor, String key) {
        requireUser(actor);
        Instant now = clock.instant();
        return tx.call(c -> {
            AssessmentRow a = dao.findByKey(c, key).filter(x -> "published".equals(x.status()))
                    .orElseThrow(() -> new RuleException("That assessment isn't available."));
            List<AttemptRow> attempts = dao.attempts(c, a.id(), actor.userId());
            Optional<AttemptRow> open = attempts.stream().filter(t -> "in_progress".equals(t.status())).findFirst();
            if (open.isPresent()) {
                return open.get().id(); // resume rather than error
            }
            StartDecision d = AssessmentRules.canStart(settings(a), attempts.size(), false, now);
            if (d != StartDecision.OK) {
                throw new RuleException(startMessage(d));
            }
            return dao.createAttempt(c, a.id(), actor.userId(), attempts.size() + 1, random.nextLong(), now,
                    AssessmentRules.deadline(settings(a), now));
        });
    }

    /** The attempt to answer. If its time has run out, it is submitted first and empty is returned. */
    public Optional<AttemptView> attempt(Actor actor, UUID attemptId) {
        requireUser(actor);
        Optional<AttemptRow> found = tx.read(c -> dao.findAttempt(c, attemptId, actor.userId()));
        if (found.isEmpty() || !"in_progress".equals(found.get().status())) {
            return Optional.empty();
        }
        AttemptRow t = found.get();
        Instant now = clock.instant();
        if (!AssessmentRules.acceptsAnswers(t.deadlineAt(), now)) {
            grade(t.id(), t.userId(), true);
            return Optional.empty();
        }
        AssessmentRow a = tx.read(c -> dao.findById(c, t.assessmentId())).orElseThrow();
        List<ItemRow> items = ordered(tx.read(c -> dao.items(c, a.id())), a.shuffleItems(), t.seed());
        Map<UUID, QuestionRow> qs = questionsFor(items);
        Map<UUID, ResponseRow> saved = tx.read(c -> dao.responses(c, t.id())).stream()
                .collect(Collectors.toMap(ResponseRow::itemId, Function.identity()));
        List<AttemptItem> views = new ArrayList<>();
        int answered = 0;
        for (int i = 0; i < items.size(); i++) {
            ItemRow item = items.get(i);
            Response r = saved.containsKey(item.id()) ? Response.fromJson(saved.get(item.id()).responseJson()) : null;
            boolean has = r != null && (!r.options().isEmpty() || !r.text().isBlank());
            answered += has ? 1 : 0;
            views.add(new AttemptItem(i + 1, item.id(), QuestionService.view(qs.get(item.questionId()), actor.userId()),
                    r == null ? List.of() : List.copyOf(r.options()), r == null ? "" : r.text(), has));
        }
        Long deadlineMs = t.deadlineAt() == null ? null : t.deadlineAt().toEpochMilli();
        Long left = t.deadlineAt() == null ? null : Math.max(0, Duration.between(now, t.deadlineAt()).toSeconds());
        String leftLabel = left == null ? null : left >= 60 ? (left / 60) + " min left" : left + " s left";
        return Optional.of(new AttemptView(t.id(), a.key(), a.title(), deadlineMs, left, leftLabel, views, answered, items.size(),
                aiText(a.aiPolicy())));
    }

    /** Saves answers (only for this attempt's items, only while time remains). Returns false if refused. */
    public boolean save(Actor actor, UUID attemptId, Map<UUID, Response> answers) {
        requireUser(actor);
        Instant now = clock.instant();
        return tx.call(c -> {
            Optional<AttemptRow> t = dao.findAttempt(c, attemptId, actor.userId());
            if (t.isEmpty() || !"in_progress".equals(t.get().status()) || !AssessmentRules.acceptsAnswers(t.get().deadlineAt(), now)) {
                return false;
            }
            Set<UUID> itemIds = dao.items(c, t.get().assessmentId()).stream().map(ItemRow::id).collect(Collectors.toSet());
            for (Map.Entry<UUID, Response> e : answers.entrySet()) {
                if (itemIds.contains(e.getKey())) {
                    dao.saveResponse(c, attemptId, e.getKey(), e.getValue().toJson(), now);
                }
            }
            return true;
        });
    }

    /** Saves the final answers (if time remains) and grades the attempt. */
    public boolean submit(Actor actor, UUID attemptId, Map<UUID, Response> answers) {
        requireUser(actor);
        Optional<AttemptRow> t = tx.read(c -> dao.findAttempt(c, attemptId, actor.userId()));
        if (t.isEmpty() || !"in_progress".equals(t.get().status())) {
            return false;
        }
        save(actor, attemptId, answers); // silently ignored after the deadline: the saved answers are graded
        return grade(attemptId, actor.userId(), false);
    }

    /**
     * Grades exactly once (the attempt row is locked). Every item is graded from the saved response;
     * missing answers score zero. Writes snapshots, official evidence and updates the learner model.
     */
    boolean grade(UUID attemptId, UUID userId, boolean auto) {
        Instant now = clock.instant();
        return tx.call(c -> {
            Optional<AttemptRow> locked = dao.lockOpen(c, attemptId);
            if (locked.isEmpty() || !locked.get().userId().equals(userId)) {
                return false;
            }
            AttemptRow t = locked.get();
            AssessmentRow a = dao.findById(c, t.assessmentId()).orElseThrow();
            List<ItemRow> items = dao.items(c, a.id());
            Map<UUID, QuestionRow> qs = questionsFor(items);
            Map<UUID, ResponseRow> saved = dao.responses(c, t.id()).stream()
                    .collect(Collectors.toMap(ResponseRow::itemId, Function.identity()));
            double score = 0;
            double max = 0;
            Set<UUID> skills = new LinkedHashSet<>();
            for (ItemRow item : items) {
                QuestionRow q = qs.get(item.questionId());
                Response r = saved.containsKey(item.id()) ? Response.fromJson(saved.get(item.id()).responseJson())
                        : new Response(Set.of(), "");
                Grader.Result result = QuestionService.grade(q, r);
                double points = Math.round(result.partial() * item.points() * 100) / 100.0;
                score += points;
                max += item.points();
                UUID responseId = dao.gradeResponse(c, t.id(), item.id(), r.toJson(), result.correct(), result.partial(),
                        points, snapshot(q), now);
                for (UUID skill : q.skillIds()) {
                    evidence.insertForResponse(c, userId, skill, responseId, result.partial(), W_ASSESSMENT, q.difficulty(), now);
                    skills.add(skill);
                }
            }
            boolean late = AssessmentRules.isLate(settings(a), now);
            dao.markGraded(c, t.id(), score, max, late, auto, now);
            learner.recompute(c, userId, skills, now);
            events.publish(c, EVENT_GRADED, Map.of("userId", userId.toString(), "assessmentId", a.id().toString(),
                    "attemptId", t.id().toString(), "score", score, "max", max));
            return true;
        });
    }

    /** Auto-submits attempts whose time ran out (runs every minute from the background worker). */
    public int sweepOverdue() {
        Instant cutoff = clock.instant().minus(AssessmentRules.GRACE);
        List<AttemptRow> overdue = tx.read(c -> dao.overdue(c, cutoff));
        int n = 0;
        for (AttemptRow t : overdue) {
            if (grade(t.id(), t.userId(), true)) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ results

    public Optional<Result> result(Actor actor, UUID attemptId) {
        requireUser(actor);
        Optional<AttemptRow> found = tx.read(c -> dao.findAttempt(c, attemptId, actor.userId()));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AttemptRow t = found.get();
        if ("in_progress".equals(t.status())) {
            if (AssessmentRules.acceptsAnswers(t.deadlineAt(), clock.instant())) {
                return Optional.empty();
            }
            grade(t.id(), t.userId(), true);
            t = tx.read(c -> dao.findAttempt(c, attemptId, actor.userId())).orElseThrow();
        }
        AttemptRow graded = t;
        AssessmentRow a = tx.read(c -> dao.findById(c, graded.assessmentId())).orElseThrow();
        boolean visible = AssessmentRules.feedbackVisible(settings(a), clock.instant());
        List<ItemRow> items = ordered(tx.read(c -> dao.items(c, a.id())), a.shuffleItems(), graded.seed());
        Map<UUID, ResponseRow> responses = tx.read(c -> dao.responses(c, graded.id())).stream()
                .collect(Collectors.toMap(ResponseRow::itemId, Function.identity()));
        List<ResultItem> out = new ArrayList<>();
        Map<UUID, int[]> bySkill = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            ItemRow item = items.get(i);
            ResponseRow r = responses.get(item.id());
            QuestionRow snap = fromSnapshot(r.snapshotJson());
            Grader.Result g = new Grader.Result(Boolean.TRUE.equals(r.correct()), r.credit() == null ? 0 : r.credit());
            for (UUID s : snap.skillIds()) {
                int[] cnt = bySkill.computeIfAbsent(s, k -> new int[2]);
                cnt[1]++;
                if (g.correct()) {
                    cnt[0]++;
                }
            }
            out.add(new ResultItem(i + 1, Markdown.render(snap.stem()),
                    visible ? QuestionService.feedback(snap, Response.fromJson(r.responseJson()), g, actor.userId()) : null,
                    r.pointsAwarded() == null ? 0 : r.pointsAwarded(), item.points(),
                    fmt(r.pointsAwarded()) + " / " + fmt(item.points()) + (item.points() == 1 ? " point" : " points")));
        }
        Map<UUID, MasteryRow> mastery = learner.mastery(actor);
        List<SkillResult> skills = new ArrayList<>();
        for (SkillRef s : catalog.skillRefs(bySkill.keySet())) {
            int[] cnt = bySkill.get(s.id());
            MasteryRow m = mastery.get(s.id());
            skills.add(new SkillResult(s.title(), s.topicTitle(), cnt[0], cnt[1],
                    m == null ? MasteryState.NOT_STARTED.label() : MasteryState.fromDb(m.state()).label()));
        }
        int used = tx.read(c -> dao.attempts(c, a.id(), actor.userId())).size();
        double max = graded.maxScore() == null ? 0 : graded.maxScore();
        double score = graded.score() == null ? 0 : graded.score();
        return Optional.of(new Result(a.key(), a.title(), graded.id(), graded.attemptNo(), score, max,
                fmt(score) + " of " + fmt(max), max == 0 ? 0 : (int) Math.round(100 * score / max), graded.late(), graded.autoSubmitted(),
                TimeLabels.dateTime(graded.submittedAt(), TimeLabels.DEFAULT_ZONE), visible,
                visible ? out : out.stream().map(x -> new ResultItem(x.position(), x.stem(), null, x.points(), x.maxPoints(),
                        x.pointsLabel())).toList(),
                skills, Math.max(0, a.maxAttempts() - used)));
    }

    // ------------------------------------------------------------------ analysis (staff only)

    public record AdminRow(String key, String title, String kindLabel, int questions, String timeLabel, int maxAttempts,
                           int gradedAttempts, Integer averagePercent) {}

    public List<AdminRow> adminList(Actor actor) {
        PlatformPolicy.require(actor, PlatformAction.USE_ADMIN_AREA);
        return tx.read(c -> {
            List<AdminRow> out = new ArrayList<>();
            for (AssessmentRow a : dao.published(c)) {
                List<AttemptRow> graded = dao.gradedAttempts(c, a.id());
                Integer avg = graded.isEmpty() ? null : (int) Math.round(100 * graded.stream()
                        .mapToDouble(t -> t.maxScore() == null || t.maxScore() == 0 ? 0 : t.score() / t.maxScore())
                        .average().orElse(0));
                out.add(new AdminRow(a.key(), a.title(), KIND_LABELS.getOrDefault(a.kind(), a.kind()), dao.items(c, a.id()).size(),
                        a.timeLimitSec() == null ? "No time limit" : (a.timeLimitSec() / 60) + " minutes", a.maxAttempts(),
                        graded.size(), avg));
            }
            return out;
        });
    }

    public Optional<Analysis> analysis(Actor actor, String key) {
        PlatformPolicy.require(actor, PlatformAction.USE_ADMIN_AREA);
        return tx.read(c -> {
            Optional<AssessmentRow> a = dao.findByKey(c, key);
            if (a.isEmpty()) {
                return Optional.<Analysis>empty();
            }
            List<ItemRow> items = dao.items(c, a.get().id());
            List<AttemptRow> graded = dao.gradedAttempts(c, a.get().id());
            List<ItemAnalysis.Attempt> data = new ArrayList<>();
            double pctSum = 0;
            for (AttemptRow t : graded) {
                Map<String, Double> credit = new HashMap<>();
                Map<String, List<String>> chosen = new HashMap<>();
                for (ResponseRow r : dao.responses(c, t.id())) {
                    credit.put(r.itemId().toString(), r.credit() == null ? 0 : r.credit());
                    chosen.put(r.itemId().toString(), Response.fromJson(r.responseJson()).options().stream().map(UUID::toString).toList());
                }
                data.add(new ItemAnalysis.Attempt(t.score() == null ? 0 : t.score(), credit, chosen));
                pctSum += t.maxScore() == null || t.maxScore() == 0 ? 0 : t.score() / t.maxScore();
            }
            List<ItemAnalysis.ItemStats> stats = ItemAnalysis.analyse(items.stream().map(i -> i.id().toString()).toList(), data);
            Map<UUID, QuestionRow> qs = questionsFor(items);
            List<AnalysisItem> out = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                QuestionRow q = qs.get(items.get(i).questionId());
                ItemAnalysis.ItemStats st = stats.get(i);
                List<Map<String, Object>> options = new ArrayList<>();
                for (OptionRow o : q.options()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("label", Markdown.inline(o.label()));
                    m.put("correct", o.correct());
                    int count = st.optionCounts().getOrDefault(o.id().toString(), 0);
                    m.put("count", count);
                    m.put("percent", st.responses() == 0 ? 0 : Math.round(100.0 * count / st.responses()));
                    options.add(m);
                }
                out.add(new AnalysisItem(i + 1, Markdown.render(q.stem()), st.responses(), st.difficulty(), st.discrimination(),
                        String.format(Locale.ROOT, "%.2f", st.difficulty()),
                        st.discrimination() == null ? "n/a" : String.format(Locale.ROOT, "%+.2f", st.discrimination()),
                        st.flag(), options));
            }
            int avg = graded.isEmpty() ? 0 : (int) Math.round(100 * pctSum / graded.size());
            return Optional.of(new Analysis(a.get().key(), a.get().title(), graded.size(), avg, out));
        });
    }

    // ------------------------------------------------------------------ helpers

    static Settings settings(AssessmentRow a) {
        return new Settings(a.timeLimitSec(), a.maxAttempts(), a.availableFrom(), a.dueAt(),
                FeedbackPolicy.valueOf(a.feedbackPolicy().toUpperCase(Locale.ROOT)));
    }

    private Map<UUID, QuestionRow> questionsFor(List<ItemRow> items) {
        return questions.byIds(items.stream().map(ItemRow::questionId).toList()).stream()
                .collect(Collectors.toMap(QuestionRow::id, Function.identity()));
    }

    /** Item order for an attempt: authored, or shuffled reproducibly from the attempt's seed. */
    static List<ItemRow> ordered(List<ItemRow> items, boolean shuffle, long seed) {
        List<ItemRow> copy = new ArrayList<>(items);
        if (shuffle) {
            Collections.shuffle(copy, new Random(seed));
        }
        return copy;
    }

    /** Exactly what was graded, so later edits to the question never change this result. */
    static String snapshot(QuestionRow q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", q.id().toString());
        m.put("key", q.key());
        m.put("type", q.type());
        m.put("stem", q.stem());
        m.put("difficulty", q.difficulty());
        m.put("explanation", q.explanation());
        m.put("spec", q.specJson());
        m.put("skills", q.skillIds().stream().map(UUID::toString).toList());
        m.put("options", q.options().stream().map(o -> {
            Map<String, Object> om = new LinkedHashMap<>();
            om.put("id", o.id().toString());
            om.put("position", o.position());
            om.put("label", o.label());
            om.put("correct", o.correct());
            om.put("rationale", o.rationale());
            return om;
        }).toList());
        return Json.write(m);
    }

    static QuestionRow fromSnapshot(String json) {
        try {
            JsonNode n = Json.mapper().readTree(json);
            List<OptionRow> options = new ArrayList<>();
            for (JsonNode o : n.path("options")) {
                options.add(new OptionRow(UUID.fromString(o.path("id").asText()), o.path("position").asInt(),
                        o.path("label").asText(), o.path("correct").asBoolean(),
                        o.path("rationale").isNull() ? null : o.path("rationale").asText(null)));
            }
            List<UUID> skills = Json.mapper().convertValue(n.path("skills"), new TypeReference<List<String>>() {})
                    .stream().map(UUID::fromString).toList();
            return new QuestionRow(UUID.fromString(n.path("id").asText()), n.path("key").asText(), n.path("type").asText(),
                    n.path("stem").asText(), n.path("spec").asText("{}"), n.path("difficulty").asInt(), "[]",
                    n.path("explanation").isNull() ? null : n.path("explanation").asText(null), options, skills);
        } catch (IOException e) {
            throw new IllegalStateException("Corrupt question snapshot", e);
        }
    }

    static String aiText(String policy) {
        return switch (policy) {
            case "allowed" -> "The AI tutor is available during this assessment.";
            case "hints_only" -> "Only step-by-step hints are available from the tutor during this assessment.";
            default -> "The AI tutor is turned off during this assessment. (Pathwise can't control other tools, so please work on your own.)";
        };
    }

    static String feedbackText(AssessmentRow a) {
        return switch (a.feedbackPolicy()) {
            case "after_due" -> "You'll see which answers were right after the due date.";
            case "manual_release" -> "Your teacher will release the answers later. You'll see your score straight away.";
            default -> "You'll see your score and which answers were right as soon as you submit.";
        };
    }

    static String startMessage(StartDecision d) {
        return switch (d) {
            case OK -> "You can start now.";
            case NOT_YET_AVAILABLE -> "This assessment hasn't opened yet.";
            case CLOSED -> "This assessment is closed.";
            case NO_ATTEMPTS_LEFT -> "You've used all your attempts.";
            case ALREADY_IN_PROGRESS -> "You have an attempt in progress.";
        };
    }

    private static String fmt(Double v) {
        if (v == null) {
            return "0";
        }
        return v == Math.rint(v) ? Long.toString(v.longValue()) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static void requireUser(Actor actor) {
        if (actor == null || !actor.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }
    }
}
