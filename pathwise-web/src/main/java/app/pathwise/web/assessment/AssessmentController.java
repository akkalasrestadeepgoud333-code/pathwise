package app.pathwise.web.assessment;

import app.pathwise.core.ids.Ids;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.web.platform.http.PageServlet;
import app.pathwise.web.platform.view.ViewModels;
import app.pathwise.web.questions.QuestionService.Response;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Official assessments.
 * <ul>
 *   <li>{@code GET /app/assessments}: list</li>
 *   <li>{@code GET /app/assessments/<key>}: rules page; {@code POST .../<key>/start}</li>
 *   <li>{@code GET /app/assessments/attempt/<id>}: answer; {@code POST .../save} and {@code POST .../submit}</li>
 *   <li>{@code GET /app/assessments/result/<id>}</li>
 * </ul>
 * Answers are posted as {@code item=<itemId>} (one per item on the page), {@code o.<itemId>=<optionId>} and
 * {@code t.<itemId>=<text>}, so clearing an answer is saved too.
 */
public class AssessmentController extends PageServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        req.setAttribute("nav", "assessments");
        String[] p = parts(req);
        AssessmentService svc = app().assessments();
        if (p.length == 0) {
            req.setAttribute("cards", ViewModels.list(svc.list(actor)));
            render(req, res, "assessments/index");
            return;
        }
        if (p.length == 2 && "attempt".equals(p[0])) {
            UUID id = Ids.parseOrNull(p[1]);
            var view = id == null ? java.util.Optional.<AssessmentService.AttemptView>empty() : svc.attempt(actor, id);
            if (view.isEmpty()) {
                // Finished (or time ran out and it was just submitted): show the result if it is theirs.
                if (id != null && svc.result(actor, id).isPresent()) {
                    redirect(req, res, "/app/assessments/result/" + id);
                } else {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                }
                return;
            }
            req.setAttribute("attempt", ViewModels.of(view.get()));
            req.setAttribute("confirming", "1".equals(req.getParameter("confirm")));
            render(req, res, "assessments/attempt");
            return;
        }
        if (p.length == 2 && "result".equals(p[0])) {
            UUID id = Ids.parseOrNull(p[1]);
            var result = id == null ? java.util.Optional.<AssessmentService.Result>empty() : svc.result(actor, id);
            if (result.isEmpty()) {
                if (id != null && svc.attempt(actor, id).isPresent()) {
                    redirect(req, res, "/app/assessments/attempt/" + id); // still running
                } else {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                }
                return;
            }
            req.setAttribute("result", ViewModels.of(result.get()));
            req.setAttribute("next", app().recommendations().top(actor).map(ViewModels::of).orElse(null));
            render(req, res, "assessments/result");
            return;
        }
        if (p.length == 1) {
            var rules = svc.rules(actor, p[0]);
            if (rules.isEmpty()) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            req.setAttribute("rules", ViewModels.of(rules.get()));
            render(req, res, "assessments/rules");
            return;
        }
        res.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        String[] p = parts(req);
        AssessmentService svc = app().assessments();
        if (p.length == 2 && "start".equals(p[1])) {
            try {
                UUID attempt = svc.start(actor, p[0]);
                redirect(req, res, "/app/assessments/attempt/" + attempt);
            } catch (AssessmentService.RuleException e) {
                flash(req, "info", e.getMessage());
                redirect(req, res, "/app/assessments/" + p[0]);
            }
            return;
        }
        UUID id = p.length == 3 && "attempt".equals(p[0]) ? Ids.parseOrNull(p[1]) : null;
        if (id == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        boolean fetch = "fetch".equals(req.getHeader("X-Requested-With"));
        Map<UUID, Response> answers = answers(req);
        if ("save".equals(p[2])) {
            boolean saved = svc.save(actor, id, answers);
            if (fetch) {
                res.setStatus(saved ? HttpServletResponse.SC_NO_CONTENT : HttpServletResponse.SC_CONFLICT);
                return;
            }
            if (saved) {
                flash(req, "success", "Your answers are saved.");
            }
            redirect(req, res, "/app/assessments/attempt/" + id);
            return;
        }
        if ("submit".equals(p[2])) {
            boolean auto = "1".equals(req.getParameter("auto"));
            if (!auto && !"yes".equals(req.getParameter("confirm"))) {
                svc.save(actor, id, answers);
                var view = svc.attempt(actor, id);
                if (view.isPresent() && view.get().answered() < view.get().total()) {
                    int missing = view.get().total() - view.get().answered();
                    flash(req, "info", missing == 1 ? "1 question is unanswered. Submit anyway, or go back and answer it."
                            : missing + " questions are unanswered. Submit anyway, or go back and answer them.");
                    redirect(req, res, "/app/assessments/attempt/" + id + "?confirm=1");
                    return;
                }
            }
            svc.submit(actor, id, answers);
            redirect(req, res, "/app/assessments/result/" + id);
            return;
        }
        res.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    static Map<UUID, Response> answers(HttpServletRequest req) {
        Map<UUID, Response> out = new LinkedHashMap<>();
        String[] items = req.getParameterValues("item");
        if (items == null) {
            return out;
        }
        for (String raw : items) {
            UUID item = Ids.parseOrNull(raw);
            if (item == null || out.size() >= 60) {
                continue;
            }
            Set<UUID> options = new HashSet<>();
            String[] chosen = req.getParameterValues("o." + item);
            if (chosen != null) {
                for (String v : chosen) {
                    UUID o = Ids.parseOrNull(v);
                    if (o != null) {
                        options.add(o);
                    }
                }
            }
            String text = req.getParameter("t." + item);
            text = text == null ? "" : text.strip();
            out.put(item, new Response(options, text.substring(0, Math.min(text.length(), 500))));
        }
        return out;
    }

    private static String[] parts(HttpServletRequest req) {
        String info = req.getPathInfo();
        return info == null || info.length() < 2 ? new String[0] : info.substring(1).split("/");
    }
}
