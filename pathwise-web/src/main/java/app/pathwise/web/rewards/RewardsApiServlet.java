package app.pathwise.web.rewards;

import app.pathwise.core.security.Actor;
import app.pathwise.web.platform.http.ApiServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/**
 * {@code GET /api/v1/me/rewards} (level, XP, streak, badges), {@code GET /api/v1/me/notifications},
 * {@code POST /api/v1/me/notifications/read-all}. The Android app polls these when it opens.
 */
public class RewardsApiServlet extends ApiServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Actor actor = requireActor(req);
        String path = req.getServletPath();
        if ("/api/v1/me/rewards".equals(path)) {
            writeJson(res, 200, app().rewards().achievements(actor));
            return;
        }
        if ("/api/v1/me/notifications".equals(path)) {
            writeJson(res, 200, Map.of("items", app().rewards().notifications(actor), "unread", app().rewards().unread(actor.userId())));
            return;
        }
        notFound();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Actor actor = requireActor(req);
        if ("/api/v1/me/notifications".equals(req.getServletPath()) && "/read-all".equals(req.getPathInfo())) {
            app().rewards().markAllRead(actor);
            writeJson(res, 200, Map.of("unread", 0));
            return;
        }
        notFound();
    }
}
