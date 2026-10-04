package app.pathwise.web.rooms;

import app.pathwise.core.rooms.RoomPolicy.Action;
import app.pathwise.core.security.AccessDeniedException;
import app.pathwise.core.security.Actor;
import app.pathwise.data.Tx;
import app.pathwise.data.catalog.CatalogDao;
import app.pathwise.data.learner.EvidenceDao;
import app.pathwise.data.rooms.AssignmentDao;
import app.pathwise.data.rooms.AssignmentDao.AssignmentRow;
import app.pathwise.data.rooms.AssignmentDao.SubmissionRow;
import app.pathwise.data.rooms.RoomDao;
import app.pathwise.web.catalog.CatalogService;
import app.pathwise.web.content.Markdown;
import app.pathwise.web.content.SafeHtml;
import app.pathwise.web.learner.LearnerService;
import app.pathwise.web.platform.events.EventBus;
import app.pathwise.web.platform.view.TimeLabels;
import app.pathwise.web.rooms.RoomService.Ctx;
import app.pathwise.web.rooms.RoomService.RoomNotFoundException;
import app.pathwise.web.rooms.RoomService.RoomRuleException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Individual assignments in a room: teachers set and grade them; students draft, hand in and see feedback.
 * A grade becomes official evidence for the assignment's skills (weight 0.9, outcome = grade fraction).
 */
public final class AssignmentService {
    public static final double W_ASSIGNMENT = 0.9;
    public static final String EVENT_GRADED = "assignment.graded";

    private final Tx tx;
    private final RoomService rooms;
    private final CatalogService catalog;
    private final LearnerService learner;
    private final EventBus events;
    private final Clock clock;
    private final AssignmentDao dao = new AssignmentDao();
    private final RoomDao roomDao = new RoomDao();
    private final EvidenceDao evidence = new EvidenceDao();
    private final CatalogDao catalogDao = new CatalogDao();

    public AssignmentService(Tx tx, RoomService rooms, CatalogService catalog, LearnerService learner, EventBus events, Clock clock) {
        this.tx = tx;
        this.rooms = rooms;
        this.catalog = catalog;
        this.learner = learner;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ teacher

    public record Row(UUID id, String title, String status, String statusLabel, String dueLabel, String points, int submitted,
                      int toGrade, int graded, int students, boolean canPublish) {}

    public record SkillChoice(String id, String title, String topic) {}

    public record Page(RoomService.Header header, List<Row> rows, List<SkillChoice> skills) {}

    public Page page(Actor actor, UUID roomId) {
        return tx.read(c -> {
            Ctx ctx = rooms.require(c, actor, roomId, Action.USE_TEACHER_VIEW);
            int students = roomDao.activeStudents(c, roomId).size();
            List<Row> rows = new ArrayList<>();
            for (AssignmentRow a : dao.ofRoom(c, roomId)) {
                var subs = dao.submissionsOf(c, a.id());
                int submitted = (int) subs.stream().filter(s -> !"draft".equals(s.s().status())).count();
                int toGrade = (int) subs.stream().filter(s -> "submitted".equals(s.s().status())).count();
                int graded = (int) subs.stream().filter(s -> "graded".equals(s.s().status())).count();
                rows.add(new Row(a.id(), a.title(), a.status(), "draft".equals(a.status()) ? "Draft" : "Published", due(a),
                        fmt(a.maxPoints()) + " points", submitted, toGrade, graded, students,
                        "draft".equals(a.status()) && ctx.can(Action.PUBLISH)));
            }
            List<SkillChoice> skills = new ArrayList<>();
            for (var s : catalog.subjects()) {
                if (ctx.room().subjectId() != null && !ctx.room().subjectId().equals(s.id())) {
                    continue;
                }
                catalog.graph(s.key()).ifPresent(g -> g.topics().forEach(t -> g.skillsOf(t.id())
                        .forEach(k -> skills.add(new SkillChoice(k.id().toString(), k.title(), t.title())))));
            }
            return new Page(rooms.header(c, ctx), rows, skills);
        });
    }

    public record Draft(String title, String instructions, double maxPoints, LocalDate dueOn, boolean allowLate, List<UUID> skillIds) {}

    public UUID create(Actor actor, UUID roomId, Draft d) {
        String title = d.title() == null ? "" : d.title().strip();
        if (title.length() < 2 || title.length() > 160) {
            throw new RoomRuleException("Give the assignment a title of 2 to 160 characters.");
        }
        if (d.maxPoints() <= 0 || d.maxPoints() > 1000) {
            throw new RoomRuleException("Points must be between 1 and 1000.");
        }
        String instructions = d.instructions() == null || d.instructions().isBlank() ? null : d.instructions().strip();
        if (instructions != null && instructions.length() > 8000) {
            throw new RoomRuleException("Instructions can be at most 8000 characters.");
        }
        Instant due = d.dueOn() == null ? null : d.dueOn().plusDays(1).atStartOfDay(TimeLabels.DEFAULT_ZONE).toInstant().minusSeconds(60);
        if (due != null && due.isBefore(clock.instant())) {
            throw new RoomRuleException("The due date is in the past.");
        }
        Set<UUID> skills = new LinkedHashSet<>(d.skillIds() == null ? List.of() : d.skillIds());
        if (skills.size() > 10) {
            throw new RoomRuleException("Link at most 10 skills.");
        }
        return tx.call(c -> {
            Ctx ctx = rooms.require(c, actor, roomId, Action.AUTHOR);
            Set<UUID> allowed = new HashSet<>();
            catalog.subjects().stream().filter(s -> ctx.room().subjectId() == null || ctx.room().subjectId().equals(s.id()))
                    .forEach(s -> catalog.graph(s.key()).ifPresent(g -> g.skills().forEach(k -> allowed.add(k.id()))));
            if (!allowed.containsAll(skills)) {
                throw new RoomRuleException("Some chosen skills aren't part of this room's subject.");
            }
            UUID id = dao.create(c, roomId, actor.userId(), title, instructions, d.maxPoints(), due, d.allowLate());
            dao.setSkills(c, id, skills);
            return id;
        });
    }

    public void publish(Actor actor, UUID roomId, UUID assignmentId) {
        tx.run(c -> {
            rooms.require(c, actor, roomId, Action.PUBLISH);
            if (!dao.setStatus(c, roomId, assignmentId, "draft", "published")) {
                throw new RoomRuleException("Only drafts of this room can be published.");
            }
        });
    }

    public void archive(Actor actor, UUID roomId, UUID assignmentId) {
        tx.run(c -> {
            rooms.require(c, actor, roomId, Action.AUTHOR);
            dao.setStatus(c, roomId, assignmentId, null, "archived");
        });
    }

    public record SubmissionItem(UUID userId, String name, String status, String statusLabel, String submittedLabel, boolean late,
                                 String body, String grade, String feedback, int version) {}

    public record Grading(RoomService.Header header, UUID id, String title, SafeHtml instructions, String dueLabel, double maxPoints,
                          String points, List<String> skills, List<SubmissionItem> submissions, List<String> missing, boolean canGrade) {}

    public Grading grading(Actor actor, UUID roomId, UUID assignmentId) {
        Instant now = clock.instant();
        return tx.read(c -> {
            Ctx ctx = rooms.require(c, actor, roomId, Action.USE_TEACHER_VIEW);
            if (!ctx.can(Action.GRADE) && !ctx.can(Action.VIEW_INDIVIDUAL_ANALYTICS)) {
                throw new AccessDeniedException("room:grading");
            }
            AssignmentRow a = dao.find(c, assignmentId).filter(x -> x.roomId().equals(roomId)).orElseThrow(RoomNotFoundException::new);
            var subs = dao.submissionsOf(c, assignmentId);
            List<SubmissionItem> items = subs.stream().filter(s -> !"draft".equals(s.s().status()))
                    .map(s -> new SubmissionItem(s.s().userId(), s.displayName(), s.s().status(), statusLabel(s.s().status()),
                            s.s().submittedAt() == null ? "" : TimeLabels.relative(s.s().submittedAt(), now, TimeLabels.DEFAULT_ZONE),
                            s.s().late(), s.s().body(), s.s().grade() == null ? "" : fmt(s.s().grade()), s.s().feedback(), s.s().version()))
                    .toList();
            Set<UUID> handedIn = subs.stream().filter(s -> !"draft".equals(s.s().status())).map(s -> s.s().userId()).collect(Collectors.toSet());
            List<String> missing = roomDao.members(c, roomId).stream()
                    .filter(m -> "student".equals(m.role()) && "active".equals(m.status()) && !handedIn.contains(m.userId()))
                    .map(RoomDao.MemberView::displayName).toList();
            List<String> skills = catalogDao.skillRefs(c, dao.skills(c, assignmentId)).stream().map(CatalogDao.SkillRef::title).toList();
            return new Grading(rooms.header(c, ctx), a.id(), a.title(), Markdown.render(a.instructions() == null ? "" : a.instructions()),
                    due(a), a.maxPoints(), fmt(a.maxPoints()), skills, items, missing, ctx.can(Action.GRADE));
        });
    }

    /** Grades a handed-in submission (or sends it back), once per hand-in. Grading writes official evidence. */
    public void grade(Actor actor, UUID roomId, UUID assignmentId, UUID studentId, String gradeText, String feedback, boolean sendBack) {
        String fb = feedback == null || feedback.isBlank() ? null : feedback.strip();
        if (fb != null && fb.length() > 8000) {
            throw new RoomRuleException("Feedback can be at most 8000 characters.");
        }
        Instant now = clock.instant();
        tx.run(c -> {
            Ctx ctx = rooms.require(c, actor, roomId, Action.GRADE);
            AssignmentRow a = dao.find(c, assignmentId).filter(x -> x.roomId().equals(roomId)).orElseThrow(RoomNotFoundException::new);
            SubmissionRow s = dao.lock(c, assignmentId, studentId).filter(x -> "submitted".equals(x.status()))
                    .orElseThrow(() -> new RoomRuleException("There's no handed-in work waiting to be graded for that student."));
            if (sendBack) {
                if (fb == null) {
                    throw new RoomRuleException("Say what to change when sending work back.");
                }
                dao.returnForRevision(c, s.id(), fb, ctx.userId(), now);
                return;
            }
            double grade;
            try {
                grade = Double.parseDouble(gradeText == null ? "" : gradeText.strip().replace(',', '.'));
            } catch (NumberFormatException e) {
                throw new RoomRuleException("Enter the grade as a number of points.");
            }
            if (grade < 0 || grade > a.maxPoints() || Double.isNaN(grade)) {
                throw new RoomRuleException("The grade must be between 0 and " + fmt(a.maxPoints()) + ".");
            }
            grade = Math.round(grade * 100) / 100.0;
            dao.grade(c, s.id(), grade, fb, ctx.userId(), now);
            double outcome = Math.round(1000 * grade / a.maxPoints()) / 1000.0;
            List<UUID> skills = dao.skills(c, assignmentId);
            for (UUID skill : skills) {
                evidence.insertForSubmission(c, studentId, skill, s.id(), outcome, W_ASSIGNMENT, 3, now);
            }
            learner.recompute(c, studentId, skills, now);
            events.publish(c, EVENT_GRADED, Map.of("roomId", roomId.toString(), "assignmentId", assignmentId.toString(),
                    "userId", studentId.toString(), "grade", grade));
        });
    }

    // ------------------------------------------------------------------ student

    public record StudentView(UUID roomId, String roomName, UUID id, String title, SafeHtml instructions, String dueLabel,
                              boolean overdue, boolean allowLate, String points, List<String> skills, String status,
                              String statusLabel, String body, String grade, String feedback, boolean canEdit, boolean late) {}

    public StudentView studentView(Actor actor, UUID roomId, UUID assignmentId) {
        Instant now = clock.instant();
        return tx.read(c -> {
            Ctx ctx = rooms.require(c, actor, roomId, Action.VIEW_ROOM);
            AssignmentRow a = dao.find(c, assignmentId)
                    .filter(x -> x.roomId().equals(roomId) && "published".equals(x.status())).orElseThrow(RoomNotFoundException::new);
            Optional<SubmissionRow> s = dao.submission(c, assignmentId, actor.userId());
            String status = s.map(SubmissionRow::status).orElse("none");
            boolean overdue = a.dueAt() != null && now.isAfter(a.dueAt());
            boolean canEdit = ctx.can(Action.DO_ROOM_WORK) && !"graded".equals(status) && !"submitted".equals(status) && (!overdue || a.allowLate());
            List<String> skills = catalogDao.skillRefs(c, dao.skills(c, assignmentId)).stream().map(CatalogDao.SkillRef::title).toList();
            return new StudentView(roomId, ctx.room().name(), a.id(), a.title(), Markdown.render(a.instructions() == null ? "" : a.instructions()),
                    due(a), overdue, a.allowLate(), fmt(a.maxPoints()), skills, status, statusLabel(status),
                    s.map(SubmissionRow::body).orElse(""), s.map(x -> x.grade() == null ? "" : fmt(x.grade())).orElse(""),
                    s.map(SubmissionRow::feedback).orElse(null), canEdit, s.map(SubmissionRow::late).orElse(false));
        });
    }

    /** Saves the text, and hands it in if {@code handIn}. */
    public void save(Actor actor, UUID roomId, UUID assignmentId, String body, boolean handIn) {
        String text = body == null ? "" : body.strip();
        if (text.length() > 20000) {
            throw new RoomRuleException("Your answer can be at most 20,000 characters.");
        }
        if (handIn && text.isEmpty()) {
            throw new RoomRuleException("Write something before handing in.");
        }
        Instant now = clock.instant();
        tx.run(c -> {
            rooms.require(c, actor, roomId, Action.DO_ROOM_WORK);
            AssignmentRow a = dao.find(c, assignmentId)
                    .filter(x -> x.roomId().equals(roomId) && "published".equals(x.status())).orElseThrow(RoomNotFoundException::new);
            Optional<SubmissionRow> existing = dao.lock(c, assignmentId, actor.userId());
            String status = existing.map(SubmissionRow::status).orElse("none");
            if ("graded".equals(status) || "submitted".equals(status)) {
                throw new RoomRuleException("This has been handed in. You can edit it again if your teacher sends it back.");
            }
            boolean overdue = a.dueAt() != null && now.isAfter(a.dueAt());
            if (overdue && !a.allowLate()) {
                throw new RoomRuleException("The deadline has passed and this assignment doesn't accept late work.");
            }
            dao.saveDraft(c, assignmentId, actor.userId(), text, now);
            if (handIn) {
                dao.submit(c, assignmentId, actor.userId(), overdue, now);
                events.publish(c, "assignment.submitted", Map.of("roomId", roomId.toString(), "assignmentId", assignmentId.toString(),
                        "userId", actor.userId().toString()));
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    static String statusLabel(String status) {
        return switch (status) {
            case "draft" -> "Draft saved";
            case "submitted" -> "Handed in";
            case "returned" -> "Sent back for changes";
            case "graded" -> "Graded";
            default -> "Not started";
        };
    }

    private static String due(AssignmentRow a) {
        return a.dueAt() == null ? "No due date" : "Due " + TimeLabels.dateTime(a.dueAt(), TimeLabels.DEFAULT_ZONE);
    }

    static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
    }
}
