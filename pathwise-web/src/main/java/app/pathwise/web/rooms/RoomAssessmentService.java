package app.pathwise.web.rooms;

import app.pathwise.core.rooms.RoomPolicy.Action;
import app.pathwise.core.security.Actor;
import app.pathwise.data.Tx;
import app.pathwise.data.assessment.AssessmentDao;
import app.pathwise.data.assessment.AssessmentDao.AssessmentRow;
import app.pathwise.data.catalog.CatalogDao;
import app.pathwise.data.questions.QuestionDao;
import app.pathwise.data.questions.QuestionDao.QuestionRow;
import app.pathwise.data.rooms.RoomDao;
import app.pathwise.web.assessment.AssessmentService;
import app.pathwise.web.catalog.CatalogService;
import app.pathwise.web.platform.view.TimeLabels;
import app.pathwise.web.rooms.RoomService.Ctx;
import app.pathwise.web.rooms.RoomService.RoomRuleException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Teacher-made assessments for a room: built from the question bank, drafted, published, and (for
 * manual-release feedback) released. Students take them through the normal assessment pages.
 */
public final class RoomAssessmentService {
    private final Tx tx;
    private final RoomService rooms;
    private final CatalogService catalog;
    private final AssessmentService assessmentService;
    private final Clock clock;
    private final AssessmentDao dao = new AssessmentDao();
    private final QuestionDao questions = new QuestionDao();
    private final CatalogDao catalogDao = new CatalogDao();
    private final RoomDao roomDao = new RoomDao();

    public RoomAssessmentService(Tx tx, RoomService rooms, CatalogService catalog, AssessmentService assessmentService, Clock clock) {
        this.tx = tx;
        this.rooms = rooms;
        this.catalog = catalog;
        this.assessmentService = assessmentService;
        this.clock = clock;
    }

    public record Row(UUID id, String key, String title, String kindLabel, String status, String statusLabel, String dueLabel,
                      String feedbackLabel, int questions, int submitted, int students, boolean canPublish, boolean canRelease,
                      boolean released) {}

    public record Topic(String key, String title, String subjectTitle) {}

    public record Page(RoomService.Header header, List<Row> rows, List<Topic> topics) {}

    public Page page(Actor actor, UUID roomId) {
        Instant now = clock.instant();
        return tx.read(c -> {
            Ctx ctx = rooms.require(c, actor, roomId, Action.USE_TEACHER_VIEW);
            int students = roomDao.activeStudents(c, roomId).size();
            Map<UUID, Set<UUID>> submittedBy = new java.util.HashMap<>();
            dao.roomScores(c, roomId).forEach(s -> submittedBy.computeIfAbsent(s.assessmentId(), k -> new HashSet<>()).add(s.userId()));
            List<Row> rows = new ArrayList<>();
            for (AssessmentRow a : dao.ofRoom(c, roomId)) {
                boolean released = a.resultsReleasedAt() != null;
                rows.add(new Row(a.id(), a.key(), a.title(), kindLabel(a.kind()), a.status(),
                        "draft".equals(a.status()) ? "Draft" : "Published",
                        a.dueAt() == null ? "No due date" : "Due " + TimeLabels.dateTime(a.dueAt(), TimeLabels.DEFAULT_ZONE),
                        feedbackLabel(a.feedbackPolicy(), released), dao.items(c, a.id()).size(),
                        submittedBy.getOrDefault(a.id(), Set.of()).size(), students,
                        "draft".equals(a.status()) && ctx.can(Action.PUBLISH),
                        "published".equals(a.status()) && "manual_release".equals(a.feedbackPolicy()) && !released && ctx.can(Action.GRADE),
                        released));
            }
            return new Page(rooms.header(c, ctx), rows, topics(ctx));
        });
    }

    private List<Topic> topics(Ctx ctx) {
        List<Topic> out = new ArrayList<>();
        for (var s : catalog.subjects()) {
            if (ctx.room().subjectId() != null && !ctx.room().subjectId().equals(s.id())) {
                continue;
            }
            catalog.graph(s.key()).ifPresent(g -> g.topics().forEach(t -> out.add(new Topic(t.key(), t.title(), s.title()))));
        }
        return out;
    }

    public record Pick(UUID id, String stem, String type, int difficulty, boolean testOnly) {}

    public record Builder(RoomService.Header header, Topic topic, List<Pick> questions) {}

    /** The bank questions for one topic, for the teacher to choose from. */
    public Optional<Builder> builder(Actor actor, UUID roomId, String topicKey) {
        return tx.read(c -> {
            Ctx ctx = rooms.require(c, actor, roomId, Action.AUTHOR);
            Optional<Topic> topic = topics(ctx).stream().filter(t -> t.key().equals(topicKey)).findFirst();
            if (topic.isEmpty()) {
                return Optional.<Builder>empty();
            }
            List<UUID> skills = topicSkills(topicKey);
            List<QuestionRow> bank = skills.isEmpty() ? List.of() : questions.bankForSkills(c, skills);
            Map<UUID, Boolean> practice = bank.isEmpty() ? Map.of() : questions.practiceEnabled(c, bank.stream().map(QuestionRow::id).toList());
            List<Pick> picks = bank.stream().map(q -> new Pick(q.id(), plain(q.stem()), typeLabel(q.type()), q.difficulty(),
                    !practice.getOrDefault(q.id(), true))).toList();
            return Optional.of(new Builder(rooms.header(c, ctx), topic.get(), picks));
        });
    }

    private List<UUID> topicSkills(String topicKey) {
        for (var s : catalog.subjects()) {
            var g = catalog.graph(s.key());
            if (g.isPresent()) {
                var t = g.get().topic(topicKey);
                if (t.isPresent()) {
                    return g.get().skillsOf(t.get().id()).stream().map(CatalogDao.SkillRow::id).toList();
                }
            }
        }
        return List.of();
    }

    public record Draft(String topicKey, String title, String kind, String instructions, Integer timeLimitMinutes, int maxAttempts,
                        boolean shuffle, String feedback, LocalDate dueOn, List<UUID> questionIds) {}

    /** Creates a draft. Questions must come from the chosen topic's bank. */
    public UUID create(Actor actor, UUID roomId, Draft d) {
        String title = d.title() == null ? "" : d.title().strip();
        if (title.length() < 2 || title.length() > 160) {
            throw new RoomRuleException("Give the assessment a title of 2 to 160 characters.");
        }
        String kind = switch (d.kind() == null ? "" : d.kind()) {
            case "quiz", "topic_test", "practice_test" -> d.kind();
            default -> "quiz";
        };
        String feedback = switch (d.feedback() == null ? "" : d.feedback()) {
            case "immediate", "after_due", "manual_release" -> d.feedback();
            default -> "immediate";
        };
        if ("after_due".equals(feedback) && d.dueOn() == null) {
            throw new RoomRuleException("\"Show answers after the due date\" needs a due date.");
        }
        Integer limit = d.timeLimitMinutes();
        if (limit != null && (limit < 1 || limit > 240)) {
            throw new RoomRuleException("The time limit must be 1 to 240 minutes (or none).");
        }
        int attempts = Math.max(1, Math.min(d.maxAttempts(), 20));
        List<UUID> picked = new ArrayList<>(new LinkedHashSet<>(d.questionIds() == null ? List.of() : d.questionIds()));
        if (picked.isEmpty() || picked.size() > 50) {
            throw new RoomRuleException("Choose between 1 and 50 questions.");
        }
        Instant due = d.dueOn() == null ? null : d.dueOn().plusDays(1).atStartOfDay(TimeLabels.DEFAULT_ZONE).toInstant().minusSeconds(60);
        if (due != null && due.isBefore(clock.instant())) {
            throw new RoomRuleException("The due date is in the past.");
        }
        List<UUID> skills = topicSkills(d.topicKey());
        return tx.call(c -> {
            Ctx ctx = rooms.require(c, actor, roomId, Action.AUTHOR);
            Set<UUID> allowed = questions.bankForSkills(c, skills).stream().map(QuestionRow::id).collect(Collectors.toSet());
            if (skills.isEmpty() || !allowed.containsAll(picked)) {
                throw new RoomRuleException("Some chosen questions aren't in that topic's bank.");
            }
            UUID subject = subjectOf(d.topicKey());
            UUID topic = catalog.subjects().stream().map(s -> catalog.graph(s.key())).filter(Optional::isPresent)
                    .map(g -> g.get().topic(d.topicKey())).filter(Optional::isPresent).map(t -> t.get().id()).findFirst().orElse(null);
            String key = "rm-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            UUID id = dao.createForRoom(c, roomId, key, subject, topic, kind, title,
                    d.instructions() == null || d.instructions().isBlank() ? null : d.instructions().strip(),
                    limit == null ? null : limit * 60, attempts, d.shuffle(), feedback, due, ctx.userId());
            dao.replaceItems(c, id, picked, picked.stream().map(x -> 1.0).toList());
            return id;
        });
    }

    private UUID subjectOf(String topicKey) {
        for (var s : catalog.subjects()) {
            if (catalog.graph(s.key()).flatMap(g -> g.topic(topicKey)).isPresent()) {
                return s.id();
            }
        }
        throw new RoomRuleException("Choose a topic.");
    }

    public void publish(Actor actor, UUID roomId, UUID assessmentId) {
        tx.run(c -> {
            rooms.require(c, actor, roomId, Action.PUBLISH);
            if (!dao.publishForRoom(c, roomId, assessmentId)) {
                throw new RoomRuleException("Only drafts of this room can be published.");
            }
        });
    }

    public void release(Actor actor, UUID roomId, UUID assessmentId) {
        tx.run(c -> {
            rooms.require(c, actor, roomId, Action.GRADE);
            if (!dao.releaseResults(c, roomId, assessmentId, clock.instant())) {
                throw new RoomRuleException("There are no results to release for that assessment.");
            }
        });
    }

    public void archive(Actor actor, UUID roomId, UUID assessmentId) {
        tx.run(c -> {
            rooms.require(c, actor, roomId, Action.AUTHOR);
            dao.archiveForRoom(c, roomId, assessmentId);
        });
    }

    /** Item analysis for teachers who may grade or see individual analytics. */
    public Optional<AssessmentService.Analysis> analysis(Actor actor, UUID roomId, UUID assessmentId) {
        Ctx ctx = rooms.check(actor, roomId, Action.USE_TEACHER_VIEW);
        if (!ctx.can(Action.GRADE) && !ctx.can(Action.VIEW_INDIVIDUAL_ANALYTICS)) {
            throw new app.pathwise.core.security.AccessDeniedException("room:analysis");
        }
        return assessmentService.roomAnalysis(roomId, assessmentId);
    }

    static String feedbackLabel(String policy, boolean released) {
        return switch (policy) {
            case "after_due" -> "Answers shown after the due date";
            case "manual_release" -> released ? "Results released" : "Results held until you release them";
            default -> "Answers shown on submit";
        };
    }

    static String kindLabel(String kind) {
        return switch (kind) {
            case "topic_test" -> "Topic test";
            case "practice_test" -> "Practice test";
            default -> "Quiz";
        };
    }

    static String typeLabel(String type) {
        return switch (type) {
            case "single_choice" -> "One answer";
            case "multi_choice" -> "Several answers";
            case "numeric" -> "Number";
            default -> "Short text";
        };
    }

    /** Markdown stem → short plain text for the picker. */
    static String plain(String md) {
        String s = md.replaceAll("```[\\s\\S]*?```", " [code] ").replaceAll("[`*_#>]", "").replaceAll("\\s+", " ").strip();
        return s.length() > 160 ? s.substring(0, 157) + "…" : s;
    }
}
