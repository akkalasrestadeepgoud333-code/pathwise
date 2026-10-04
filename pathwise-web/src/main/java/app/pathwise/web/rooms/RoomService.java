package app.pathwise.web.rooms;

import app.pathwise.core.identity.PlatformRole;
import app.pathwise.core.rooms.RoomCodes;
import app.pathwise.core.rooms.RoomPolicy;
import app.pathwise.core.rooms.RoomPolicy.Action;
import app.pathwise.core.security.AccessDeniedException;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.NotAuthenticatedException;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.core.security.PlatformPolicy;
import app.pathwise.core.security.Tokens;
import app.pathwise.data.Tx;
import app.pathwise.data.assessment.AssessmentDao;
import app.pathwise.data.content.ContentDao;
import app.pathwise.data.content.ProgressDao;
import app.pathwise.data.games.GameDao;
import app.pathwise.data.rooms.RoomDao;
import app.pathwise.data.rooms.RoomDao.ItemRow;
import app.pathwise.data.rooms.RoomDao.MemberRow;
import app.pathwise.data.rooms.RoomDao.MemberView;
import app.pathwise.data.rooms.RoomDao.RoomRow;
import app.pathwise.data.rooms.RoomDao.UnitRow;
import app.pathwise.web.catalog.CatalogService;
import app.pathwise.web.platform.Json;
import app.pathwise.web.platform.events.EventBus;
import app.pathwise.web.platform.view.TimeLabels;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Rooms: creation, joining (code, approval, invite links), people and co-teacher permissions, the curriculum
 * (units → items), announcements, and the student's view of a room. Every entry point resolves the caller's
 * membership and checks {@link RoomPolicy}; a room you have no membership in is reported as not found.
 */
public final class RoomService {
    public static final String EVENT_MEMBER_JOINED = "room.member_joined";
    public static final String EVENT_JOIN_REQUESTED = "room.join_requested";
    public static final Duration INVITE_LIFETIME_MAX = Duration.ofDays(30);

    private final Tx tx;
    private final CatalogService catalog;
    private final EventBus events;
    private final Clock clock;
    private final RoomDao dao = new RoomDao();
    private final ContentDao content = new ContentDao();
    private final ProgressDao progress = new ProgressDao();
    private final AssessmentDao assessments = new AssessmentDao();
    private final GameDao games = new GameDao();
    private final app.pathwise.data.rooms.AssignmentDao assignmentDao = new app.pathwise.data.rooms.AssignmentDao();

    public RoomService(Tx tx, CatalogService catalog, EventBus events, Clock clock) {
        this.tx = tx;
        this.catalog = catalog;
        this.events = events;
        this.clock = clock;
    }

    /** The room doesn't exist, or the caller has no membership in it. */
    public static final class RoomNotFoundException extends RuntimeException {
        public RoomNotFoundException() {
            super("Room not found");
        }
    }

    /** A request that breaks a room rule; the message is shown to the user. */
    public static final class RoomRuleException extends RuntimeException {
        public RoomRuleException(String message) {
            super(message);
        }
    }

    /** The caller's standing in a room, resolved once per request. */
    public record Ctx(RoomRow room, RoomPolicy.Membership membership, UUID userId) {
        public boolean can(Action a) {
            return RoomPolicy.can(membership, new RoomPolicy.RoomState(room.archivedAt() != null, room.studentsSeeClassStats()), a);
        }

        public boolean isOwner() {
            return membership != null && membership.active() && membership.role() == RoomPolicy.Role.OWNER;
        }
    }

    // ------------------------------------------------------------------ access

    Ctx ctx(Connection c, Actor actor, UUID roomId) throws SQLException {
        requireUser(actor);
        RoomRow room = dao.find(c, roomId).orElseThrow(RoomNotFoundException::new);
        Optional<MemberRow> m = dao.membership(c, roomId, actor.userId());
        if (m.isEmpty() || !"active".equals(m.get().status())) {
            throw new RoomNotFoundException();
        }
        return new Ctx(room, toMembership(m.get()), actor.userId());
    }

    Ctx require(Connection c, Actor actor, UUID roomId, Action action) throws SQLException {
        Ctx ctx = ctx(c, actor, roomId);
        if (!ctx.can(action)) {
            throw new AccessDeniedException("room:" + action.name());
        }
        return ctx;
    }

    /** For other room services: resolve and check in one read. */
    public Ctx check(Actor actor, UUID roomId, Action action) {
        return tx.read(c -> require(c, actor, roomId, action));
    }

    static RoomPolicy.Membership toMembership(MemberRow m) {
        RoomPolicy.Role role = RoomPolicy.Role.fromDb(m.role());
        return new RoomPolicy.Membership(role, "active".equals(m.status()), role == RoomPolicy.Role.CO_TEACHER ? perms(m.permsJson()) : null);
    }

    static RoomPolicy.Perms perms(String json) {
        try {
            JsonNode n = Json.mapper().readTree(json == null ? "{}" : json);
            return new RoomPolicy.Perms(n.path("author").asBoolean(), n.path("publish").asBoolean(), n.path("grade").asBoolean(),
                    n.path("moderate").asBoolean(), n.path("analytics").asBoolean());
        } catch (IOException e) {
            return RoomPolicy.Perms.NONE;
        }
    }

    static String permsJson(RoomPolicy.Perms p) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        m.put("author", p.author());
        m.put("publish", p.publish());
        m.put("grade", p.grade());
        m.put("moderate", p.moderate());
        m.put("analytics", p.analytics());
        return Json.write(m);
    }

    // ------------------------------------------------------------------ rooms

    public record RoomCard(UUID id, String name, String codeLabel, String role, String roleLabel, String status, int students,
                           boolean archived) {}

    public record MyRooms(List<RoomCard> teaching, List<RoomCard> learning, List<RoomCard> pending) {}

    public MyRooms myRooms(Actor actor) {
        requireUser(actor);
        return tx.read(c -> {
            List<RoomCard> teaching = new ArrayList<>();
            List<RoomCard> learning = new ArrayList<>();
            List<RoomCard> pending = new ArrayList<>();
            for (RoomDao.MyRoom r : dao.roomsOf(c, actor.userId())) {
                RoomCard card = new RoomCard(r.id(), r.name(), RoomCodes.format(r.code()), r.role(), roleLabel(r.role()), r.status(),
                        r.students(), r.archivedAt() != null);
                if ("pending".equals(r.status())) {
                    pending.add(card);
                } else if ("student".equals(r.role())) {
                    learning.add(card);
                } else {
                    teaching.add(card);
                }
            }
            return new MyRooms(teaching, learning, pending);
        });
    }

    public UUID create(Actor actor, String name, String description, String subjectKey, String joinPolicy) {
        PlatformPolicy.require(actor, PlatformAction.CREATE_ROOM);
        String n = clean(name, 2, 120, "Give the room a name of 2 to 120 characters.");
        String d = description == null || description.isBlank() ? null : description.strip();
        if (d != null && d.length() > 2000) {
            throw new RoomRuleException("The description can be at most 2000 characters.");
        }
        String policy = joinPolicy(joinPolicy);
        UUID subject = subjectId(subjectKey);
        Instant now = clock.instant();
        return tx.call(c -> {
            String code = freshCode(c);
            UUID id = dao.create(c, n, d, subject, code, policy, now);
            dao.putMembership(c, id, actor.userId(), "owner", "active", "{}", now, null, now);
            events.publish(c, "room.created", Map.of("roomId", id.toString(), "ownerId", actor.userId().toString()));
            return id;
        });
    }

    private String freshCode(Connection c) throws SQLException {
        for (int i = 0; i < 20; i++) {
            String code = RoomCodes.generate();
            if (!dao.codeExists(c, code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not find a free room code");
    }

    private UUID subjectId(String subjectKey) {
        if (subjectKey == null || subjectKey.isBlank()) {
            return null;
        }
        return catalog.subjects().stream().filter(s -> s.key().equals(subjectKey)).map(s -> s.id()).findFirst()
                .orElseThrow(() -> new RoomRuleException("That subject isn't available."));
    }

    static String joinPolicy(String v) {
        return switch (v == null ? "" : v) {
            case "open", "approval", "invite_only" -> v;
            default -> "approval";
        };
    }

    public record Settings(UUID id, String name, String description, String subjectKey, String joinPolicy, boolean classStats,
                           String codeLabel, boolean archived, List<Map<String, String>> subjects) {}

    public Settings settings(Actor actor, UUID roomId) {
        return tx.read(c -> {
            Ctx ctx = require(c, actor, roomId, Action.EDIT_SETTINGS);
            RoomRow r = ctx.room();
            String subjectKey = catalog.subjects().stream().filter(s -> s.id().equals(r.subjectId())).map(s -> s.key()).findFirst().orElse("");
            List<Map<String, String>> subjects = catalog.subjects().stream().map(s -> Map.of("key", s.key(), "title", s.title())).toList();
            return new Settings(r.id(), r.name(), r.description(), subjectKey, r.joinPolicy(), r.studentsSeeClassStats(),
                    RoomCodes.format(r.code()), r.archivedAt() != null, subjects);
        });
    }

    public void updateSettings(Actor actor, UUID roomId, String name, String description, String subjectKey, String joinPolicy,
                               boolean classStats) {
        String n = clean(name, 2, 120, "Give the room a name of 2 to 120 characters.");
        String d = description == null || description.isBlank() ? null : description.strip();
        UUID subject = subjectId(subjectKey);
        tx.run(c -> {
            require(c, actor, roomId, Action.ARCHIVE); // owner, room not archived
            dao.updateSettings(c, roomId, n, d, subject, joinPolicy(joinPolicy), classStats, clock.instant());
        });
    }

    public void newCode(Actor actor, UUID roomId) {
        tx.run(c -> {
            require(c, actor, roomId, Action.ARCHIVE);
            dao.setCode(c, roomId, freshCode(c));
        });
    }

    public void setArchived(Actor actor, UUID roomId, boolean archived) {
        tx.run(c -> {
            require(c, actor, roomId, Action.EDIT_SETTINGS);
            dao.setArchived(c, roomId, archived ? clock.instant() : null);
        });
    }

    // ------------------------------------------------------------------ joining

    public record JoinOutcome(UUID roomId, String roomName, String status) {}

    /** Join by code. Open rooms admit at once; approval rooms create a request; invite-only rooms refuse. */
    public JoinOutcome joinByCode(Actor actor, String typedCode) {
        requireUser(actor);
        String code = RoomCodes.normalise(typedCode);
        if (code == null) {
            throw new RoomRuleException("Room codes have 8 letters and digits, like K7M3-QX9P.");
        }
        Instant now = clock.instant();
        return tx.call(c -> {
            RoomRow room = dao.findByCode(c, code).orElseThrow(() -> new RoomRuleException("No room has that code. Check it with your teacher."));
            if (room.archivedAt() != null) {
                throw new RoomRuleException("That room has been archived, so it isn't taking new members.");
            }
            Optional<MemberRow> existing = dao.membership(c, room.id(), actor.userId());
            if (existing.isPresent() && !"removed".equals(existing.get().status())) {
                return new JoinOutcome(room.id(), room.name(), existing.get().status());
            }
            if (existing.isPresent()) {
                throw new RoomRuleException("You were removed from this room. Ask the teacher for an invite link to rejoin.");
            }
            return switch (room.joinPolicy()) {
                case "open" -> {
                    dao.putMembership(c, room.id(), actor.userId(), "student", "active", "{}", now, null, now);
                    events.publish(c, EVENT_MEMBER_JOINED, Map.of("roomId", room.id().toString(), "userId", actor.userId().toString()));
                    yield new JoinOutcome(room.id(), room.name(), "active");
                }
                case "approval" -> {
                    dao.putMembership(c, room.id(), actor.userId(), "student", "pending", "{}", null, null, now);
                    events.publish(c, EVENT_JOIN_REQUESTED, Map.of("roomId", room.id().toString(), "userId", actor.userId().toString()));
                    yield new JoinOutcome(room.id(), room.name(), "pending");
                }
                default -> throw new RoomRuleException("This room only takes members through an invite link from the teacher.");
            };
        });
    }

    /** Creates an invite link; returns the raw token (only its hash is stored, so it is shown once). */
    public String createInvite(Actor actor, UUID roomId, String role, int maxUses, int days) {
        if (!"student".equals(role) && !"co_teacher".equals(role)) {
            throw new RoomRuleException("Invites are for students or co-teachers.");
        }
        int uses = Math.max(1, Math.min(maxUses, 1000));
        long d = Math.max(1, Math.min(days, INVITE_LIFETIME_MAX.toDays()));
        Instant now = clock.instant();
        return tx.call(c -> {
            require(c, actor, roomId, "co_teacher".equals(role) ? Action.MANAGE_CO_TEACHERS : Action.MANAGE_STUDENTS);
            String token = Tokens.newToken();
            dao.createInvite(c, roomId, Tokens.sha256Hex(token), role, uses, now.plus(Duration.ofDays(d)), actor.userId(), now);
            return token;
        });
    }

    public void revokeInvite(Actor actor, UUID roomId, UUID inviteId) {
        tx.run(c -> {
            require(c, actor, roomId, Action.MANAGE_STUDENTS);
            dao.revokeInvite(c, roomId, inviteId, clock.instant());
        });
    }

    public record InvitePreview(UUID roomId, String roomName, String role, String roleLabel, boolean valid, String problem) {}

    public InvitePreview previewInvite(String token) {
        return tx.read(c -> {
            Optional<RoomDao.InviteRow> inv = token == null || token.length() > 200 ? Optional.empty()
                    : dao.inviteByHash(c, Tokens.sha256Hex(token));
            if (inv.isEmpty()) {
                return new InvitePreview(null, null, null, null, false, "This invite link isn't valid. Ask your teacher for a new one.");
            }
            RoomRow room = dao.find(c, inv.get().roomId()).orElseThrow();
            String problem = inviteProblem(inv.get(), room, clock.instant());
            return new InvitePreview(room.id(), room.name(), inv.get().role(), roleLabel(inv.get().role()), problem == null, problem);
        });
    }

    private static String inviteProblem(RoomDao.InviteRow inv, RoomRow room, Instant now) {
        if (inv.revokedAt() != null) {
            return "This invite link was turned off by the teacher.";
        }
        if (!inv.expiresAt().isAfter(now)) {
            return "This invite link has expired. Ask your teacher for a new one.";
        }
        if (inv.uses() >= inv.maxUses()) {
            return "This invite link has been used up. Ask your teacher for a new one.";
        }
        if (room.archivedAt() != null) {
            return "That room has been archived.";
        }
        return null;
    }

    /** Accepting a valid invite admits at once (no approval needed). Co-teacher invites need a teacher account. */
    public JoinOutcome acceptInvite(Actor actor, String token) {
        requireUser(actor);
        Instant now = clock.instant();
        return tx.call(c -> {
            RoomDao.InviteRow inv = (token == null || token.length() > 200 ? Optional.<RoomDao.InviteRow>empty()
                    : dao.inviteByHash(c, Tokens.sha256Hex(token))).orElseThrow(() -> new RoomRuleException("This invite link isn't valid."));
            RoomRow room = dao.find(c, inv.roomId()).orElseThrow();
            String problem = inviteProblem(inv, room, now);
            if (problem != null) {
                throw new RoomRuleException(problem);
            }
            Optional<MemberRow> existing = dao.membership(c, room.id(), actor.userId());
            if (existing.isPresent() && "active".equals(existing.get().status())) {
                return new JoinOutcome(room.id(), room.name(), "active"); // already in: don't use up the link
            }
            boolean coTeacher = "co_teacher".equals(inv.role());
            if (coTeacher && !actor.hasRole(PlatformRole.TEACHER) && !actor.hasRole(PlatformRole.ADMIN)) {
                throw new RoomRuleException("Co-teacher invites need a teacher account.");
            }
            if (!dao.useInvite(c, inv.id(), now)) {
                throw new RoomRuleException("This invite link has just been used up.");
            }
            dao.putMembership(c, room.id(), actor.userId(), inv.role(), "active",
                    coTeacher ? permsJson(RoomPolicy.Perms.DEFAULT_CO_TEACHER) : "{}", now, null, now);
            events.publish(c, EVENT_MEMBER_JOINED, Map.of("roomId", room.id().toString(), "userId", actor.userId().toString(),
                    "role", inv.role()));
            return new JoinOutcome(room.id(), room.name(), "active");
        });
    }

    // ------------------------------------------------------------------ people

    public record Member(UUID userId, String name, String email, String role, String roleLabel, String joinedLabel,
                         String lastActiveLabel, RoomPolicy.Perms perms) {}

    public record InviteView(UUID id, String role, String roleLabel, String usesLabel, String expiresLabel, boolean active) {}

    public record People(Header header, List<Member> teachers, List<Member> students, List<Member> pending, List<InviteView> invites) {}

    public People people(Actor actor, UUID roomId) {
        Instant now = clock.instant();
        return tx.read(c -> {
            Ctx ctx = require(c, actor, roomId, Action.USE_TEACHER_VIEW);
            List<Member> teachers = new ArrayList<>();
            List<Member> students = new ArrayList<>();
            List<Member> pending = new ArrayList<>();
            for (MemberView m : dao.members(c, roomId)) {
                Member v = new Member(m.userId(), m.displayName(), m.email(), m.role(), roleLabel(m.role()),
                        m.joinedAt() == null ? TimeLabels.relative(m.requestedAt(), now, TimeLabels.DEFAULT_ZONE)
                                : TimeLabels.date(m.joinedAt(), TimeLabels.DEFAULT_ZONE),
                        m.lastEvidenceAt() == null ? "No activity yet" : TimeLabels.relative(m.lastEvidenceAt(), now, TimeLabels.DEFAULT_ZONE),
                        "co_teacher".equals(m.role()) ? perms(m.permsJson()) : null);
                if ("pending".equals(m.status())) {
                    pending.add(v);
                } else if ("student".equals(m.role())) {
                    students.add(v);
                } else {
                    teachers.add(v);
                }
            }
            List<InviteView> invites = dao.invites(c, roomId).stream().map(i -> new InviteView(i.id(), i.role(), roleLabel(i.role()),
                    i.uses() + " of " + i.maxUses() + " used", TimeLabels.date(i.expiresAt(), TimeLabels.DEFAULT_ZONE),
                    i.revokedAt() == null && i.expiresAt().isAfter(now) && i.uses() < i.maxUses())).toList();
            return new People(header(c, ctx), teachers, students, pending, invites);
        });
    }

    public void approve(Actor actor, UUID roomId, UUID userId, boolean accept) {
        Instant now = clock.instant();
        tx.run(c -> {
            require(c, actor, roomId, Action.MANAGE_STUDENTS);
            MemberRow m = dao.membership(c, roomId, userId).filter(x -> "pending".equals(x.status()))
                    .orElseThrow(() -> new RoomRuleException("That request has already been handled."));
            if (accept) {
                dao.putMembership(c, roomId, userId, m.role(), "active", m.permsJson(), now, actor.userId(), now);
                events.publish(c, EVENT_MEMBER_JOINED, Map.of("roomId", roomId.toString(), "userId", userId.toString()));
            } else {
                dao.putMembership(c, roomId, userId, m.role(), "removed", m.permsJson(), null, actor.userId(), now);
            }
        });
    }

    /** Removes a student (teachers) or a co-teacher (owner only). Owners can't be removed here. */
    public void remove(Actor actor, UUID roomId, UUID userId) {
        Instant now = clock.instant();
        tx.run(c -> {
            MemberRow m = dao.membership(c, roomId, userId).orElseThrow(() -> new RoomRuleException("That person isn't in this room."));
            switch (m.role()) {
                case "student" -> require(c, actor, roomId, Action.MANAGE_STUDENTS);
                case "co_teacher" -> require(c, actor, roomId, Action.MANAGE_CO_TEACHERS);
                default -> throw new RoomRuleException("The owner can't be removed.");
            }
            dao.putMembership(c, roomId, userId, m.role(), "removed", m.permsJson(), null, actor.userId(), now);
        });
    }

    public void setPerms(Actor actor, UUID roomId, UUID userId, RoomPolicy.Perms perms) {
        Instant now = clock.instant();
        tx.run(c -> {
            require(c, actor, roomId, Action.MANAGE_CO_TEACHERS);
            MemberRow m = dao.membership(c, roomId, userId).filter(x -> "co_teacher".equals(x.role()) && "active".equals(x.status()))
                    .orElseThrow(() -> new RoomRuleException("That person isn't a co-teacher here."));
            dao.putMembership(c, roomId, userId, "co_teacher", "active", permsJson(perms), m.joinedAt(), actor.userId(), now);
        });
    }

    // ------------------------------------------------------------------ header (shared by teacher pages)

    public record Header(UUID id, String name, String codeLabel, String joinPolicy, String joinPolicyLabel, boolean archived,
                         String role, boolean canAuthor, boolean canPublish, boolean canGrade, boolean canManageStudents,
                         boolean canManageCoTeachers, boolean canEditSettings, boolean canAnalytics, boolean canAnnounce,
                         int students, int pending) {}

    Header header(Connection c, Ctx ctx) throws SQLException {
        List<MemberView> members = dao.members(c, ctx.room().id());
        int students = (int) members.stream().filter(m -> "student".equals(m.role()) && "active".equals(m.status())).count();
        int pending = (int) members.stream().filter(m -> "pending".equals(m.status())).count();
        RoomRow r = ctx.room();
        return new Header(r.id(), r.name(), RoomCodes.format(r.code()), r.joinPolicy(), joinPolicyLabel(r.joinPolicy()),
                r.archivedAt() != null, ctx.membership().role().dbValue(), ctx.can(Action.AUTHOR), ctx.can(Action.PUBLISH),
                ctx.can(Action.GRADE), ctx.can(Action.MANAGE_STUDENTS), ctx.can(Action.MANAGE_CO_TEACHERS), ctx.can(Action.EDIT_SETTINGS),
                ctx.can(Action.VIEW_INDIVIDUAL_ANALYTICS), ctx.can(Action.POST_ANNOUNCEMENT), students, pending);
    }

    public Header header(Actor actor, UUID roomId) {
        return tx.read(c -> header(c, require(c, actor, roomId, Action.USE_TEACHER_VIEW)));
    }

    // ------------------------------------------------------------------ curriculum

    public record ItemView(UUID id, String kind, String kindLabel, String title, String href, String publishLabel, boolean required,
                           boolean first, boolean last) {}

    public record UnitView(UUID id, String title, String publishLabel, String publishValue, boolean scheduled, List<ItemView> items,
                           boolean first, boolean last) {}

    public record Choice(String value, String label, String group) {}

    public record Curriculum(Header header, List<UnitView> units, List<Choice> choices) {}

    public Curriculum curriculum(Actor actor, UUID roomId) {
        Instant now = clock.instant();
        return tx.read(c -> {
            Ctx ctx = require(c, actor, roomId, Action.USE_TEACHER_VIEW);
            List<UnitRow> units = dao.units(c, roomId);
            Map<UUID, List<ItemRow>> byUnit = dao.items(c, roomId).stream().collect(Collectors.groupingBy(ItemRow::unitId, LinkedHashMap::new, Collectors.toList()));
            List<UnitView> out = new ArrayList<>();
            for (int u = 0; u < units.size(); u++) {
                UnitRow unit = units.get(u);
                List<ItemRow> items = byUnit.getOrDefault(unit.id(), List.of());
                List<ItemView> iv = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) {
                    ItemRow it = items.get(i);
                    iv.add(new ItemView(it.id(), it.kind(), kindLabel(it.kind()), it.title(), href(it, roomId), publishLabel(it.publishAt(), now),
                            it.required(), i == 0, i == items.size() - 1));
                }
                out.add(new UnitView(unit.id(), unit.title(), publishLabel(unit.publishAt(), now),
                        unit.publishAt() == null ? "" : LocalDate.ofInstant(unit.publishAt(), TimeLabels.DEFAULT_ZONE).toString(),
                        unit.publishAt() != null && unit.publishAt().isAfter(now), iv, u == 0, u == units.size() - 1));
            }
            return new Curriculum(header(c, ctx), out, choices(c, ctx.room()));
        });
    }

    /** What can be added: the room subject's modules and games, platform assessments, and this room's own assessments. */
    private List<Choice> choices(Connection c, RoomRow room) throws SQLException {
        List<Choice> out = new ArrayList<>();
        List<UUID> subjects = room.subjectId() == null ? catalog.subjects().stream().map(s -> s.id()).toList() : List.of(room.subjectId());
        for (UUID s : subjects) {
            for (ContentDao.ModuleRow m : content.publishedModulesOfSubject(c, s)) {
                out.add(new Choice("module:" + m.id(), m.title(), "Modules"));
            }
        }
        for (GameDao.GameRow g : games.published(c)) {
            if (room.subjectId() == null || room.subjectId().equals(g.subjectId())) {
                out.add(new Choice("game:" + g.id(), g.title(), "Games"));
            }
        }
        for (AssessmentDao.AssessmentRow a : assessments.published(c)) {
            if (room.subjectId() == null || room.subjectId().equals(a.subjectId())) {
                out.add(new Choice("assessment:" + a.id(), a.title(), "Platform assessments"));
            }
        }
        for (AssessmentDao.AssessmentRow a : assessments.ofRoom(c, room.id())) {
            out.add(new Choice("assessment:" + a.id(), a.title() + ("draft".equals(a.status()) ? " (draft)" : ""), "This room's assessments"));
        }
        for (app.pathwise.data.rooms.AssignmentDao.AssignmentRow a : assignmentDao.ofRoom(c, room.id())) {
            out.add(new Choice("assignment:" + a.id(), a.title() + ("draft".equals(a.status()) ? " (draft)" : ""), "This room's assignments"));
        }
        return out;
    }

    public void addUnit(Actor actor, UUID roomId, String title, LocalDate publishOn) {
        String t = clean(title, 1, 120, "Give the unit a title (up to 120 characters).");
        tx.run(c -> {
            require(c, actor, roomId, Action.AUTHOR);
            dao.addUnit(c, roomId, t, startOfDay(publishOn));
        });
    }

    public void updateUnit(Actor actor, UUID roomId, UUID unitId, String title, LocalDate publishOn) {
        String t = clean(title, 1, 120, "Give the unit a title (up to 120 characters).");
        tx.run(c -> {
            require(c, actor, roomId, publishOn == null ? Action.AUTHOR : Action.PUBLISH);
            if (!dao.updateUnit(c, roomId, unitId, t, startOfDay(publishOn))) {
                throw new RoomNotFoundException();
            }
        });
    }

    public void deleteUnit(Actor actor, UUID roomId, UUID unitId) {
        tx.run(c -> {
            require(c, actor, roomId, Action.AUTHOR);
            dao.deleteUnit(c, roomId, unitId);
        });
    }

    public void moveUnit(Actor actor, UUID roomId, UUID unitId, int delta) {
        tx.run(c -> {
            require(c, actor, roomId, Action.AUTHOR);
            List<UUID> order = new ArrayList<>(dao.units(c, roomId).stream().map(UnitRow::id).toList());
            move(order, unitId, delta);
            dao.reorderUnits(c, roomId, order);
        });
    }

    /** {@code choice} is {@code kind:uuid} from {@link Curriculum#choices()}; anything else is refused. */
    public void addItem(Actor actor, UUID roomId, UUID unitId, String choice, LocalDate publishOn, boolean required) {
        String[] parts = choice == null ? new String[0] : choice.split(":", 2);
        UUID ref = parts.length == 2 ? app.pathwise.core.ids.Ids.parseOrNull(parts[1]) : null;
        if (ref == null) {
            throw new RoomRuleException("Choose something to add.");
        }
        tx.run(c -> {
            Ctx ctx = require(c, actor, roomId, Action.AUTHOR);
            if (dao.units(c, roomId).stream().noneMatch(u -> u.id().equals(unitId))) {
                throw new RoomNotFoundException();
            }
            boolean allowed = choices(c, ctx.room()).stream().anyMatch(x -> x.value().equals(choice));
            if (!allowed) {
                throw new RoomRuleException("That can't be added to this room.");
            }
            dao.addItem(c, unitId, parts[0], ref, startOfDay(publishOn), required);
        });
    }

    public void deleteItem(Actor actor, UUID roomId, UUID itemId) {
        tx.run(c -> {
            require(c, actor, roomId, Action.AUTHOR);
            dao.deleteItem(c, roomId, itemId);
        });
    }

    public void moveItem(Actor actor, UUID roomId, UUID itemId, int delta) {
        tx.run(c -> {
            require(c, actor, roomId, Action.AUTHOR);
            List<ItemRow> all = dao.items(c, roomId);
            ItemRow item = all.stream().filter(i -> i.id().equals(itemId)).findFirst().orElseThrow(RoomNotFoundException::new);
            List<UUID> order = new ArrayList<>(all.stream().filter(i -> i.unitId().equals(item.unitId())).map(ItemRow::id).toList());
            move(order, itemId, delta);
            dao.reorderItems(c, item.unitId(), order);
        });
    }

    private static void move(List<UUID> order, UUID id, int delta) {
        int i = order.indexOf(id);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= order.size()) {
            return;
        }
        order.set(i, order.get(j));
        order.set(j, id);
    }

    // ------------------------------------------------------------------ announcements

    public record Announcement(UUID id, String author, String body, boolean pinned, String when) {}

    public void announce(Actor actor, UUID roomId, String body, boolean pinned) {
        String b = clean(body, 1, 4000, "Write an announcement (up to 4000 characters).");
        tx.run(c -> {
            require(c, actor, roomId, Action.POST_ANNOUNCEMENT);
            dao.addAnnouncement(c, roomId, actor.userId(), b, pinned, clock.instant());
            events.publish(c, "room.announcement_posted", Map.of("roomId", roomId.toString()));
        });
    }

    public void deleteAnnouncement(Actor actor, UUID roomId, UUID id) {
        tx.run(c -> {
            require(c, actor, roomId, Action.POST_ANNOUNCEMENT);
            dao.deleteAnnouncement(c, roomId, id, clock.instant());
        });
    }

    List<Announcement> announcements(Connection c, UUID roomId, int limit) throws SQLException {
        Instant now = clock.instant();
        return dao.announcements(c, roomId, limit).stream().map(a -> new Announcement(a.id(), a.authorName() == null ? "A teacher" : a.authorName(),
                a.body(), a.pinned(), TimeLabels.relative(a.createdAt(), now, TimeLabels.DEFAULT_ZONE))).toList();
    }

    // ------------------------------------------------------------------ the student's view

    public record StudentItem(String kind, String kindLabel, String title, String href, String status, String statusLabel,
                              boolean required) {}

    public record StudentUnit(String title, List<StudentItem> items) {}

    public record StudentRoom(UUID id, String name, String description, String teachers, boolean archived, boolean teacherView,
                              List<Announcement> announcements, List<StudentUnit> units, StudentItem next, int done, int total,
                              Integer classAveragePercent) {}

    /** A room as a member sees it. Teachers see the same page (with their own progress) plus a link to manage it. */
    public StudentRoom studentView(Actor actor, UUID roomId) {
        Instant now = clock.instant();
        return tx.read(c -> {
            Ctx ctx = require(c, actor, roomId, Action.VIEW_ROOM);
            Map<UUID, Boolean> status = statusOf(c, actor.userId());
            List<UnitRow> units = dao.units(c, roomId);
            Map<UUID, List<ItemRow>> byUnit = dao.items(c, roomId).stream().collect(Collectors.groupingBy(ItemRow::unitId));
            List<StudentUnit> out = new ArrayList<>();
            StudentItem next = null;
            int done = 0;
            int total = 0;
            for (UnitRow u : units) {
                if (u.publishAt() != null && u.publishAt().isAfter(now)) {
                    continue;
                }
                List<StudentItem> items = new ArrayList<>();
                for (ItemRow it : byUnit.getOrDefault(u.id(), List.of())) {
                    if (!visibleToStudents(c, it, now)) {
                        continue;
                    }
                    String st = itemStatus(c, it, actor.userId(), status);
                    StudentItem si = new StudentItem(it.kind(), kindLabel(it.kind()), it.title(), href(it, roomId), st, statusLabel(it.kind(), st), it.required());
                    items.add(si);
                    if (it.required()) {
                        total++;
                        if ("done".equals(st)) {
                            done++;
                        } else if (next == null) {
                            next = si;
                        }
                    }
                }
                if (!items.isEmpty()) {
                    out.add(new StudentUnit(u.title(), items));
                }
            }
            String teachers = dao.members(c, roomId).stream().filter(m -> !"student".equals(m.role()) && "active".equals(m.status()))
                    .map(MemberView::displayName).collect(Collectors.joining(", "));
            Integer classAverage = ctx.can(Action.VIEW_CLASS_AGGREGATES) ? classAverage(c, roomId, now) : null;
            return new StudentRoom(roomId, ctx.room().name(), ctx.room().description(), teachers, ctx.room().archivedAt() != null,
                    ctx.can(Action.USE_TEACHER_VIEW), announcements(c, roomId, 10), out, next, done, total, classAverage);
        });
    }

    public record DueSoon(String title, String kindLabel, String href, String dueLabel, long daysLeft, String inLabel) {}

    /** {@code nextDue}: when the next item is itself due within a week, its \"In 2 days\" label (it isn't repeated in {@code dueSoon}). */
    public record RoomNext(UUID roomId, String roomName, StudentItem next, String nextDue, List<DueSoon> dueSoon) {}

    /**
     * Room context for the student's own next steps: in each room, the next unfinished required item and
     * anything due within a week that isn't done. Plain curriculum order and deadlines, nothing inferred.
     */
    public List<RoomNext> roomNextSteps(Actor actor) {
        requireUser(actor);
        Instant now = clock.instant();
        return tx.read(c -> {
            List<RoomNext> out = new ArrayList<>();
            Map<UUID, Boolean> modules = statusOf(c, actor.userId());
            for (RoomDao.MyRoom r : dao.roomsOf(c, actor.userId())) {
                if (!"student".equals(r.role()) || !"active".equals(r.status()) || r.archivedAt() != null) {
                    continue;
                }
                StudentItem next = null;
                String nextDue = null;
                List<DueSoon> due = new ArrayList<>();
                for (ItemRow it : requiredVisibleItems(c, r.id(), now)) {
                    String st = itemStatus(c, it, actor.userId(), modules);
                    if ("done".equals(st)) {
                        continue;
                    }
                    StudentItem si = new StudentItem(it.kind(), kindLabel(it.kind()), it.title(), href(it, r.id()), st, statusLabel(it.kind(), st), true);
                    if (next == null) {
                        next = si;
                    }
                    Instant dueAt = switch (it.kind()) {
                        case "assessment" -> assessments.findById(c, it.refId()).map(AssessmentDao.AssessmentRow::dueAt).orElse(null);
                        case "assignment" -> assignmentDao.find(c, it.refId()).map(app.pathwise.data.rooms.AssignmentDao.AssignmentRow::dueAt).orElse(null);
                        default -> null;
                    };
                    if (dueAt != null && dueAt.isAfter(now) && dueAt.isBefore(now.plus(Duration.ofDays(7)))) {
                        long days = Duration.between(now, dueAt).toDays();
                        String in = days == 0 ? "Due today" : days == 1 ? "Due tomorrow" : "In " + days + " days";
                        if (si == next) {
                            nextDue = in;
                        } else {
                            due.add(new DueSoon(it.title(), kindLabel(it.kind()), si.href(),
                                    TimeLabels.dateTime(dueAt, TimeLabels.DEFAULT_ZONE), days, in));
                        }
                    }
                }
                if (next != null || !due.isEmpty()) {
                    out.add(new RoomNext(r.id(), r.name(), next, nextDue, due));
                }
            }
            return out;
        });
    }

    /** Average share of required published items done, over active students; hidden below the minimum group size. */
    Integer classAverage(Connection c, UUID roomId, Instant now) throws SQLException {
        List<UUID> students = dao.activeStudents(c, roomId);
        if (students.size() < RoomPolicy.MIN_GROUP_FOR_AGGREGATES) {
            return null;
        }
        List<ItemRow> items = requiredVisibleItems(c, roomId, now);
        if (items.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (UUID s : students) {
            Map<UUID, Boolean> st = statusOf(c, s);
            long d = 0;
            for (ItemRow it : items) {
                if ("done".equals(itemStatus(c, it, s, st))) {
                    d++;
                }
            }
            sum += (double) d / items.size();
        }
        return (int) Math.round(100 * sum / students.size());
    }

    List<ItemRow> requiredVisibleItems(Connection c, UUID roomId, Instant now) throws SQLException {
        Map<UUID, UnitRow> units = dao.units(c, roomId).stream().collect(Collectors.toMap(UnitRow::id, u -> u));
        List<ItemRow> out = new ArrayList<>();
        for (ItemRow it : dao.items(c, roomId)) {
            UnitRow u = units.get(it.unitId());
            if (it.required() && (u.publishAt() == null || !u.publishAt().isAfter(now)) && visibleToStudents(c, it, now)) {
                out.add(it);
            }
        }
        return out;
    }

    private boolean visibleToStudents(Connection c, ItemRow it, Instant now) throws SQLException {
        if (it.publishAt() != null && it.publishAt().isAfter(now)) {
            return false;
        }
        if ("assessment".equals(it.kind())) {
            return assessments.findById(c, it.refId()).map(a -> "published".equals(a.status())).orElse(false);
        }
        if ("assignment".equals(it.kind())) {
            return assignmentDao.find(c, it.refId()).map(a -> "published".equals(a.status())).orElse(false);
        }
        return true;
    }

    /** Module completion for one student: id → completed? (absent = not started). */
    Map<UUID, Boolean> statusOf(Connection c, UUID userId) throws SQLException {
        Map<UUID, Boolean> out = new java.util.HashMap<>();
        progress.allProgress(c, userId).forEach((id, p) -> out.put(id, p.completed()));
        return out;
    }

    /** done, started or todo. */
    String itemStatus(Connection c, ItemRow it, UUID userId, Map<UUID, Boolean> modules) throws SQLException {
        return switch (it.kind()) {
            case "module" -> modules.containsKey(it.refId()) ? (modules.get(it.refId()) ? "done" : "started") : "todo";
            case "assessment" -> {
                var attempts = assessments.attempts(c, it.refId(), userId);
                yield attempts.stream().anyMatch(t -> "graded".equals(t.status())) ? "done"
                        : attempts.isEmpty() ? "todo" : "started";
            }
            case "assignment" -> {
                String st = assignmentDao.submission(c, it.refId(), userId).map(app.pathwise.data.rooms.AssignmentDao.SubmissionRow::status).orElse("none");
                yield switch (st) {
                    case "submitted", "graded" -> "done";
                    case "draft", "returned" -> "started";
                    default -> "todo";
                };
            }
            default -> {
                var s = games.stats(c, userId).get(it.refId());
                yield s != null && s.plays() > 0 ? "done" : "todo";
            }
        };
    }

    // ------------------------------------------------------------------ labels and helpers

    static String statusLabel(String kind, String status) {
        return switch (status) {
            case "done" -> switch (kind) {
                case "module" -> "Studied";
                case "assessment" -> "Submitted";
                case "assignment" -> "Handed in";
                default -> "Played";
            };
            case "started" -> "In progress";
            default -> "Not started";
        };
    }

    static String kindLabel(String kind) {
        return switch (kind) {
            case "module" -> "Module";
            case "assessment" -> "Assessment";
            case "assignment" -> "Assignment";
            default -> "Game";
        };
    }

    static String href(ItemRow it, UUID roomId) {
        return switch (it.kind()) {
            case "module" -> "/app/modules/" + it.refKey();
            case "assessment" -> "/app/assessments/" + it.refKey();
            case "assignment" -> "/app/rooms/" + roomId + "/assignments/" + it.refId();
            default -> "/app/games/" + it.refKey();
        };
    }

    static String roleLabel(String role) {
        return switch (role) {
            case "owner" -> "Owner";
            case "co_teacher" -> "Co-teacher";
            default -> "Student";
        };
    }

    static String joinPolicyLabel(String p) {
        return switch (p) {
            case "open" -> "Anyone with the code joins at once";
            case "invite_only" -> "Invite links only";
            default -> "Code, then teacher approval";
        };
    }

    private static String publishLabel(Instant at, Instant now) {
        if (at == null) {
            return null;
        }
        String d = TimeLabels.date(at, TimeLabels.DEFAULT_ZONE);
        return at.isAfter(now) ? "Opens " + d : "Opened " + d;
    }

    static Instant startOfDay(LocalDate d) {
        return d == null ? null : d.atStartOfDay(TimeLabels.DEFAULT_ZONE).toInstant();
    }

    private static String clean(String v, int min, int max, String message) {
        String s = v == null ? "" : v.strip();
        if (s.length() < min || s.length() > max) {
            throw new RoomRuleException(message);
        }
        return s;
    }

    private static void requireUser(Actor actor) {
        if (actor == null || !actor.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }
    }
}
