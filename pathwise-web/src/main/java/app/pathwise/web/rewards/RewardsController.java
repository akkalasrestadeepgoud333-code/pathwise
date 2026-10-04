package app.pathwise.web.rewards;

import app.pathwise.core.ids.Ids;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.web.platform.http.PageServlet;
import app.pathwise.web.platform.view.ViewModels;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

/**
 * {@code /app/achievements}: level, XP, streak and the trophy case. {@code /app/notifications}: the inbox;
 * {@code GET /app/notifications/<id>} marks one read and opens it; {@code POST /app/notifications} marks all read.
 */
public class RewardsController extends PageServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        RewardsService svc = app().rewards();
        if ("/app/achievements".equals(req.getServletPath())) {
            req.setAttribute("nav", "achievements");
            req.setAttribute("ach", ViewModels.of(svc.achievements(actor)));
            render(req, res, "rewards/achievements");
            return;
        }
        String info = req.getPathInfo();
        if (info != null && info.length() > 1) {
            UUID id = Ids.parseOrNull(info.substring(1));
            String link = id == null ? null : svc.open(actor, id).orElse(null);
            redirect(req, res, link == null ? "/app/notifications" : link);
            return;
        }
        req.setAttribute("nav", "notifications");
        req.setAttribute("items", ViewModels.list(svc.notifications(actor)));
        render(req, res, "rewards/notifications");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        app().rewards().markAllRead(actor);
        redirect(req, res, "/app/notifications");
    }
}
