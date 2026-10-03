package app.pathwise.web.identity;

import app.pathwise.core.ids.Ids;
import app.pathwise.core.security.Actor;
import app.pathwise.data.identity.UserRecord;
import app.pathwise.web.platform.RequestMeta;
import app.pathwise.web.platform.http.ApiException;
import app.pathwise.web.platform.http.ApiServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /api/v1/auth/*} for the Android app and other API clients. Issues bearer tokens; web pages
 * use the cookie session from {@link AuthController} instead.
 */
public class AuthApiServlet extends ApiServlet {

    public record TokenResponse(String token, Instant expiresAt, MeApiServlet.Me user) {}

    public record SessionDto(UUID id, String client, String device, Instant createdAt, Instant lastUsedAt, boolean current) {}

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        switch (subPath(req)) {
            case "/register" -> register(req, res);
            case "/login" -> login(req, res);
            case "/logout" -> {
                Actor actor = requireActor(req);
                app().identity().endSession(actor, meta(req));
                res.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
            case "/sessions/revoke-others" -> {
                Actor actor = requireActor(req);
                int n = app().identity().revokeOtherSessions(actor, meta(req));
                writeJson(res, 200, Map.of("revoked", n));
            }
            default -> notFound();
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if ("/sessions".equals(subPath(req))) {
            Actor actor = requireActor(req);
            List<SessionDto> list = app().identity().listSessions(actor).stream()
                    .map(s -> new SessionDto(s.id(), s.client(), s.userAgent(), s.createdAt(), s.lastUsedAt(), s.current()))
                    .toList();
            writeJson(res, 200, Map.of("items", list));
            return;
        }
        notFound();
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String path = subPath(req);
        if (path.startsWith("/sessions/")) {
            Actor actor = requireActor(req);
            UUID id = Ids.parseOrNull(path.substring("/sessions/".length()));
            // 404 both for malformed ids and for sessions that belong to someone else (no enumeration).
            if (id == null || !app().identity().revokeSession(actor, id, meta(req))) {
                throw new ApiException(404, "SESSION_NOT_FOUND", "No such active session.");
            }
            res.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        notFound();
    }

    private void register(HttpServletRequest req, HttpServletResponse res) throws IOException {
        RegisterForm form = readBody(req, RegisterForm.class);
        RequestMeta meta = meta(req);
        if (!app().registerLimiter().tryAcquire("register:" + meta.rateKeyIp())) {
            throw new ApiException(429, "RATE_LIMITED", "Too many sign-ups from this network. Try again later.");
        }
        try {
            app().identity().register(new IdentityService.Registration(
                    form.getEmail(), form.getPassword(), form.getDisplayName(), form.isTeacher()), meta);
        } catch (IdentityService.RegistrationException e) {
            int status = "email".equals(e.field()) ? 409 : 400;
            throw new ApiException(status, status == 409 ? "EMAIL_TAKEN" : "VALIDATION_FAILED", e.getMessage(),
                    Map.of(e.field(), e.getMessage()));
        }
        UserRecord user = app().identity().authenticate(form.getEmail(), form.getPassword(), meta).orElseThrow();
        writeJson(res, 201, issueToken(user, req, meta));
    }

    private void login(HttpServletRequest req, HttpServletResponse res) throws IOException {
        LoginForm form = readBody(req, LoginForm.class);
        RequestMeta meta = meta(req);
        String key = "login:" + meta.rateKeyIp() + ":" + form.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!app().loginLimiter().tryAcquire(key)) {
            res.setHeader("Retry-After", Long.toString(Math.max(1, app().loginLimiter().retryAfterMillis(key) / 1000)));
            throw new ApiException(429, "RATE_LIMITED", "Too many attempts. Wait a minute and try again.");
        }
        UserRecord user = app().identity().authenticate(form.getEmail(), form.getPassword(), meta)
                .orElseThrow(() -> new ApiException(401, "INVALID_CREDENTIALS", "That email and password don't match an account."));
        app().loginLimiter().reset(key);
        writeJson(res, 200, issueToken(user, req, meta));
    }

    private TokenResponse issueToken(UserRecord user, HttpServletRequest req, RequestMeta meta) {
        IdentityService.NewSession s = app().identity().startSession(user, IdentityService.Client.ANDROID, meta);
        return new TokenResponse(s.token(), s.expiresAt(), MeApiServlet.Me.of(s.actor(), user.email()));
    }

}
