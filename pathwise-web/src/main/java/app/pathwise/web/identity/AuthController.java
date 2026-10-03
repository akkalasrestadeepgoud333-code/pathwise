package app.pathwise.web.identity;

import app.pathwise.core.security.Actor;
import app.pathwise.data.identity.UserRecord;
import app.pathwise.web.platform.RequestMeta;
import app.pathwise.web.platform.Validation;
import app.pathwise.web.platform.http.AuthFilter;
import app.pathwise.web.platform.http.Csrf;
import app.pathwise.web.platform.http.PageServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Sign-in, sign-up and sign-out pages: {@code /login}, {@code /register}, {@code /logout}. */
public class AuthController extends PageServlet {
    private static final String GENERIC_LOGIN_ERROR = "That email and password don't match an account.";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = actor(req);
        String path = req.getServletPath();
        if (actor.isAuthenticated() && !"/logout".equals(path)) {
            redirect(req, res, actor.homePath());
            return;
        }
        switch (path) {
            case "/login" -> {
                req.setAttribute("form", new LoginForm());
                req.setAttribute("next", safeNext(req.getParameter("next"), ""));
                render(req, res, "auth/login");
            }
            case "/register" -> {
                RegisterForm form = new RegisterForm();
                if ("teacher".equals(req.getParameter("as"))) {
                    form.setRole("teacher");
                }
                req.setAttribute("form", form);
                render(req, res, "auth/register");
            }
            default -> redirect(req, res, "/"); // GET /logout does nothing: sign-out is POST-only
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        switch (req.getServletPath()) {
            case "/login" -> login(req, res);
            case "/register" -> register(req, res);
            case "/logout" -> logout(req, res);
            default -> res.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void login(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        LoginForm form = new LoginForm();
        form.setEmail(param(req, "email").trim());
        form.setPassword(param(req, "password"));
        String next = safeNext(req.getParameter("next"), "");
        req.setAttribute("form", form);
        req.setAttribute("next", next);

        Map<String, String> errors = Validation.validate(form);
        if (!errors.isEmpty()) {
            req.setAttribute("errors", errors);
            res.setStatus(422);
            render(req, res, "auth/login");
            return;
        }
        RequestMeta meta = meta(req);
        String limiterKey = "login:" + meta.rateKeyIp() + ":" + form.getEmail().toLowerCase(Locale.ROOT);
        if (!app().loginLimiter().tryAcquire(limiterKey)) {
            long seconds = Math.max(1, app().loginLimiter().retryAfterMillis(limiterKey) / 1000);
            res.setHeader("Retry-After", Long.toString(seconds));
            req.setAttribute("formError", "Too many attempts. Wait " + seconds + " seconds and try again.");
            res.setStatus(429);
            render(req, res, "auth/login");
            return;
        }
        Optional<UserRecord> user = app().identity().authenticate(form.getEmail(), form.getPassword(), meta);
        if (user.isEmpty()) {
            req.setAttribute("formError", GENERIC_LOGIN_ERROR);
            res.setStatus(401);
            render(req, res, "auth/login");
            return;
        }
        app().loginLimiter().reset(limiterKey);
        IdentityService.NewSession session = app().identity().startSession(user.get(), IdentityService.Client.WEB, meta);
        establishWebSession(req, session);
        redirect(req, res, next.isEmpty() ? session.actor().homePath() : next);
    }

    private void register(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        RegisterForm form = new RegisterForm();
        form.setDisplayName(param(req, "displayName").trim());
        form.setEmail(param(req, "email").trim());
        form.setPassword(param(req, "password"));
        form.setRole(param(req, "role").isEmpty() ? "student" : param(req, "role"));
        req.setAttribute("form", form);

        Map<String, String> errors = new java.util.LinkedHashMap<>(Validation.validate(form));
        RequestMeta meta = meta(req);
        if (errors.isEmpty() && !app().registerLimiter().tryAcquire("register:" + meta.rateKeyIp())) {
            req.setAttribute("formError", "Too many sign-ups from this network. Try again later.");
            res.setStatus(429);
            render(req, res, "auth/register");
            return;
        }
        if (errors.isEmpty()) {
            try {
                app().identity().register(new IdentityService.Registration(
                        form.getEmail(), form.getPassword(), form.getDisplayName(), form.isTeacher()), meta);
            } catch (IdentityService.RegistrationException e) {
                errors.put(e.field(), e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            req.setAttribute("errors", errors);
            res.setStatus(422);
            render(req, res, "auth/register");
            return;
        }
        UserRecord user = app().identity().authenticate(form.getEmail(), form.getPassword(), meta).orElseThrow();
        IdentityService.NewSession session = app().identity().startSession(user, IdentityService.Client.WEB, meta);
        establishWebSession(req, session);
        flash(req, "success", "Welcome to Pathwise, " + session.actor().displayName().split("\\s+")[0] + ".");
        redirect(req, res, session.actor().homePath());
    }

    private void logout(HttpServletRequest req, HttpServletResponse res) throws IOException {
        app().identity().endSession(actor(req), meta(req));
        var session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        redirect(req, res, "/login?signedOut=1");
    }

    /** New HttpSession id (prevents session fixation) and a fresh CSRF token. */
    private static void establishWebSession(HttpServletRequest req, IdentityService.NewSession session) {
        req.getSession(true);
        req.changeSessionId();
        req.getSession().setAttribute(AuthFilter.SESSION_KEY, session.id().toString());
        Csrf.rotate(req);
    }
}
