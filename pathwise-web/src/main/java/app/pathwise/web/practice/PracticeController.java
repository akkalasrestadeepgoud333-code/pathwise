package app.pathwise.web.practice;

import app.pathwise.core.ids.Ids;
import app.pathwise.core.learner.MasteryState;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.data.catalog.CatalogDao.SkillRef;
import app.pathwise.data.learner.EvidenceDao.MasteryRow;
import app.pathwise.web.platform.http.PageServlet;
import app.pathwise.web.platform.view.ViewModels;
import app.pathwise.web.questions.QuestionService.Response;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Practice pages.
 * <ul>
 *   <li>{@code GET /app/practice}: hub (quick practice, skills to strengthen)</li>
 *   <li>{@code POST /app/practice/start}: {@code mode}, optional {@code skill}</li>
 *   <li>{@code GET /app/practice/<attempt>}: the current question, or the summary</li>
 *   <li>{@code POST /app/practice/<attempt>/<n>/answer} then {@code GET .../<n>/feedback}</li>
 *   <li>{@code POST /app/practice/<attempt>/<n>/hint}</li>
 * </ul>
 */
public class PracticeController extends PageServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        req.setAttribute("nav", "practice");
        String[] p = parts(req);
        if (p.length == 0) {
            hub(req, res, actor);
            return;
        }
        UUID attemptId = Ids.parseOrNull(p[0]);
        if (attemptId == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (p.length == 1) {
            var view = app().practice().view(actor, attemptId);
            if (view.isEmpty()) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            req.setAttribute("practiceSession", ViewModels.of(view.get()));
            if (view.get().summary() != null) {
                req.setAttribute("next", app().recommendations().top(actor).map(ViewModels::of).orElse(null));
            }
            render(req, res, "practice/session");
            return;
        }
        if (p.length == 3 && "feedback".equals(p[2]) && p[1].matches("\\d{1,2}")) {
            var result = app().practice().answered(actor, attemptId, Integer.parseInt(p[1]));
            if (result.isEmpty()) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            req.setAttribute("attemptId", attemptId.toString());
            req.setAttribute("result", ViewModels.of(result.get()));
            var session = app().practice().view(actor, attemptId).orElseThrow();
            req.setAttribute("title", session.title());
            render(req, res, "practice/feedback");
            return;
        }
        res.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        String[] p = parts(req);
        if (p.length == 1 && "start".equals(p[0])) {
            PracticeService.Mode mode;
            try {
                mode = PracticeService.Mode.valueOf(param(req, "mode").toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                mode = PracticeService.Mode.PRACTICE;
            }
            UUID skill = Ids.parseOrNull(req.getParameter("skill"));
            try {
                UUID attempt = app().practice().start(actor, skill == null ? PracticeService.Mode.QUICK : mode, skill, null);
                redirect(req, res, "/app/practice/" + attempt);
            } catch (PracticeService.NoQuestionsException e) {
                flash(req, "info", "There's nothing to practise yet. Study a module first, and practice questions will follow.");
                redirect(req, res, "/app/practice");
            }
            return;
        }
        UUID attemptId = p.length == 3 ? Ids.parseOrNull(p[0]) : null;
        if (attemptId == null || !p[1].matches("\\d{1,2}")) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        int position = Integer.parseInt(p[1]);
        if ("hint".equals(p[2])) {
            app().practice().revealHint(actor, attemptId, position);
            redirect(req, res, "/app/practice/" + attemptId);
            return;
        }
        if ("answer".equals(p[2])) {
            var result = app().practice().answer(actor, attemptId, position, response(req));
            if (result.isEmpty()) {
                // Already answered (double submit) or not this user's: show what we have.
                redirect(req, res, "/app/practice/" + attemptId);
                return;
            }
            redirect(req, res, "/app/practice/" + attemptId + "/" + position + "/feedback");
            return;
        }
        res.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    private void hub(HttpServletRequest req, HttpServletResponse res, Actor actor) throws ServletException, IOException {
        Map<UUID, MasteryRow> mastery = app().learner().mastery(actor);
        var supply = app().questions().supply();
        List<SkillRef> refs = app().catalog().skillRefs(mastery.keySet());
        List<Map<String, Object>> focus = new ArrayList<>();
        List<Map<String, Object>> strong = new ArrayList<>();
        refs.stream()
                .sorted(Comparator.comparingDouble((SkillRef s) -> mastery.get(s.id()).estimate()))
                .forEach(s -> {
                    MasteryRow m = mastery.get(s.id());
                    MasteryState st = MasteryState.fromDb(m.state());
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("id", s.id().toString());
                    v.put("title", s.title());
                    v.put("topicTitle", s.topicTitle());
                    v.put("state", st.cssClass());
                    v.put("stateLabel", st.label());
                    v.put("evidence", m.evidenceCount());
                    v.put("practicable", supply.containsKey(s.id()));
                    v.put("challengeable", supply.containsKey(s.id()) && supply.get(s.id()).challenge() > 0);
                    (st.atLeast(MasteryState.PROFICIENT) ? strong : focus).add(v);
                });
        req.setAttribute("focus", focus);
        req.setAttribute("strong", strong);
        render(req, res, "practice/hub");
    }

    private static Response response(HttpServletRequest req) {
        Set<UUID> options = new HashSet<>();
        String[] values = req.getParameterValues("option");
        if (values != null) {
            for (String v : values) {
                UUID id = Ids.parseOrNull(v);
                if (id != null) {
                    options.add(id);
                }
            }
        }
        String text = req.getParameter("text");
        return new Response(options, text == null ? "" : text.substring(0, Math.min(text.length(), 500)));
    }

    private static String[] parts(HttpServletRequest req) {
        String info = req.getPathInfo();
        return info == null || info.length() < 2 ? new String[0] : info.substring(1).split("/");
    }
}
