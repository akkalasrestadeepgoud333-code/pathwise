package app.pathwise.web.identity;

import app.pathwise.core.identity.PasswordHasher;
import app.pathwise.core.identity.PasswordPolicy;
import app.pathwise.core.identity.PlatformRole;
import app.pathwise.core.ids.Ids;
import app.pathwise.core.security.AccessDeniedException;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.Tokens;
import app.pathwise.data.DataAccessException;
import app.pathwise.data.Tx;
import app.pathwise.data.identity.LearnerProfileDao;
import app.pathwise.data.identity.SessionDao;
import app.pathwise.data.identity.SessionRecord;
import app.pathwise.data.identity.UserDao;
import app.pathwise.data.identity.UserRecord;
import app.pathwise.data.platform.AdminDao;
import app.pathwise.data.platform.AuditDao;
import app.pathwise.web.platform.Json;
import app.pathwise.web.platform.RequestMeta;
import app.pathwise.web.platform.events.EventBus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Accounts, credentials and sessions. The only service that sees password hashes or raw tokens. */
public final class IdentityService {
    public static final String EVENT_USER_REGISTERED = "identity.user_registered";
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);
    private static final Set<String> THEMES = Set.of("system", "light", "dark");

    private final Tx tx;
    private final PasswordHasher hasher;
    private final EventBus events;
    private final Clock clock;
    private final Duration webTtl;
    private final Duration bearerTtl;
    private final UserDao users = new UserDao();
    private final SessionDao sessions = new SessionDao();
    private final LearnerProfileDao profiles = new LearnerProfileDao();
    private final AuditDao audit = new AuditDao();
    private final AdminDao settings = new AdminDao();
    /** Verified against when the email is unknown, so response time does not reveal which emails exist. */
    private final String dummyHash;

    public IdentityService(Tx tx, PasswordHasher hasher, EventBus events, Clock clock, Duration webTtl, Duration bearerTtl) {
        this.tx = tx;
        this.hasher = hasher;
        this.events = events;
        this.clock = clock;
        this.webTtl = webTtl;
        this.bearerTtl = bearerTtl;
        this.dummyHash = hasher.hash("pathwise-timing-equaliser".toCharArray());
    }

    public enum Client {
        WEB("web"), ANDROID("android");
        final String db;
        Client(String db) { this.db = db; }
    }

    public record Registration(String email, String password, String displayName, boolean teacher) {}

    public record NewSession(UUID id, String token, Instant expiresAt, Actor actor) {}

    public record SessionView(UUID id, String client, String userAgent, Instant createdAt, Instant lastUsedAt, boolean current) {}

    /** A rejected registration, with the field it concerns. */
    public static class RegistrationException extends RuntimeException {
        private final String field;

        public RegistrationException(String field, String message) {
            super(message);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }

    /** Sign-ups are switched off by an administrator (shown on the form's email field). */
    public static final class RegistrationClosedException extends RegistrationException {
        public RegistrationClosedException() {
            super("email", "New sign-ups are closed on this Pathwise right now. Ask your administrator for an account.");
        }
    }

    /** Creates a student (and optionally teacher) account. Returns the new user id. */
    public UUID register(Registration r, RequestMeta meta) {
        return register(r, meta, true);
    }

    /** {@code respectClosedSignups} is false only for the administrator CLI. */
    private UUID register(Registration r, RequestMeta meta, boolean respectClosedSignups) {
        if (respectClosedSignups && "false".equals(tx.read(c -> settings.setting(c, "registration.open")).orElse("true").strip())) {
            throw new RegistrationClosedException();
        }
        String email = normaliseEmail(r.email());
        String name = r.displayName().trim().replaceAll("\\s+", " ");
        PasswordPolicy.check(r.password(), email).ifPresent(msg -> {
            throw new RegistrationException("password", msg);
        });
        String hash = hasher.hash(r.password().toCharArray());
        UUID id = Ids.newId();
        try {
            tx.run(c -> {
                if (users.emailInUse(c, email)) {
                    throw new RegistrationException("email", "An account with this email already exists. Try signing in.");
                }
                users.insert(c, id, email, hash, name);
                users.addRole(c, id, PlatformRole.STUDENT, null);
                if (r.teacher()) {
                    users.addRole(c, id, PlatformRole.TEACHER, null);
                }
                profiles.insertDefault(c, id);
                audit.record(c, id, "identity.register", "user", id.toString(),
                        Json.write(Map.of("teacher", r.teacher())), meta.ipHash());
                events.publish(c, EVENT_USER_REGISTERED, Map.of("userId", id.toString(), "teacher", r.teacher()));
            });
        } catch (DataAccessException e) {
            if (e.isUniqueViolation()) { // two concurrent sign-ups with the same email
                throw new RegistrationException("email", "An account with this email already exists. Try signing in.");
            }
            throw e;
        }
        return id;
    }

    /**
     * Checks credentials. Returns empty for unknown email, wrong password or a non-active account,
     * without revealing which. Upgrades the stored hash if the cost parameters were raised.
     */
    public Optional<UserRecord> authenticate(String email, String password, RequestMeta meta) {
        String normalised = normaliseEmail(email);
        Optional<UserRecord> user = tx.read(c -> users.findByEmail(c, normalised));
        char[] pw = password == null ? new char[0] : password.toCharArray();
        if (user.isEmpty()) {
            hasher.verify(pw, dummyHash);
            return Optional.empty();
        }
        UserRecord u = user.get();
        boolean ok = hasher.verify(pw, u.passwordHash());
        if (!ok || !u.isActive()) {
            tx.run(c -> audit.record(c, u.id(), "identity.login_failed", "user", u.id().toString(),
                    Json.write(Map.of("reason", ok ? "inactive" : "password")), meta.ipHash()));
            return Optional.empty();
        }
        if (hasher.needsRehash(u.passwordHash())) {
            String upgraded = hasher.hash(pw);
            tx.run(c -> users.updatePasswordHash(c, u.id(), upgraded));
        }
        return Optional.of(u);
    }

    /** Opens a session for an authenticated user. The raw token is returned once and never stored. */
    public NewSession startSession(UserRecord user, Client client, RequestMeta meta) {
        Instant now = clock.instant();
        Instant expires = now.plus(client == Client.WEB ? webTtl : bearerTtl);
        String token = Tokens.newToken();
        UUID id = Ids.newId();
        Set<PlatformRole> roles = tx.call(c -> {
            sessions.insert(c, id, user.id(), Tokens.sha256Hex(token), client.db, meta.userAgent(), meta.ipHash(), now, expires);
            users.touchLastSeen(c, user.id(), now);
            audit.record(c, user.id(), "identity.login", "session", id.toString(),
                    Json.write(Map.of("client", client.db)), meta.ipHash());
            return users.rolesOf(c, user.id());
        });
        return new NewSession(id, token, expires, new Actor(user.id(), user.displayName(), roles, id));
    }

    /** Resolves a web session (the id stored in the HttpSession). */
    public Optional<Actor> resolveSession(UUID sessionId) {
        return resolve(c -> sessions.findActiveById(c, sessionId, clock.instant()), webTtl);
    }

    /** Resolves a mobile/API bearer token. */
    public Optional<Actor> resolveBearer(String token) {
        if (token == null || token.length() < 20 || token.length() > 200) {
            return Optional.empty();
        }
        String hash = Tokens.sha256Hex(token);
        return resolve(c -> sessions.findActiveByTokenHash(c, hash, clock.instant()), bearerTtl);
    }

    private Optional<Actor> resolve(Tx.SqlFunction<Optional<SessionRecord>> finder, Duration ttl) {
        Instant now = clock.instant();
        return tx.call(c -> {
            Optional<SessionRecord> s = finder.apply(c);
            if (s.isEmpty()) {
                return Optional.<Actor>empty();
            }
            SessionRecord session = s.get();
            Optional<UserRecord> user = users.findById(c, session.userId());
            if (user.isEmpty() || !user.get().isActive()) {
                return Optional.<Actor>empty();
            }
            if (session.lastUsedAt().plus(TOUCH_INTERVAL).isBefore(now)) {
                sessions.touch(c, session.id(), now, now.plus(ttl));
                users.touchLastSeen(c, session.userId(), now);
            }
            return Optional.of(new Actor(session.userId(), user.get().displayName(), users.rolesOf(c, session.userId()), session.id()));
        });
    }

    public void endSession(Actor actor, RequestMeta meta) {
        if (!actor.isAuthenticated() || actor.sessionId() == null) {
            return;
        }
        tx.run(c -> {
            sessions.revoke(c, actor.sessionId(), actor.userId(), clock.instant());
            audit.record(c, actor.userId(), "identity.logout", "session", actor.sessionId().toString(), null, meta.ipHash());
        });
    }

    public List<SessionView> listSessions(Actor actor) {
        requireUser(actor);
        return tx.read(c -> sessions.listActive(c, actor.userId(), clock.instant())).stream()
                .map(s -> new SessionView(s.id(), s.client(), describeAgent(s.userAgent()), s.createdAt(), s.lastUsedAt(),
                        s.id().equals(actor.sessionId())))
                .toList();
    }

    /** Revokes one of the actor's own sessions. Returns false if it was not theirs or already ended. */
    public boolean revokeSession(Actor actor, UUID sessionId, RequestMeta meta) {
        requireUser(actor);
        return tx.call(c -> {
            boolean revoked = sessions.revoke(c, sessionId, actor.userId(), clock.instant());
            if (revoked) {
                audit.record(c, actor.userId(), "identity.session_revoked", "session", sessionId.toString(), null, meta.ipHash());
            }
            return revoked;
        });
    }

    public int revokeOtherSessions(Actor actor, RequestMeta meta) {
        requireUser(actor);
        return tx.call(c -> {
            int n = sessions.revokeAllExcept(c, actor.userId(), actor.sessionId(), clock.instant());
            audit.record(c, actor.userId(), "identity.sessions_revoked_others", "user", actor.userId().toString(),
                    Json.write(Map.of("count", n)), meta.ipHash());
            return n;
        });
    }

    /** Confirms the password of a signed-in person for sensitive actions. Returns false if it was wrong. */
    public boolean reauthenticate(Actor actor, String password, RequestMeta meta) {
        requireUser(actor);
        UserRecord u = tx.read(c -> users.findById(c, actor.userId())).orElseThrow(() -> new AccessDeniedException("account"));
        boolean ok = hasher.verify(password == null ? new char[0] : password.toCharArray(), u.passwordHash());
        tx.run(c -> {
            if (ok && actor.sessionId() != null) {
                sessions.markReauth(c, actor.sessionId(), actor.userId(), clock.instant());
            }
            audit.record(c, actor.userId(), ok ? "identity.reauth" : "identity.reauth_failed", "session",
                    String.valueOf(actor.sessionId()), null, meta.ipHash());
        });
        return ok;
    }

    /** Ends every session of an account (after a suspension). Returns how many were open. */
    public int endAllSessions(UUID userId) {
        return tx.call(c -> sessions.revokeAll(c, userId, clock.instant()));
    }

    /** True if this session re-entered its password within {@code window}. */
    public boolean recentlyReauthenticated(Actor actor, Duration window) {
        if (actor == null || actor.sessionId() == null) {
            return false;
        }
        Instant cutoff = clock.instant().minus(window);
        return tx.read(c -> sessions.reauthAt(c, actor.sessionId())).filter(t -> t.isAfter(cutoff)).isPresent();
    }

    public Optional<UserRecord> findUser(UUID id) {
        return tx.read(c -> users.findById(c, id));
    }

    public String theme(UUID userId) {
        return tx.read(c -> profiles.theme(c, userId)).orElse("system");
    }

    public void setTheme(Actor actor, String theme) {
        requireUser(actor);
        String t = theme == null ? "" : theme.toLowerCase(Locale.ROOT);
        if (!THEMES.contains(t)) {
            throw new IllegalArgumentException("Unknown theme");
        }
        tx.run(c -> profiles.updateTheme(c, actor.userId(), t));
    }

    /**
     * Creates the first (or an additional) administrator from the command line. If the email already
     * belongs to an account, that account is granted the admin role instead.
     */
    public UUID createOrPromoteAdmin(String email, String displayName, String password) {
        String normalised = normaliseEmail(email);
        Optional<UserRecord> existing = tx.read(c -> users.findByEmail(c, normalised));
        if (existing.isPresent()) {
            UUID id = existing.get().id();
            tx.run(c -> {
                users.addRole(c, id, PlatformRole.ADMIN, null);
                audit.record(c, null, "identity.admin_granted", "user", id.toString(), Json.write(Map.of("via", "cli")), null);
            });
            return id;
        }
        UUID id = register(new Registration(normalised, password, displayName, false), RequestMeta.system(), false);
        tx.run(c -> {
            users.addRole(c, id, PlatformRole.ADMIN, null);
            audit.record(c, null, "identity.admin_granted", "user", id.toString(), Json.write(Map.of("via", "cli")), null);
        });
        return id;
    }

    public Set<PlatformRole> rolesOf(UUID userId) {
        return tx.read(c -> {
            Set<PlatformRole> r = users.rolesOf(c, userId);
            return r.isEmpty() ? EnumSet.noneOf(PlatformRole.class) : r;
        });
    }

    private static void requireUser(Actor actor) {
        if (actor == null || !actor.isAuthenticated()) {
            throw new AccessDeniedException("account");
        }
    }

    static String normaliseEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** A short human description such as "Edge on Windows" — never shown to anyone but the owner. */
    static String describeAgent(String ua) {
        if (ua == null || ua.isBlank()) {
            return "Unknown device";
        }
        if (ua.startsWith("pathwise-android")) {
            return "Pathwise Android app";
        }
        String browser = ua.contains("Edg/") ? "Edge" : ua.contains("OPR/") ? "Opera" : ua.contains("Chrome/") ? "Chrome"
                : ua.contains("Firefox/") ? "Firefox" : ua.contains("Safari/") ? "Safari" : "Browser";
        String os = ua.contains("Windows") ? "Windows" : ua.contains("Android") ? "Android" : ua.contains("iPhone") ? "iPhone"
                : ua.contains("Mac OS") ? "macOS" : ua.contains("Linux") ? "Linux" : null;
        return os == null ? browser : browser + " on " + os;
    }
}
