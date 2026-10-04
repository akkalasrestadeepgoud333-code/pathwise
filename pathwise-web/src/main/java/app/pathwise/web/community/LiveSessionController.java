package app.pathwise.web.community;

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
 * {@code /app/live}: the list of live doubt sessions (and, for hosts, a form to open one);
 * {@code /app/live/<id>}: the session; {@code /app/live/<id>/queue}: the part of it that refreshes every few seconds.
 */
public class LiveSessionController extends PageServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        LiveSessionService svc = app().live();
        req.setAttribute("nav", "live");
        String info = req.getPathInfo();
        if (info == null || "/".equals(info)) {
            req.setAttribute("home", ViewModels.of(svc.home(actor)));
            render(req, res, "live/index");
            return;
        }
        String[] parts = info.substring(1).split("/");
        UUID id = Ids.parseOrNull(parts[0]);
        boolean fragment = parts.length == 2 && "queue".equals(parts[1]);
        if (id == null || parts.length > 2 || (parts.length == 2 && !fragment)) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            req.setAttribute("s", ViewModels.of(svc.view(actor, id)));
        } catch (LiveSessionService.SessionNotFoundException e) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        res.setHeader("Cache-Control", "no-store");
        render(req, res, fragment ? "live/queue" : "live/session");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        LiveSessionService svc = app().live();
        String info = req.getPathInfo();
        if (info == null || "/".equals(info)) {
            try {
                UUID id = svc.create(actor, req.getParameter("title"));
                flash(req, "success", "Session created. Share the page link, then press Start when you're ready.");
                redirect(req, res, "/app/live/" + id);
            } catch (LiveSessionService.LiveRuleException e) {
                flash(req, "error", e.getMessage());
                redirect(req, res, "/app/live");
            }
            return;
        }
        UUID id = Ids.parseOrNull(info.substring(1));
        if (id == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            switch (param(req, "action")) {
                case "join" -> svc.join(actor, id);
                case "leave" -> svc.leave(actor, id);
                case "hand-up" -> svc.hand(actor, id, true);
                case "hand-down" -> svc.hand(actor, id, false);
                case "ask" -> svc.ask(actor, id, req.getParameter("body"));
                case "vote" -> svc.vote(actor, id, uuid(req, "questionId"));
                case "start" -> svc.move(actor, id, "live");
                case "end" -> svc.move(actor, id, "ended");
                case "lock" -> svc.setLocked(actor, id, true);
                case "unlock" -> svc.setLocked(actor, id, false);
                case "question" -> svc.setQuestion(actor, id, uuid(req, "questionId"), param(req, "to"));
                case "remove" -> svc.remove(actor, id, Integer.parseInt(param(req, "alias").strip()));
                default -> {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            }
        } catch (LiveSessionService.SessionNotFoundException e) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        } catch (LiveSessionService.LiveRuleException e) {
            flash(req, "error", e.getMessage());
        } catch (NumberFormatException e) {
            flash(req, "error", "No one has that alias here.");
        }
        redirect(req, res, "/app/live/" + id);
    }

    private static UUID uuid(HttpServletRequest req, String name) {
        UUID id = Ids.parseOrNull(req.getParameter(name));
        if (id == null) {
            throw new LiveSessionService.SessionNotFoundException();
        }
        return id;
    }
}
