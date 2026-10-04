package app.pathwise.web.rewards;

import app.pathwise.core.learner.MasteryState;
import app.pathwise.core.rewards.Badges;
import app.pathwise.core.rewards.Badges.Badge;
import app.pathwise.core.rewards.Levels;
import app.pathwise.core.rewards.Streaks;
import app.pathwise.core.rewards.XpRules;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.NotAuthenticatedException;
import app.pathwise.data.Tx;
import app.pathwise.data.assessment.AssessmentDao;
import app.pathwise.data.catalog.CatalogDao;
import app.pathwise.data.community.DoubtDao;
import app.pathwise.data.learner.EvidenceDao;
import app.pathwise.data.rewards.RewardsDao;
import app.pathwise.data.rooms.RoomDao;
import app.pathwise.web.platform.Json;
import app.pathwise.web.platform.events.EventBus;
import app.pathwise.web.platform.view.TimeLabels;
import com.fasterxml.jackson.databind.JsonNode;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * XP, streaks, badges and in-app notifications. Everything here reacts to domain events after they commit
 * (via the outbox), and every XP change has a unique key, so a handler that runs twice changes nothing.
 * Nothing rewards logging in, time on page or asking doubts.
 */
public final class RewardsService {
    private final Tx tx;
    private final Clock clock;
    private final RewardsDao dao = new RewardsDao();
    private final DoubtDao doubts = new DoubtDao();
    private final RoomDao rooms = new RoomDao();
    private final AssessmentDao assessments = new AssessmentDao();
    private final EvidenceDao evidence = new EvidenceDao();
    private final CatalogDao catalog = new CatalogDao();

    public RewardsService(Tx tx, Clock clock) {
        this.tx = tx;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ event handlers

    public void register(EventBus bus) {
        bus.subscribe("content.module_completed", p -> tx.run(c -> {
            UUID u = uuid(p, "userId");
            dao.addXp(c, u, "MODULE_COMPLETE", XpRules.MODULE_COMPLETE, "module:" + u + ":" + p.path("moduleId").asText(), "Finished a module");
            dao.markMeaningful(c, u, today());
            evaluate(c, u);
        }));
        bus.subscribe("module_check.submitted", p -> tx.run(c -> {
            UUID u = uuid(p, "userId");
            double total = p.path("total").asDouble(0);
            if (total > 0 && p.path("score").asDouble(0) / total >= 0.5) {
                dao.markMeaningful(c, u, today());
            }
        }));
        bus.subscribe("practice.answered", p -> tx.run(c -> {
            UUID u = uuid(p, "userId");
            LocalDate day = today();
            boolean challenge = "challenge".equals(p.path("kind").asText());
            int count = dao.touchDay(c, u, day, 1, challenge);
            if (count >= 5) {
                dao.markMeaningful(c, u, day);
            }
            if (p.path("correct").asBoolean() && !p.path("repeat").asBoolean() && anySkillNotMastered(c, u, p.path("skills"))
                    && dao.xpSince(c, u, "PRACTICE_CORRECT", startOfToday()) < XpRules.PRACTICE_DAILY_CAP) {
                dao.addXp(c, u, "PRACTICE_CORRECT", XpRules.PRACTICE_CORRECT, "practice:" + u + ":" + p.path("questionId").asText() + ":" + day,
                        "Correct practice answer");
            }
            evaluate(c, u);
        }));
        bus.subscribe("game.completed", p -> tx.run(c -> {
            UUID u = uuid(p, "userId");
            double acc = p.path("accuracy").asDouble(0);
            LocalDate day = today();
            if (acc >= 0.5) {
                dao.markMeaningful(c, u, day);
            }
            int xp = XpRules.game(acc);
            String prefix = "game:" + u + ":" + p.path("activityId").asText() + ":" + day + ":";
            if (xp > 0 && dao.countKeysLike(c, prefix) < XpRules.GAMES_PER_DAY_PER_GAME) {
                dao.addXp(c, u, "GAME_COMPLETE", xp, prefix + p.path("attemptId").asText(), "Verified game at " + Math.round(acc * 100) + "%");
            }
            evaluate(c, u);
        }));
        bus.subscribe("assessment.graded", p -> tx.run(c -> {
            UUID u = uuid(p, "userId");
            UUID assessment = uuid(p, "assessmentId");
            UUID attempt = uuid(p, "attemptId");
            dao.markMeaningful(c, u, today());
            String title = assessments.findById(c, assessment).map(AssessmentDao.AssessmentRow::title).orElse("an assessment");
            dao.addXp(c, u, "ASSESSMENT_SUBMIT", XpRules.ASSESSMENT_SUBMIT, "assess:" + u + ":" + assessment, "Submitted " + title);
            double best = -1;
            double now = 0;
            for (AssessmentDao.AttemptRow t : assessments.attempts(c, assessment, u)) {
                if (!"graded".equals(t.status()) || t.maxScore() == null || t.maxScore() == 0) {
                    continue;
                }
                double f = t.score() / t.maxScore();
                if (t.id().equals(attempt)) {
                    now = f;
                } else {
                    best = Math.max(best, f);
                }
            }
            if (best >= 0 && XpRules.improved(best, now)) {
                dao.addXp(c, u, "ASSESSMENT_IMPROVE", XpRules.ASSESSMENT_IMPROVE, "improve:" + attempt, "Improved on " + title);
            }
            dao.notify(c, u, "assessment_result", "Your result for " + title + " is ready", null, "/app/assessments/result/" + attempt, null);
            evaluate(c, u);
        }));
        bus.subscribe("learner.mastery_changed", p -> tx.run(c -> {
            UUID u = uuid(p, "userId");
            UUID skill = uuid(p, "skillId");
            String to = p.path("to").asText();
            dao.transition(c, u, skill, p.path("from").asText(), to);
            String skillTitle = catalog.skillRefs(c, List.of(skill)).stream().findFirst().map(CatalogDao.SkillRef::title).orElse("a skill");
            if ("proficient".equals(to) || "mastered".equals(to)) {
                dao.addXp(c, u, "SKILL_PROFICIENT", XpRules.SKILL_PROFICIENT, "prof:" + u + ":" + skill, "Proficient: " + skillTitle);
            }
            if ("mastered".equals(to) && dao.addXp(c, u, "SKILL_MASTERED", XpRules.SKILL_MASTERED, "mast:" + u + ":" + skill, "Mastered: " + skillTitle)) {
                dao.notify(c, u, "milestone", "You mastered " + skillTitle, "Backed by recent assessment or challenge evidence.", "/app/progress", null);
            }
            evaluate(c, u);
        }));
        bus.subscribe("doubt.answered", p -> tx.run(c -> {
            UUID threadId = uuid(p, "threadId");
            Optional<DoubtDao.ThreadRow> t = doubts.thread(c, threadId);
            Optional<DoubtDao.PostRow> post = doubts.post(c, uuid(p, "postId"));
            if (t.isEmpty() || post.isEmpty()) {
                return;
            }
            String group = "doubt:" + threadId;
            if (!post.get().authorId().equals(t.get().authorId())) {
                if (!dao.unreadInGroup(c, t.get().authorId(), group)) {
                    dao.notify(c, t.get().authorId(), "doubt_answered", "New answer to your doubt", t.get().title(), "/app/doubts/" + threadId, group);
                }
            } else if (t.get().roomId() != null) {
                for (UUID teacher : teachers(c, t.get().roomId())) {
                    if (!dao.unreadInGroup(c, teacher, group)) {
                        dao.notify(c, teacher, "doubt_reply", "A student replied to their doubt", t.get().title(), "/app/doubts/" + threadId, group);
                    }
                }
            }
        }));
        bus.subscribe("doubt.asked", p -> tx.run(c -> {
            Optional<DoubtDao.ThreadRow> t = doubts.thread(c, uuid(p, "threadId"));
            if (t.isPresent() && t.get().roomId() != null) {
                for (UUID teacher : teachers(c, t.get().roomId())) {
                    dao.notify(c, teacher, "room_activity", "New private doubt", t.get().title(), "/app/doubts/" + t.get().id(), null);
                }
            }
        }));
        bus.subscribe("doubt.resolved", p -> tx.run(c -> {
            Optional<DoubtDao.PostRow> post = doubts.post(c, uuid(p, "postId"));
            if (post.isEmpty()) {
                return;
            }
            UUID author = post.get().authorId();
            if (dao.addXp(c, author, "ANSWER_ACCEPTED", XpRules.ANSWER_ACCEPTED, "accepted:" + post.get().id(), "Your answer was accepted")) {
                dao.markMeaningful(c, author, today());
                dao.notify(c, author, "doubt_answered", "Your answer was accepted", "It helped someone resolve their doubt.",
                        "/app/doubts/" + post.get().threadId(), null);
            }
            evaluate(c, author);
        }));
        bus.subscribe("doubt.answer_verified", p -> tx.run(c -> {
            Optional<DoubtDao.PostRow> post = doubts.post(c, uuid(p, "postId"));
            if (post.isEmpty()) {
                return;
            }
            UUID author = post.get().authorId();
            if (dao.addXp(c, author, "ANSWER_VERIFIED", XpRules.ANSWER_VERIFIED, "verified:" + post.get().id(), "Your answer was teacher verified")) {
                dao.notify(c, author, "doubt_answered", "A teacher verified your answer", null, "/app/doubts/" + post.get().threadId(), null);
            }
            evaluate(c, author);
        }));
        bus.subscribe("doubt.answer_removed", p -> tx.run(c -> {
            Optional<DoubtDao.PostRow> post = doubts.post(c, uuid(p, "postId"));
            if (post.isEmpty()) {
                return;
            }
            UUID author = post.get().authorId();
            // Moderation reverses community XP with a negative event; learning XP is never taken back.
            if (dao.hasXpKey(c, "accepted:" + post.get().id())) {
                dao.addXp(c, author, "ANSWER_ACCEPTED", -XpRules.ANSWER_ACCEPTED, "accepted-reversed:" + post.get().id(), "Answer removed by a moderator");
            }
            if (dao.hasXpKey(c, "verified:" + post.get().id())) {
                dao.addXp(c, author, "ANSWER_VERIFIED", -XpRules.ANSWER_VERIFIED, "verified-reversed:" + post.get().id(), "Answer removed by a moderator");
            }
        }));
        bus.subscribe("assignment.graded", p -> tx.run(c -> dao.notify(c, uuid(p, "userId"), "assignment_graded", "Your assignment was graded",
                null, "/app/rooms/" + p.path("roomId").asText() + "/assignments/" + p.path("assignmentId").asText(), null)));
        bus.subscribe("assignment.submitted", p -> tx.run(c -> {
            UUID room = uuid(p, "roomId");
            for (UUID teacher : teachers(c, room)) {
                String group = "submissions:" + room;
                if (!dao.unreadInGroup(c, teacher, group)) {
                    dao.notify(c, teacher, "room_activity", "New work to grade", null, "/teach/rooms/" + room + "/assignments", group);
                }
            }
        }));
        bus.subscribe("room.join_requested", p -> tx.run(c -> {
            UUID room = uuid(p, "roomId");
            for (UUID teacher : teachers(c, room)) {
                String group = "joinreq:" + room;
                if (!dao.unreadInGroup(c, teacher, group)) {
                    dao.notify(c, teacher, "room_activity", "Students are waiting to join", null, "/teach/rooms/" + room + "/people", group);
                }
            }
        }));
        bus.subscribe("room.announcement_posted", p -> tx.run(c -> {
            UUID room = uuid(p, "roomId");
            Optional<RoomDao.RoomRow> r = rooms.find(c, room);
            String body = rooms.announcements(c, room, 1).stream().findFirst().map(RoomDao.AnnouncementRow::body).orElse(null);
            for (UUID student : rooms.activeStudents(c, room)) {
                String group = "announce:" + room;
                if (!dao.unreadInGroup(c, student, group)) {
                    dao.notify(c, student, "announcement", "New announcement in " + r.map(RoomDao.RoomRow::name).orElse("your room"), body,
                            "/app/rooms/" + room, group);
                }
            }
        }));
    }

    private boolean anySkillNotMastered(Connection c, UUID user, JsonNode skills) throws SQLException {
        Map<UUID, EvidenceDao.MasteryRow> m = evidence.mastery(c, user);
        for (JsonNode s : skills) {
            EvidenceDao.MasteryRow row = m.get(UUID.fromString(s.asText()));
            if (row == null || !"mastered".equals(row.state())) {
                return true;
            }
        }
        return false;
    }

    private List<UUID> teachers(Connection c, UUID roomId) throws SQLException {
        return rooms.members(c, roomId).stream().filter(m -> "active".equals(m.status()) && !"student".equals(m.role()))
                .map(RoomDao.MemberView::userId).toList();
    }

    // ------------------------------------------------------------------ badges

    /** Awards every badge whose criteria are now met. Idempotent: awards and their XP are unique per user and badge. */
    void evaluate(Connection c, UUID user) throws SQLException {
        Map<String, Instant> held = dao.awards(c, user);
        Badges.Facts facts = facts(c, user);
        for (Badge b : Badges.CATALOGUE) {
            if (held.containsKey(b.key())) {
                continue;
            }
            Badges.Progress pr = Badges.progress(b.criterion(), facts);
            if (pr.met() && dao.award(c, user, b.key(), Json.write(Map.of("detail", pr.detail(), "rule", b.criterion().toString())))) {
                dao.addXp(c, user, "BADGE_EARNED", b.rarity().xp, "badge:" + user + ":" + b.key(), "Badge: " + b.name());
                dao.notify(c, user, "badge_earned", "You earned " + b.name(), b.description(), "/app/achievements", null);
            }
        }
    }

    private Badges.Facts facts(Connection c, UUID user) {
        return new BadgeFactsService(dao, c, user, today());
    }

    /** Badge facts for one user, read from recorded evidence (a named class so the architecture rules can see it). */
    private static final class BadgeFactsService implements Badges.Facts {
        private final RewardsDao dao;
        private final Connection c;
        private final UUID user;
        private final LocalDate today;
        private RewardsDao.PracticeFacts practice;

        BadgeFactsService(RewardsDao dao, Connection c, UUID user, LocalDate today) {
            this.dao = dao;
            this.c = c;
            this.user = user;
            this.today = today;
        }

        private RewardsDao.PracticeFacts practice() {
            if (practice == null) {
                practice = call(() -> dao.practice(c, user));
            }
            return practice;
        }

        public int modulesCompleted() { return call(() -> dao.modulesCompleted(c, user)); }
        public int coreSkillsAtLeast(String s, List<String> t) { return call(() -> dao.coreSkillsAtLeast(c, user, atLeast(s), t)); }
        public int topicsFullyMastered() { return call(() -> dao.topicsFullyMastered(c, user)); }
        public int gamesAtAccuracy(String t, double a) { return call(() -> dao.gamesAtAccuracy(c, user, t, a)); }
        public int gameSkillsAtAccuracy(String t, double a) { return call(() -> dao.gameSkillsAtAccuracy(c, user, t, a)); }
        public int practiceFirstAnswers() { return practice().firstAnswers(); }
        public double practiceAccuracy() { return practice().rollingAccuracy(); }
        public int practiceSkills() { return practice().skills(); }
        public int assessmentsAtLeast(String k, double f) { return call(() -> dao.assessmentsAtLeast(c, user, k, f)); }
        public int comebacks() { return call(() -> dao.comebacks(c, user)); }
        public int acceptedAnswers() { return call(() -> dao.acceptedAnswers(c, user)); }
        public int distinctAskersAccepted() { return call(() -> dao.distinctAskersAccepted(c, user)); }
        public int verifiedAnswers() { return call(() -> dao.verifiedAnswers(c, user)); }
        public int longestStreak() { return call(() -> Streaks.compute(dao.meaningfulDays(c, user), today).longest()); }
    }
    private static List<String> atLeast(String state) {
        List<String> out = new ArrayList<>();
        boolean on = false;
        for (MasteryState s : MasteryState.values()) {
            on |= s.dbValue().equals(state);
            if (on) {
                out.add(s.dbValue());
            }
        }
        return out;
    }

    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }

    private static <T> T call(SqlSupplier<T> s) {
        try {
            return s.get();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ views

    public record Summary(int level, long xp, int percentToNext, long nextLevelAt, int streak, boolean activeToday, String nextBadge,
                          String nextBadgeDetail, int nextBadgePercent, int unread) {}

    public record XpLine(String note, int xp, String when) {}

    public record BadgeView(String key, String name, String description, String category, String rarity, String icon, boolean earned,
                            String earnedLabel, int percent, String detail) {}

    public record Achievements(Summary summary, int longestStreak, int freezes, List<XpLine> recent, List<BadgeView> earned,
                               List<BadgeView> locked) {}

    public Summary summary(Actor actor) {
        requireUser(actor);
        return tx.read(c -> summary(c, actor.userId()));
    }

    private Summary summary(Connection c, UUID user) throws SQLException {
        Levels.Level lv = Levels.of(dao.totalXp(c, user));
        Streaks.Streak st = Streaks.compute(dao.meaningfulDays(c, user), today());
        Map<String, Instant> held = dao.awards(c, user);
        Badges.Facts f = facts(c, user);
        Badge best = null;
        Badges.Progress bestP = null;
        for (Badge b : Badges.CATALOGUE) {
            if (held.containsKey(b.key())) {
                continue;
            }
            Badges.Progress p = Badges.progress(b.criterion(), f);
            if (bestP == null || p.percent() > bestP.percent()) {
                best = b;
                bestP = p;
            }
        }
        return new Summary(lv.level(), lv.xp(), lv.percentToNext(), lv.nextLevelAt(), st.current(), st.activeToday(),
                best == null ? null : best.name(), bestP == null ? null : bestP.detail(), bestP == null ? 0 : bestP.percent(), dao.unread(c, user));
    }

    public Achievements achievements(Actor actor) {
        requireUser(actor);
        Instant now = clock.instant();
        return tx.read(c -> {
            UUID user = actor.userId();
            Summary s = summary(c, user);
            Streaks.Streak st = Streaks.compute(dao.meaningfulDays(c, user), today());
            List<XpLine> recent = dao.recentXp(c, user, 15).stream()
                    .map(x -> new XpLine(x.note() == null ? x.rule() : x.note(), x.xp(), TimeLabels.relative(x.at(), now, TimeLabels.DEFAULT_ZONE))).toList();
            Map<String, Instant> held = dao.awards(c, user);
            Badges.Facts f = facts(c, user);
            List<BadgeView> earned = new ArrayList<>();
            List<BadgeView> locked = new ArrayList<>();
            for (Badge b : Badges.CATALOGUE) {
                if (held.containsKey(b.key())) {
                    earned.add(new BadgeView(b.key(), b.name(), b.description(), b.category(), b.rarity().label(), b.icon(), true,
                            TimeLabels.date(held.get(b.key()), TimeLabels.DEFAULT_ZONE), 100, null));
                } else {
                    Badges.Progress p = Badges.progress(b.criterion(), f);
                    locked.add(new BadgeView(b.key(), b.name(), b.description(), b.category(), b.rarity().label(), b.icon(), false, null,
                            p.percent(), p.detail()));
                }
            }
            locked.sort((a, b) -> Integer.compare(b.percent(), a.percent()));
            return new Achievements(s, st.longest(), st.freezesHeld(), recent, earned, locked);
        });
    }

    public record NotificationView(UUID id, String type, String title, String body, String link, String when, boolean unread) {}

    public List<NotificationView> notifications(Actor actor) {
        requireUser(actor);
        Instant now = clock.instant();
        return tx.read(c -> dao.notifications(c, actor.userId(), 50).stream().map(n -> new NotificationView(n.id(), n.type(), n.title(),
                n.body(), n.link(), TimeLabels.relative(n.createdAt(), now, TimeLabels.DEFAULT_ZONE), n.readAt() == null)).toList());
    }

    public int unread(UUID userId) {
        return tx.read(c -> dao.unread(c, userId));
    }

    /** Marks one read and returns where it points (only a local path, never an external URL). */
    public Optional<String> open(Actor actor, UUID id) {
        requireUser(actor);
        return tx.call(c -> dao.markRead(c, actor.userId(), id)).filter(l -> l.startsWith("/") && !l.startsWith("//"));
    }

    public void markAllRead(Actor actor) {
        requireUser(actor);
        tx.run(c -> dao.markAllRead(c, actor.userId()));
    }

    public int purgeOld() {
        return tx.call(c -> dao.purgeNotifications(c, clock.instant().minus(java.time.Duration.ofDays(180))));
    }

    /** Re-checks badges for a user (e.g. after a streak day), outside any event. */
    public void reevaluate(UUID userId) {
        tx.run(c -> evaluate(c, userId));
    }

    // ------------------------------------------------------------------ helpers

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), TimeLabels.DEFAULT_ZONE);
    }

    private Instant startOfToday() {
        return today().atStartOfDay(TimeLabels.DEFAULT_ZONE).toInstant();
    }

    private static UUID uuid(JsonNode p, String field) {
        return UUID.fromString(p.path(field).asText());
    }

    private static void requireUser(Actor actor) {
        if (actor == null || !actor.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }
    }
}
