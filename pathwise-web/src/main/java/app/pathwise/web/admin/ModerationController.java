package app.pathwise.web.admin;

import app.pathwise.core.ids.Ids;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.web.community.DoubtService;
import app.pathwise.web.platform.http.PageServlet;
import app.pathwise.web.platform.view.ViewModels;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

/**
 * {@code /admin/moderation}: the report queue, identity reveal (logged, with a reason), and promoting resolved
 * public doubts into the knowledge base ({@code /admin/moderation/promote/<threadId>}).
 */
public class ModerationController extends PageServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.MODERATE_COMMUNITY);
        req.setAttribute("nav", "admin-moderation");
        String info = req.getPathInfo();
        if (info != null && info.startsWith("/promote/")) {
            UUID thread = Ids.parseOrNull(info.substring("/promote/".length()));
            try {
                req.setAttribute("draft", thread == null ? null : app().doubts().promotionDraft(actor, thread));
            } catch (DoubtService.DoubtNotFoundException e) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            if (thread == null) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            req.setAttribute("threadId", thread.toString());
            req.setAttribute("options", ViewModels.of(app().doubts().askOptions(actor)));
            render(req, res, "admin/promote");
            return;
        }
        req.setAttribute("reports", ViewModels.list(app().doubts().queue(actor)));
        var session = req.getSession(false);
        if (session != null && session.getAttribute("pw.revealed") != null) {
            req.setAttribute("revealed", session.getAttribute("pw.revealed")); // shown once
            session.removeAttribute("pw.revealed");
        }
        render(req, res, "admin/moderation");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.MODERATE_COMMUNITY);
        DoubtService svc = app().doubts();
        String back = "/admin/moderation";
        try {
            switch (param(req, "action")) {
                case "moderate" -> {
                    UUID report = Ids.parseOrNull(req.getParameter("reportId"));
                    if (report == null) {
                        res.sendError(HttpServletResponse.SC_NOT_FOUND);
                        return;
                    }
                    svc.moderate(actor, report, param(req, "decision"), req.getParameter("reason"));
                    flash(req, "success", "Done. The action and your reason are in the moderation log.");
                }
                case "reveal" -> {
                    UUID thread = Ids.parseOrNull(req.getParameter("threadId"));
                    int alias = Integer.parseInt(param(req, "alias").strip());
                    if (thread == null) {
                        throw new DoubtService.DoubtNotFoundException();
                    }
                    DoubtService.Revealed who = svc.reveal(actor, thread, alias, req.getParameter("reason"));
                    req.getSession(true).setAttribute("pw.revealed", who.displayName() + " <" + who.email() + ">");
                }
                case "publish-article" -> {
                    UUID source = Ids.parseOrNull(req.getParameter("threadId"));
                    UUID id = svc.publishArticle(actor, source, req.getParameter("topic"), req.getParameter("title"), req.getParameter("body"));
                    flash(req, "success", "Published to the knowledge base.");
                    back = "/app/kb/" + id;
                }
                default -> {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            }
        } catch (DoubtService.DoubtRuleException e) {
            flash(req, "error", e.getMessage());
            if ("publish-article".equals(param(req, "action")) && req.getParameter("threadId") != null) {
                back = "/admin/moderation/promote/" + param(req, "threadId");
            }
        } catch (DoubtService.DoubtNotFoundException | NumberFormatException e) {
            flash(req, "error", "No one has that alias in that doubt.");
        }
        redirect(req, res, back);
    }
}
