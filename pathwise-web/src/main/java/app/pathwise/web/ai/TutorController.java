package app.pathwise.web.ai;

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
 * {@code /app/tutor}: your conversations and a new one; {@code POST /app/tutor/start} (mode, module, attempt,
 * position, q); {@code /app/tutor/<id>}: the conversation; {@code POST /app/tutor/<id>} with ask or delete.
 */
public class TutorController extends PageServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        req.setAttribute("nav", "tutor");
        String info = req.getPathInfo();
        TutorService svc = app().tutor();
        if (info == null || info.length() < 2) {
            req.setAttribute("home", ViewModels.of(svc.home(actor)));
            render(req, res, "tutor/index");
            return;
        }
        UUID id = Ids.parseOrNull(info.substring(1));
        try {
            if (id == null) {
                throw new TutorService.ConversationNotFoundException();
            }
            req.setAttribute("chat", ViewModels.of(svc.chat(actor, id)));
            render(req, res, "tutor/chat");
        } catch (TutorService.ConversationNotFoundException e) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        TutorService svc = app().tutor();
        String info = req.getPathInfo();
        try {
            if ("/start".equals(info)) {
                String position = param(req, "position");
                UUID id = svc.start(actor, param(req, "mode"), new TutorService.Ref(
                        req.getParameter("module") == null || req.getParameter("module").isBlank() ? null : req.getParameter("module"),
                        Ids.parseOrNull(req.getParameter("attempt")),
                        position.matches("\\d{1,2}") ? Integer.parseInt(position) : null), req.getParameter("q"));
                redirect(req, res, "/app/tutor/" + id);
                return;
            }
            UUID id = info == null || info.length() < 2 ? null : Ids.parseOrNull(info.substring(1));
            if (id == null) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            if ("delete".equals(param(req, "action"))) {
                svc.delete(actor, id);
                flash(req, "success", "Conversation deleted.");
                redirect(req, res, "/app/tutor");
                return;
            }
            svc.ask(actor, id, req.getParameter("q"));
            redirect(req, res, "/app/tutor/" + id + "#latest");
        } catch (TutorService.ConversationNotFoundException e) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
        } catch (TutorService.TutorRuleException e) {
            flash(req, "error", e.getMessage());
            String back = info != null && info.length() > 2 && !"/start".equals(info) ? "/app/tutor" + info : "/app/tutor";
            redirect(req, res, back);
        }
    }
}
