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
 * Doubts and the knowledge base.
 * <ul>
 *   <li>{@code GET /app/doubts[?q=]}: mine and the public network; {@code GET|POST /app/doubts/ask}</li>
 *   <li>{@code GET /app/doubts/<id>}; {@code POST /app/doubts/<id>} with an {@code action}</li>
 *   <li>{@code GET /app/kb[?q=]}, {@code GET /app/kb/<id>}</li>
 * </ul>
 */
public class DoubtController extends PageServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        DoubtService svc = app().doubts();
        String[] p = parts(req);
        boolean kb = "/app/kb".equals(req.getServletPath());
        req.setAttribute("nav", "doubts");
        try {
            if (kb) {
                if (p.length == 0) {
                    req.setAttribute("articles", ViewModels.list(svc.articles(req.getParameter("q"))));
                    req.setAttribute("q", param(req, "q"));
                    render(req, res, "doubts/kb");
                } else {
                    UUID id = Ids.parseOrNull(p[0]);
                    if (id == null) {
                        res.sendError(HttpServletResponse.SC_NOT_FOUND);
                        return;
                    }
                    req.setAttribute("article", ViewModels.of(svc.article(id)));
                    render(req, res, "doubts/article");
                }
                return;
            }
            if (p.length == 0) {
                req.setAttribute("lists", ViewModels.of(svc.lists(actor, req.getParameter("q"))));
                render(req, res, "doubts/index");
                return;
            }
            if (p.length == 1 && "ask".equals(p[0])) {
                askForm(req, res, actor, null);
                return;
            }
            UUID id = p.length == 1 ? Ids.parseOrNull(p[0]) : null;
            if (id == null) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            req.setAttribute("thread", ViewModels.of(svc.view(actor, id)));
            render(req, res, "doubts/thread");
        } catch (DoubtService.DoubtNotFoundException e) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void askForm(HttpServletRequest req, HttpServletResponse res, Actor actor, java.util.List<?> similar)
            throws ServletException, IOException {
        req.setAttribute("options", ViewModels.of(app().doubts().askOptions(actor)));
        req.setAttribute("similar", similar == null ? null : ViewModels.list(app().doubts().similar(param(req, "title") + " " + param(req, "body"))));
        for (String f : new String[] {"title", "body", "category", "room", "topic", "showName"}) {
            req.setAttribute("f_" + f, param(req, f));
        }
        render(req, res, "doubts/ask");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        DoubtService svc = app().doubts();
        String[] p = parts(req);
        String action = param(req, "action");
        try {
            if (p.length == 1 && "ask".equals(p[0])) {
                if ("check".equals(action)) {
                    askForm(req, res, actor, java.util.List.of());
                    return;
                }
                UUID room = Ids.parseOrNull(req.getParameter("room"));
                UUID id = svc.ask(actor, room, param(req, "category"), req.getParameter("title"), req.getParameter("body"),
                        req.getParameter("topic"), "1".equals(req.getParameter("showName")));
                flash(req, "success", room == null ? "Posted to the doubt network." : "Sent to your teacher.");
                redirect(req, res, "/app/doubts/" + id);
                return;
            }
            UUID id = p.length == 1 ? Ids.parseOrNull(p[0]) : null;
            if (id == null) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            switch (action) {
                case "answer" -> {
                    svc.answer(actor, id, req.getParameter("body"), "1".equals(req.getParameter("showName")));
                    flash(req, "success", "Answer posted.");
                }
                case "vote" -> svc.vote(actor, uuid(req, "postId"));
                case "accept" -> {
                    svc.accept(actor, id, uuid(req, "postId"));
                    flash(req, "success", "Marked as the answer that helped. Your doubt is resolved.");
                }
                case "verify" -> {
                    svc.verify(actor, uuid(req, "postId"));
                    flash(req, "success", "Marked as teacher verified.");
                }
                case "report" -> {
                    svc.report(actor, param(req, "kind"), uuid(req, "targetId"), param(req, "reason"), req.getParameter("details"));
                    flash(req, "success", "Thanks. A moderator will look at it.");
                }
                case "block" -> {
                    int alias;
                    try {
                        alias = Integer.parseInt(param(req, "alias"));
                    } catch (NumberFormatException e) {
                        throw new DoubtService.DoubtNotFoundException();
                    }
                    svc.block(actor, id, alias);
                    flash(req, "success", "Blocked. You won't see their doubts or answers anymore.");
                    redirect(req, res, "/app/doubts");
                    return;
                }
                default -> {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            }
            redirect(req, res, "/app/doubts/" + id);
        } catch (DoubtService.DoubtNotFoundException e) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
        } catch (DoubtService.DoubtRuleException e) {
            flash(req, "error", e.getMessage());
            if (p.length == 1 && "ask".equals(p[0])) {
                askForm(req, res, actor, null);
            } else {
                redirect(req, res, "/app/doubts/" + p[0]);
            }
        }
    }

    private static UUID uuid(HttpServletRequest req, String name) {
        UUID id = Ids.parseOrNull(req.getParameter(name));
        if (id == null) {
            throw new DoubtService.DoubtNotFoundException();
        }
        return id;
    }

    private static String[] parts(HttpServletRequest req) {
        String info = req.getPathInfo();
        return info == null || info.length() < 2 ? new String[0] : info.substring(1).split("/");
    }
}
