package app.pathwise.web.learn;

import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.web.platform.http.PageServlet;
import app.pathwise.web.platform.view.ViewModels;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The module player.
 * <ul>
 *   <li>{@code GET /app/modules/<module>}: resume at the first unfinished section</li>
 *   <li>{@code GET /app/modules/<module>/<section>}: a specific section</li>
 *   <li>{@code POST /app/modules/<module>/<section>/done}: mark done, continue to the next section</li>
 * </ul>
 */
public class ModuleController extends PageServlet {
    private static final String KEY = "[a-z0-9][a-z0-9-]{0,80}";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        String[] parts = parts(req);
        if (parts.length < 1 || parts.length > 2 || !valid(parts)) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        var view = app().learn().player(actor, parts[0], parts.length == 2 ? parts[1] : null);
        if (view.isEmpty()) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        req.setAttribute("player", ViewModels.of(view.get()));
        var section = view.get().section();
        if ("check".equals(section.kind())) {
            java.util.UUID checkId = app.pathwise.core.ids.Ids.parseOrNull(req.getParameter("check"));
            var results = checkId == null ? java.util.Optional.<java.util.List<app.pathwise.web.practice.PracticeService.CheckItem>>empty()
                    : app().practice().checkResults(actor, checkId);
            if (results.isPresent()) {
                req.setAttribute("checkResults", ViewModels.list(results.get()));
                long right = results.get().stream().filter(r -> r.feedback().correct()).count();
                req.setAttribute("checkScore", right + " of " + results.get().size());
            } else {
                req.setAttribute("checkQuestions", ViewModels.list(app().practice().checkQuestions(actor, section.questionKeys())));
            }
        }
        req.setAttribute("justCompleted", "1".equals(req.getParameter("completed")));
        req.setAttribute("nav", "learn");
        render(req, res, "learn/module");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        String[] parts = parts(req);
        if (parts.length == 3 && "check".equals(parts[2]) && valid(new String[]{parts[0], parts[1]})) {
            submitCheck(req, res, actor, parts[0], parts[1]);
            return;
        }
        if (parts.length != 3 || !"done".equals(parts[2]) || !valid(new String[]{parts[0], parts[1]})) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        var result = app().learn().completeSection(actor, parts[0], parts[1]);
        if (result.isEmpty()) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String module = URLEncoder.encode(parts[0], StandardCharsets.UTF_8);
        if (result.get().moduleCompletedNow()) {
            redirect(req, res, "/app/modules/" + module + "/" + URLEncoder.encode(parts[1], StandardCharsets.UTF_8) + "?completed=1");
        } else if (result.get().nextKey() != null) {
            redirect(req, res, "/app/modules/" + module + "/" + URLEncoder.encode(result.get().nextKey(), StandardCharsets.UTF_8));
        } else {
            redirect(req, res, "/app/modules/" + module);
        }
    }

    /** Grades the check, marks the section done, and shows the results on the same section. */
    private void submitCheck(HttpServletRequest req, HttpServletResponse res, Actor actor, String moduleKey, String sectionKey)
            throws ServletException, IOException {
        var view = app().learn().player(actor, moduleKey, sectionKey);
        if (view.isEmpty() || !"check".equals(view.get().section().kind())) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        var section = view.get().section();
        var questions = app().practice().checkQuestions(actor, section.questionKeys());
        java.util.Map<java.util.UUID, app.pathwise.web.questions.QuestionService.Response> responses = new java.util.HashMap<>();
        for (var q : questions) {
            java.util.Set<java.util.UUID> options = new java.util.HashSet<>();
            String[] values = req.getParameterValues("q-" + q.id());
            if (values != null) {
                for (String v : values) {
                    java.util.UUID id = app.pathwise.core.ids.Ids.parseOrNull(v);
                    if (id != null) {
                        options.add(id);
                    }
                }
            }
            String text = req.getParameter("t-" + q.id());
            responses.put(q.id(), new app.pathwise.web.questions.QuestionService.Response(options,
                    text == null ? "" : text.substring(0, Math.min(500, text.length()))));
        }
        java.util.UUID attempt = app().practice().submitCheck(actor, java.util.UUID.fromString(section.id()), section.questionKeys(), responses);
        var outcome = app().learn().completeCheckedSection(actor, moduleKey, sectionKey);
        String base = "/app/modules/" + URLEncoder.encode(moduleKey, StandardCharsets.UTF_8) + "/" + URLEncoder.encode(sectionKey, StandardCharsets.UTF_8);
        redirect(req, res, base + "?check=" + attempt + (outcome.isPresent() && outcome.get().moduleCompletedNow() ? "&completed=1" : ""));
    }

    private static String[] parts(HttpServletRequest req) {
        String info = req.getPathInfo();
        return info == null || info.length() < 2 ? new String[0] : info.substring(1).split("/");
    }

    private static boolean valid(String[] parts) {
        for (String p : parts) {
            if (!p.matches(KEY)) {
                return false;
            }
        }
        return true;
    }
}
