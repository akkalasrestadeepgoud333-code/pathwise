package app.pathwise.web.identity;

import app.pathwise.core.ids.Ids;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.data.identity.UserRecord;
import app.pathwise.web.platform.http.PageServlet;
import app.pathwise.web.platform.view.TimeLabels;
import app.pathwise.web.platform.view.ViewModels;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

/**
 * {@code /app/settings}: profile summary, appearance and active sessions.
 * POST sub-paths: {@code /theme}, {@code /sessions/revoke-others}, {@code /sessions/<id>/revoke}.
 */
public class SettingsController extends PageServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.MANAGE_OWN_ACCOUNT);
        if (req.getPathInfo() != null && !"/".equals(req.getPathInfo())) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        UserRecord user = app().identity().findUser(actor.userId()).orElseThrow();
        req.setAttribute("email", user.email());
        req.setAttribute("memberSince", user.createdAt());
        java.time.Instant now = app().clock().instant();
        req.setAttribute("sessions", app().identity().listSessions(actor).stream().map(s -> {
            java.util.Map<String, Object> view = new java.util.LinkedHashMap<>(ViewModels.of(s));
            view.put("createdLabel", TimeLabels.date(s.createdAt(), TimeLabels.DEFAULT_ZONE));
            view.put("lastUsedLabel", TimeLabels.relative(s.lastUsedAt(), now, TimeLabels.DEFAULT_ZONE));
            return view;
        }).toList());
        req.setAttribute("nav", "settings");
        render(req, res, "settings/index");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.MANAGE_OWN_ACCOUNT);
        String path = req.getPathInfo() == null ? "/" : req.getPathInfo();
        boolean fetch = "fetch".equals(req.getHeader("X-Requested-With")) || req.getHeader("HX-Request") != null;

        if ("/theme".equals(path)) {
            try {
                app().identity().setTheme(actor, req.getParameter("theme"));
            } catch (IllegalArgumentException e) {
                res.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            if (fetch) {
                res.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                flash(req, "success", "Appearance saved.");
                redirect(req, res, "/app/settings");
            }
            return;
        }
        if ("/sessions/revoke-others".equals(path)) {
            int n = app().identity().revokeOtherSessions(actor, meta(req));
            flash(req, "success", n == 0 ? "No other sessions were signed in." : "Signed out " + n + " other session" + (n == 1 ? "." : "s."));
            redirect(req, res, "/app/settings#sessions");
            return;
        }
        if (path.startsWith("/sessions/") && path.endsWith("/revoke")) {
            UUID id = Ids.parseOrNull(path.substring("/sessions/".length(), path.length() - "/revoke".length()));
            if (id != null && id.equals(actor.sessionId())) {
                flash(req, "info", "To end this session, use Sign out.");
            } else if (id != null && app().identity().revokeSession(actor, id, meta(req))) {
                flash(req, "success", "Session signed out.");
            } else {
                flash(req, "error", "That session was already signed out.");
            }
            redirect(req, res, "/app/settings#sessions");
            return;
        }
        res.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
