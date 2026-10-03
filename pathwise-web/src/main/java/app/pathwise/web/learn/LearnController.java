package app.pathwise.web.learn;

import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.web.platform.http.PageServlet;
import app.pathwise.web.platform.view.ViewModels;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * {@code /app/learn} (subjects), {@code /app/map/<subject>} (Learning Map; {@code ?view=list} for the
 * list/trail view) and {@code /app/topics/<subject>/<topic>}.
 */
public class LearnController extends PageServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        String servletPath = req.getServletPath();
        String[] parts = req.getPathInfo() == null ? new String[0] : req.getPathInfo().substring(1).split("/");
        req.setAttribute("nav", "learn");

        if ("/app/learn".equals(servletPath)) {
            req.setAttribute("subjects", ViewModels.list(app().learn().subjects(actor)));
            render(req, res, "learn/index");
            return;
        }
        if ("/app/map".equals(servletPath) && parts.length == 1) {
            var map = app().learn().map(actor, parts[0]);
            if (map.isEmpty()) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            req.setAttribute("map", ViewModels.of(map.get()));
            String view = req.getParameter("view");
            // "auto": graph on wide screens, trail on phones (decided by CSS); explicit choices override it.
            req.setAttribute("view", "list".equals(view) || "graph".equals(view) ? view : "auto");
            render(req, res, "learn/map");
            return;
        }
        if ("/app/topics".equals(servletPath) && parts.length == 2) {
            var topic = app().learn().topic(actor, parts[0], parts[1]);
            if (topic.isEmpty()) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            req.setAttribute("topic", ViewModels.of(topic.get()));
            render(req, res, "learn/topic");
            return;
        }
        res.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
