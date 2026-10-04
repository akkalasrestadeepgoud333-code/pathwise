package app.pathwise.web.rooms;

import app.pathwise.core.learner.MasteryState;
import app.pathwise.core.rooms.RoomPolicy.Action;
import app.pathwise.core.security.Actor;
import app.pathwise.data.Tx;
import app.pathwise.data.assessment.AssessmentDao;
import app.pathwise.data.assessment.AssessmentDao.AssessmentRow;
import app.pathwise.data.catalog.CatalogDao;
import app.pathwise.data.content.ContentDao;
import app.pathwise.data.learner.EvidenceDao;
import app.pathwise.data.learner.EvidenceDao.MasteryRow;
import app.pathwise.data.questions.QuestionDao;
import app.pathwise.data.rooms.RoomDao;
import app.pathwise.data.rooms.RoomDao.ItemRow;
import app.pathwise.data.rooms.RoomDao.MemberView;
import app.pathwise.web.platform.view.TimeLabels;
import app.pathwise.web.rooms.RoomService.Ctx;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What a teacher sees about a room: the cockpit (what needs doing now) and class analytics. Individual rows
 * need the analytics permission; aggregates are shown to any teacher of the room. All numbers are counts and
 * averages over evidence the platform recorded; nothing here is a prediction.
 */
public final class RoomInsightService {
    private final Tx tx;
    private final RoomService rooms;
    private final Clock clock;
    private final RoomDao dao = new RoomDao();
    private final AssessmentDao assessments = new AssessmentDao();
    private final ContentDao content = new ContentDao();
    private final QuestionDao questions = new QuestionDao();
    private final EvidenceDao evidence = new EvidenceDao();
    private final CatalogDao catalog = new CatalogDao();

    public RoomInsightService(Tx tx, RoomService rooms, Clock clock) {
        this.tx = tx;
        this.rooms = rooms;
        this.clock = clock;
    }

    public record Attention(UUID userId, String name, List<String> reasons) {}

    public record Deadline(UUID assessmentId, String title, String dueLabel, boolean overdue, int submitted, int students) {}

    public record Cockpit(RoomService.Header header, List<RoomService.Member> pending, List<Attention> attention,
                          List<Deadline> deadlines, List<RoomService.Announcement> announcements, boolean hasCurriculum) {}

    public Cockpit cockpit(Actor actor, UUID roomId) {
        Instant now = clock.instant();
        return tx.read(c -> {
            Ctx ctx = rooms.require(c, actor, roomId, Action.USE_TEACHER_VIEW);
            List<MemberView> members = dao.members(c, roomId);
            List<RoomService.Member> pending = members.stream().filter(m -> "pending".equals(m.status()))
                    .map(m -> new RoomService.Member(m.userId(), m.displayName(), m.email(), m.role(), RoomService.roleLabel(m.role()),
                            TimeLabels.relative(m.requestedAt(), now, TimeLabels.DEFAULT_ZONE), null, null)).toList();
            Data d = data(c, roomId, members, now);
            List<Deadline> deadlines = d.deadlines.stream()
                    .filter(x -> x.due().isAfter(now.minus(Duration.ofDays(3))) && x.due().isBefore(now.plus(Duration.ofDays(14))))
                    .map(x -> new Deadline(x.a().id(), x.a().title(), TimeLabels.dateTime(x.due(), TimeLabels.DEFAULT_ZONE),
                            x.due().isBefore(now), d.submitted.getOrDefault(x.a().id(), Set.of()).size(), d.students.size())).toList();
            List<Attention> attention = ctx.can(Action.VIEW_INDIVIDUAL_ANALYTICS) ? d.attention : List.of();
            return new Cockpit(rooms.header(c, ctx), pending, attention, deadlines, rooms.announcements(c, roomId, 5),
                    !dao.items(c, roomId).isEmpty());
        });
    }

    // ------------------------------------------------------------------ analytics

    public record SkillHeat(String skill, String topic, int needsSupport, int developing, int proficientOrMastered, int notStarted,
                            int needsPercent, int developingPercent, int strongPercent) {}

    public record StudentRow(UUID userId, String name, String email, int completionPercent, int needsSupport, Integer averagePercent,
                             int practice7d, int games7d, String lastActive) {}

    public record AssessmentStat(String title, int submitted, int students, Integer averagePercent, int[] buckets, UUID id,
                                 boolean ownedByRoom) {}

    public record Analytics(RoomService.Header header, int students, int active7d, Integer averageCompletion, List<SkillHeat> skills,
                            List<StudentRow> rows, boolean individual, List<AssessmentStat> assessments, List<Attention> attention) {}

    public Analytics analytics(Actor actor, UUID roomId) {
        Instant now = clock.instant();
        return tx.read(c -> {
            Ctx ctx = rooms.require(c, actor, roomId, Action.VIEW_CLASS_AGGREGATES);
            if (!ctx.can(Action.USE_TEACHER_VIEW)) {
                throw new app.pathwise.core.security.AccessDeniedException("room:analytics");
            }
            boolean individual = ctx.can(Action.VIEW_INDIVIDUAL_ANALYTICS);
            List<MemberView> members = dao.members(c, roomId);
            Data d = data(c, roomId, members, now);
            Map<UUID, RoomDao.Activity> activity = d.students.isEmpty() ? Map.of()
                    : dao.activitySince(c, d.students.stream().map(MemberView::userId).toList(), now.minus(Duration.ofDays(7)));

            List<StudentRow> rows = new ArrayList<>();
            int active = 0;
            double completionSum = 0;
            for (MemberView s : d.students) {
                int completion = d.completion.getOrDefault(s.userId(), 0);
                completionSum += completion;
                RoomDao.Activity a = activity.getOrDefault(s.userId(), new RoomDao.Activity(0, 0));
                if (s.lastEvidenceAt() != null && s.lastEvidenceAt().isAfter(now.minus(Duration.ofDays(7)))) {
                    active++;
                }
                List<Double> pcts = d.bestPercent.getOrDefault(s.userId(), Map.of()).values().stream().toList();
                rows.add(new StudentRow(s.userId(), s.displayName(), s.email(), completion, d.needsSupport.getOrDefault(s.userId(), 0),
                        pcts.isEmpty() ? null : (int) Math.round(pcts.stream().mapToDouble(x -> x).average().orElse(0)),
                        a.practiceAnswers(), a.games(),
                        s.lastEvidenceAt() == null ? "No activity yet" : TimeLabels.relative(s.lastEvidenceAt(), now, TimeLabels.DEFAULT_ZONE)));
            }
            rows.sort(Comparator.comparing(StudentRow::name, String.CASE_INSENSITIVE_ORDER));

            List<SkillHeat> heat = new ArrayList<>();
            Map<UUID, CatalogDao.SkillRef> refs = catalog.skillRefs(c, d.skills).stream()
                    .collect(Collectors.toMap(CatalogDao.SkillRef::id, r -> r));
            for (UUID skill : d.skills) {
                CatalogDao.SkillRef ref = refs.get(skill);
                if (ref == null) {
                    continue;
                }
                Map<MasteryState, Integer> counts = new EnumMap<>(MasteryState.class);
                for (MemberView s : d.students) {
                    MasteryRow m = d.mastery.getOrDefault(s.userId(), Map.of()).get(skill);
                    MasteryState st = m == null ? MasteryState.NOT_STARTED : MasteryState.fromDb(m.state());
                    counts.merge(st, 1, Integer::sum);
                }
                int needs = counts.getOrDefault(MasteryState.NEEDS_SUPPORT, 0);
                int developing = counts.getOrDefault(MasteryState.DEVELOPING, 0) + counts.getOrDefault(MasteryState.EXPLORING, 0);
                int strong = counts.getOrDefault(MasteryState.PROFICIENT, 0) + counts.getOrDefault(MasteryState.MASTERED, 0);
                int notStarted = counts.getOrDefault(MasteryState.NOT_STARTED, 0);
                int n = Math.max(1, d.students.size());
                heat.add(new SkillHeat(ref.title(), ref.topicTitle(), needs, developing, strong, notStarted,
                        step5(100.0 * needs / n), step5(100.0 * developing / n), step5(100.0 * strong / n)));
            }
            heat.sort(Comparator.comparingInt(SkillHeat::needsSupport).reversed().thenComparing(SkillHeat::skill));

            List<AssessmentStat> stats = new ArrayList<>();
            for (AssessmentRow a : d.roomAssessments) {
                List<Double> best = d.students.stream().map(s -> d.bestPercent.getOrDefault(s.userId(), Map.of()).get(a.id()))
                        .filter(x -> x != null).toList();
                int[] buckets = new int[4];
                best.forEach(p -> buckets[p < 40 ? 0 : p < 60 ? 1 : p < 80 ? 2 : 3]++);
                stats.add(new AssessmentStat(a.title(), best.size(), d.students.size(),
                        best.isEmpty() ? null : (int) Math.round(best.stream().mapToDouble(x -> x).average().orElse(0)), buckets, a.id(),
                        roomId.equals(a.ownerRoomId())));
            }
            Integer avg = d.students.isEmpty() ? null : (int) Math.round(completionSum / d.students.size());
            return new Analytics(rooms.header(c, ctx), d.students.size(), active, avg, heat, individual ? rows : List.of(), individual,
                    stats, individual ? d.attention : List.of());
        });
    }

    /** The per-student table as CSV (individual analytics permission required). */
    public String csv(Actor actor, UUID roomId) {
        rooms.check(actor, roomId, Action.VIEW_INDIVIDUAL_ANALYTICS);
        Analytics a = analytics(actor, roomId);
        StringBuilder sb = new StringBuilder("name,email,curriculum_done_percent,skills_needing_practice,assessment_average_percent,"
                + "practice_answers_7d,games_7d,last_active\r\n");
        for (StudentRow r : a.rows()) {
            sb.append(csvCell(r.name())).append(',').append(csvCell(r.email())).append(',').append(r.completionPercent()).append(',')
                    .append(r.needsSupport()).append(',').append(r.averagePercent() == null ? "" : r.averagePercent()).append(',')
                    .append(r.practice7d()).append(',').append(r.games7d()).append(',').append(csvCell(r.lastActive())).append("\r\n");
        }
        return sb.toString();
    }

    /** Quotes a cell and neutralises spreadsheet formulas (CSV injection). */
    static String csvCell(String v) {
        String s = v == null ? "" : v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ------------------------------------------------------------------ shared data

    private record DueItem(AssessmentRow a, Instant due) {}

    private static final class Data {
        List<MemberView> students;
        Set<UUID> skills = new LinkedHashSet<>();
        Map<UUID, Map<UUID, MasteryRow>> mastery = new HashMap<>();
        Map<UUID, Integer> needsSupport = new HashMap<>();
        Map<UUID, Integer> completion = new HashMap<>();
        Map<UUID, Map<UUID, Double>> bestPercent = new HashMap<>();
        Map<UUID, Set<UUID>> submitted = new HashMap<>();
        List<AssessmentRow> roomAssessments = new ArrayList<>();
        List<DueItem> deadlines = new ArrayList<>();
        List<Attention> attention = new ArrayList<>();
    }

    private Data data(Connection c, UUID roomId, List<MemberView> members, Instant now) throws SQLException {
        Data d = new Data();
        d.students = members.stream().filter(m -> "student".equals(m.role()) && "active".equals(m.status())).toList();
        List<ItemRow> items = dao.items(c, roomId);

        // The room's skills: what its modules teach and what its assessments test.
        Set<UUID> assessmentIds = new LinkedHashSet<>();
        for (ItemRow it : items) {
            if ("module".equals(it.kind())) {
                d.skills.addAll(content.skillIdsOfModule(c, it.refId()));
            } else if ("assessment".equals(it.kind())) {
                assessmentIds.add(it.refId());
            }
        }
        for (AssessmentRow a : assessments.ofRoom(c, roomId)) {
            if ("published".equals(a.status())) {
                assessmentIds.add(a.id());
            }
        }
        for (UUID aid : assessmentIds) {
            assessments.findById(c, aid).ifPresent(d.roomAssessments::add);
            List<UUID> qids = assessments.items(c, aid).stream().map(AssessmentDao.ItemRow::questionId).toList();
            if (!qids.isEmpty()) {
                questions.byIds(c, qids).forEach(q -> d.skills.addAll(q.skillIds()));
            }
        }
        d.roomAssessments.removeIf(a -> !"published".equals(a.status()));
        for (AssessmentRow a : d.roomAssessments) {
            if (a.dueAt() != null) {
                d.deadlines.add(new DueItem(a, a.dueAt()));
            }
        }
        d.deadlines.sort(Comparator.comparing(DueItem::due));

        for (AssessmentDao.RoomScore s : assessments.roomScores(c, roomId)) {
            d.submitted.computeIfAbsent(s.assessmentId(), k -> new java.util.HashSet<>()).add(s.userId());
            double pct = s.max() == 0 ? 0 : 100.0 * s.score() / s.max();
            d.bestPercent.computeIfAbsent(s.userId(), k -> new HashMap<>()).merge(s.assessmentId(), pct, Math::max);
        }

        List<ItemRow> required = rooms.requiredVisibleItems(c, roomId, now);
        for (MemberView s : d.students) {
            Map<UUID, MasteryRow> m = evidence.mastery(c, s.userId());
            d.mastery.put(s.userId(), m);
            int needs = (int) d.skills.stream().map(m::get)
                    .filter(x -> x != null && MasteryState.fromDb(x.state()) == MasteryState.NEEDS_SUPPORT).count();
            d.needsSupport.put(s.userId(), needs);
            Map<UUID, Boolean> st = rooms.statusOf(c, s.userId());
            long done = 0;
            for (ItemRow it : required) {
                if ("done".equals(rooms.itemStatus(c, it, s.userId(), st))) {
                    done++;
                }
            }
            d.completion.put(s.userId(), required.isEmpty() ? 0 : (int) Math.round(100.0 * done / required.size()));

            List<String> reasons = new ArrayList<>();
            if (needs >= 2) {
                reasons.add(needs + " room skills need practice");
            }
            boolean inactive = s.lastEvidenceAt() == null || s.lastEvidenceAt().isBefore(now.minus(Duration.ofDays(7)));
            for (DueItem due : d.deadlines) {
                boolean submitted = d.submitted.getOrDefault(due.a().id(), Set.of()).contains(s.userId());
                if (submitted) {
                    continue;
                }
                if (due.due().isBefore(now)) {
                    reasons.add("Missed the deadline for " + due.a().title());
                } else if (inactive && due.due().isBefore(now.plus(Duration.ofDays(5)))) {
                    reasons.add("Inactive for a week, with " + due.a().title() + " due soon");
                }
            }
            if (!reasons.isEmpty()) {
                d.attention.add(new Attention(s.userId(), s.displayName(), reasons));
            }
        }
        d.attention.sort(Comparator.comparingInt((Attention a) -> -a.reasons().size()).thenComparing(Attention::name));
        return d;
    }

    private static int step5(double pct) {
        return (int) (Math.round(pct / 5.0) * 5);
    }

}
