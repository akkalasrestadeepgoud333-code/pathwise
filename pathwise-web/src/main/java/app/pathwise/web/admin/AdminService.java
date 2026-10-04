package app.pathwise.web.admin;

import app.pathwise.core.identity.PlatformRole;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.core.security.PlatformPolicy;
import app.pathwise.data.Tx;
import app.pathwise.data.platform.AdminDao;
import app.pathwise.data.platform.AuditDao;
import app.pathwise.data.platform.PlatformStatsDao;
import app.pathwise.data.rooms.RoomDao;
import app.pathwise.web.identity.IdentityService;
import app.pathwise.web.platform.Json;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The admin console. Every method checks the platform policy itself. Role changes and suspensions also need
 * the admin to have re-entered their password in the last 15 minutes, and every change is audited.
 */
public final class AdminService {
    public static final Duration REAUTH_WINDOW = Duration.ofMinutes(15);
    public static final String REGISTRATION_OPEN = "registration.open";
    private static final Set<String> GRANTABLE = Set.of("teacher", "mentor", "admin");

    private final Tx tx;
    private final IdentityService identity;
    private final Clock clock;
    private final PlatformStatsDao stats = new PlatformStatsDao();
    private final AdminDao dao = new AdminDao();
    private final RoomDao rooms = new RoomDao();
    private final AuditDao audit = new AuditDao();

    public AdminService(Tx tx, IdentityService identity, Clock clock) {
        this.tx = tx;
        this.identity = identity;
        this.clock = clock;
    }

    /** A refused admin action, with a message for the admin. */
    public static final class AdminRuleException extends RuntimeException {
        public AdminRuleException(String message) {
            super(message);
        }
    }

    /** The action needs the admin's password again. */
    public static final class ReauthRequiredException extends RuntimeException {
        public ReauthRequiredException() {
            super("Please confirm your password to continue.");
        }
    }

    public PlatformStatsDao.Overview overview(Actor actor) {
        PlatformPolicy.require(actor, PlatformAction.USE_ADMIN_AREA);
        return tx.read(stats::overview);
    }

    public List<PlatformStatsDao.AuditEntry> recentAudit(Actor actor, int limit) {
        PlatformPolicy.require(actor, PlatformAction.VIEW_AUDIT_LOG);
        return tx.read(c -> stats.recentAudit(c, Math.max(1, Math.min(limit, 100))));
    }

    // ---- users ---------------------------------------------------------------------------------

    public List<AdminDao.UserRow> users(Actor actor, String query) {
        PlatformPolicy.require(actor, PlatformAction.MANAGE_USERS);
        return tx.read(c -> dao.searchUsers(c, query, 50));
    }

    public Optional<AdminDao.UserRow> user(Actor actor, UUID id) {
        PlatformPolicy.require(actor, PlatformAction.MANAGE_USERS);
        return tx.read(c -> dao.user(c, id));
    }

    public boolean reauthenticated(Actor actor) {
        return identity.recentlyReauthenticated(actor, REAUTH_WINDOW);
    }

    private void requireReauth(Actor actor) {
        if (!reauthenticated(actor)) {
            throw new ReauthRequiredException();
        }
    }

    private static String reason(String reason) {
        String r = reason == null ? "" : reason.strip();
        if (r.length() < 5 || r.length() > 300) {
            throw new AdminRuleException("Give a reason (5 to 300 characters). It is kept in the audit log.");
        }
        return r;
    }

    /** Suspending signs the person out everywhere; they can't sign in until reactivated. */
    public void setSuspended(Actor actor, UUID userId, boolean suspend, String reason) {
        PlatformPolicy.require(actor, PlatformAction.MANAGE_USERS);
        requireReauth(actor);
        String why = reason(reason);
        if (userId.equals(actor.userId())) {
            throw new AdminRuleException("You can't suspend your own account.");
        }
        tx.run(c -> {
            AdminDao.UserRow u = dao.user(c, userId).orElseThrow(() -> new AdminRuleException("No such account."));
            if (suspend && u.roles().contains("admin")) {
                throw new AdminRuleException("Remove the admin role before suspending an administrator.");
            }
            if (!dao.setStatus(c, userId, suspend ? "suspended" : "active")) {
                throw new AdminRuleException(suspend ? "That account is already suspended." : "That account is already active.");
            }
            audit.record(c, actor.userId(), suspend ? "admin.user_suspended" : "admin.user_reactivated", "user", userId.toString(),
                    Json.write(Map.of("reason", why)), null);
        });
        if (suspend) {
            // Sign-in and every request already refuse suspended accounts; this also ends their open sessions.
            identity.endAllSessions(userId);
        }
    }

    public void setRole(Actor actor, UUID userId, String role, boolean grant) {
        PlatformPolicy.require(actor, PlatformAction.GRANT_ROLES);
        requireReauth(actor);
        String r = role == null ? "" : role.toLowerCase(Locale.ROOT);
        if (!GRANTABLE.contains(r)) {
            throw new AdminRuleException("Only the teacher, mentor and admin roles can be changed here.");
        }
        boolean adminRole = PlatformRole.ADMIN.dbValue().equals(r);
        tx.run(c -> {
            AdminDao.UserRow u = dao.user(c, userId).orElseThrow(() -> new AdminRuleException("No such account."));
            if (grant) {
                if (adminRole && !"active".equals(u.status())) {
                    throw new AdminRuleException("Reactivate the account before making it an administrator.");
                }
                dao.grantRole(c, userId, r, actor.userId());
            } else {
                if (adminRole) {
                    if (userId.equals(actor.userId())) {
                        throw new AdminRuleException("You can't remove your own admin role. Ask another administrator.");
                    }
                    if (dao.countWithRole(c, r) <= 1) {
                        throw new AdminRuleException("Pathwise needs at least one administrator.");
                    }
                }
                dao.removeRole(c, userId, r);
            }
            audit.record(c, actor.userId(), grant ? "admin.role_granted" : "admin.role_revoked", "user", userId.toString(),
                    Json.write(Map.of("role", r)), null);
        });
    }

    /**
     * Removes an account for good: signed out everywhere, can't sign in, name and email wiped. Needs password
     * re-entry and a reason. Administrators must lose the admin role first; teachers must archive their rooms first.
     */
    public void removeUser(Actor actor, UUID userId, String reason) {
        PlatformPolicy.require(actor, PlatformAction.MANAGE_USERS);
        requireReauth(actor);
        String why = reason(reason);
        if (userId.equals(actor.userId())) {
            throw new AdminRuleException("You can't remove your own account here.");
        }
        tx.run(c -> {
            AdminDao.UserRow u = dao.user(c, userId).orElseThrow(() -> new AdminRuleException("No such account."));
            if (u.roles().contains("admin")) {
                throw new AdminRuleException("Remove the admin role before removing an administrator.");
            }
            int rooms = dao.activeOwnedRooms(c, userId);
            if (rooms > 0) {
                throw new AdminRuleException("This person owns " + rooms + " active room" + (rooms == 1 ? "" : "s")
                        + ". Archive them first (Admin → Rooms), so no class is left without a teacher.");
            }
            if (!dao.removeUser(c, userId)) {
                throw new AdminRuleException("That account was already removed.");
            }
            audit.record(c, actor.userId(), "admin.user_removed", "user", userId.toString(), Json.write(Map.of("reason", why)), null);
        });
        identity.endAllSessions(userId);
    }

    /**
     * Sends an in-app message (it appears under the bell). {@code userId} is used only for {@link AdminDao.Audience#USER}.
     * Returns how many people received it.
     */
    public int sendMessage(Actor actor, AdminDao.Audience audience, UUID userId, String title, String body) {
        PlatformPolicy.require(actor, PlatformAction.MANAGE_USERS);
        String t = title == null ? "" : title.strip().replaceAll("\\s+", " ");
        String b = body == null ? "" : body.strip();
        if (t.length() < 3 || t.length() > 120) {
            throw new AdminRuleException("Give the message a subject (3 to 120 characters).");
        }
        if (b.isEmpty() || b.length() > 450) {
            throw new AdminRuleException("Write the message (up to 450 characters).");
        }
        if (audience == AdminDao.Audience.USER && userId == null) {
            throw new AdminRuleException("Choose who to send it to.");
        }
        String signed = b + "\n— " + actor.displayName() + ", administrator";
        return tx.call(c -> {
            int n = dao.sendMessage(c, audience, userId, t, signed);
            audit.record(c, actor.userId(), "admin.message_sent", audience == AdminDao.Audience.USER ? "user" : "audience",
                    audience == AdminDao.Audience.USER ? userId.toString() : audience.name().toLowerCase(Locale.ROOT),
                    Json.write(Map.of("subject", t, "recipients", n)), null);
            return n;
        });
    }

    public void setVerifiedEducator(Actor actor, UUID userId, boolean verified) {
        PlatformPolicy.require(actor, PlatformAction.MANAGE_USERS);
        tx.run(c -> {
            dao.user(c, userId).orElseThrow(() -> new AdminRuleException("No such account."));
            dao.setVerifiedEducator(c, userId, verified);
            audit.record(c, actor.userId(), "admin.verified_educator", "user", userId.toString(), Json.write(Map.of("verified", verified)), null);
        });
    }

    public void setTrustLevel(Actor actor, UUID userId, int level) {
        PlatformPolicy.require(actor, PlatformAction.MANAGE_USERS);
        if (level < 0 || level > 4) {
            throw new AdminRuleException("Trust levels go from 0 to 4.");
        }
        tx.run(c -> {
            dao.user(c, userId).orElseThrow(() -> new AdminRuleException("No such account."));
            dao.setTrustLevel(c, userId, level);
            audit.record(c, actor.userId(), "admin.trust_level", "user", userId.toString(), Json.write(Map.of("level", level)), null);
        });
    }

    // ---- settings ------------------------------------------------------------------------------

    /** Public: the sign-up form reads it. Missing or unreadable means open. */
    public boolean registrationOpen() {
        return !"false".equals(tx.read(c -> dao.setting(c, REGISTRATION_OPEN)).orElse("true").strip());
    }

    public void setRegistrationOpen(Actor actor, boolean open) {
        PlatformPolicy.require(actor, PlatformAction.MANAGE_PLATFORM);
        tx.run(c -> {
            dao.putSetting(c, REGISTRATION_OPEN, open ? "true" : "false", actor.userId());
            audit.record(c, actor.userId(), "admin.setting_changed", "setting", REGISTRATION_OPEN, Json.write(Map.of("value", open)), null);
        });
    }

    // ---- games ---------------------------------------------------------------------------------

    public List<AdminDao.TemplateRow> templates(Actor actor) {
        PlatformPolicy.require(actor, PlatformAction.MANAGE_PLATFORM);
        return tx.read(dao::templates);
    }

    public void setTemplateEnabled(Actor actor, String key, boolean enabled) {
        PlatformPolicy.require(actor, PlatformAction.MANAGE_PLATFORM);
        tx.run(c -> {
            if (!dao.setTemplateEnabled(c, key, enabled)) {
                throw new AdminRuleException("No such game template.");
            }
            audit.record(c, actor.userId(), "admin.game_template", "game_template", key, Json.write(Map.of("enabled", enabled)), null);
        });
    }

    // ---- rooms ---------------------------------------------------------------------------------

    /** Names, codes and head counts only; admins never see a room's people or work from here. */
    public List<AdminDao.RoomRow> rooms(Actor actor, String query) {
        PlatformPolicy.require(actor, PlatformAction.MANAGE_PLATFORM);
        return tx.read(c -> dao.rooms(c, query, 100));
    }

    public void setRoomArchived(Actor actor, UUID roomId, boolean archived, String reason) {
        PlatformPolicy.require(actor, PlatformAction.MANAGE_PLATFORM);
        String why = reason(reason);
        tx.run(c -> {
            rooms.find(c, roomId).orElseThrow(() -> new AdminRuleException("No such room."));
            rooms.setArchived(c, roomId, archived ? clock.instant() : null);
            audit.record(c, actor.userId(), archived ? "admin.room_archived" : "admin.room_restored", "room", roomId.toString(),
                    Json.write(Map.of("reason", why)), null);
        });
    }

    // ---- audit ---------------------------------------------------------------------------------

    public List<AdminDao.AuditRow> audit(Actor actor, String actionPrefix, Long beforeId) {
        PlatformPolicy.require(actor, PlatformAction.VIEW_AUDIT_LOG);
        return tx.read(c -> dao.audit(c, actionPrefix, beforeId, 50));
    }
}
