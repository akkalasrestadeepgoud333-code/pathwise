package app.pathwise.web.engine;

import app.pathwise.core.ids.Ids;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.web.platform.http.PageServlet;
import app.pathwise.web.platform.view.ViewModels;
import app.pathwise.web.practice.PracticeService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Recommendation Center.
 * <ul>
 *   <li>{@code GET /app/next}</li>
 *   <li>{@code POST /app/next/<id>/<start|not-now|not-relevant|already-know>}</li>
 *   <li>{@code POST /app/next/goals} with {@code topic} = "subject/topic" and optional {@code date}</li>
 *   <li>{@code POST /app/next/goals/<id>/end}</li>
 * </ul>
 */
public class NextController extends PageServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        if (req.getPathInfo() != null && !"/".equals(req.getPathInfo())) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        var recs = app().recommendations().current(actor);
        Map<String, List<Object>> slots = new LinkedHashMap<>();
        for (String s : List.of("continue", "strengthen", "challenge", "explore", "revise")) {
            slots.put(s, new ArrayList<>());
        }
        recs.forEach(r -> slots.computeIfAbsent(r.slot(), k -> new ArrayList<>()).add(ViewModels.of(r)));
        req.setAttribute("slots", slots);
        req.setAttribute("top", recs.isEmpty() ? null : ViewModels.of(recs.get(0)));
        req.setAttribute("goals", ViewModels.list(app().recommendations().goals(actor)));
        req.setAttribute("history", ViewModels.list(app().recommendations().history(actor)));
        List<Map<String, String>> topics = new ArrayList<>();
        for (var s : app().catalog().subjects()) {
            app().catalog().graph(s.key()).ifPresent(g -> g.topics().forEach(t ->
                    topics.add(Map.of("value", s.key() + "/" + t.key(), "label", s.title() + " · " + t.title()))));
        }
        req.setAttribute("topicOptions", topics);
        req.setAttribute("roomNext", app.pathwise.web.platform.view.ViewModels.list(app().rooms().roomNextSteps(actor)));
        req.setAttribute("nav", "next");
        render(req, res, "next/index");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        String[] p = req.getPathInfo() == null ? new String[0] : req.getPathInfo().substring(1).split("/");
        if (p.length == 1 && "goals".equals(p[0])) {
            String[] topic = param(req, "topic").split("/", 2);
            LocalDate date = null;
            try {
                if (!param(req, "date").isBlank()) {
                    date = LocalDate.parse(param(req, "date"));
                }
                if (topic.length != 2) {
                    throw new IllegalArgumentException();
                }
                app().recommendations().setGoal(actor, topic[0], topic[1], date);
                flash(req, "success", "Goal saved. Your suggestions now take it into account.");
            } catch (IllegalArgumentException | DateTimeParseException e) {
                flash(req, "error", "Choose a topic (and an optional valid date) for your goal.");
            }
            redirect(req, res, "/app/next");
            return;
        }
        if (p.length == 3 && "goals".equals(p[0]) && "end".equals(p[2])) {
            UUID id = Ids.parseOrNull(p[1]);
            if (id != null && app().recommendations().endGoal(actor, id)) {
                flash(req, "success", "Goal removed.");
            }
            redirect(req, res, "/app/next");
            return;
        }
        if (p.length == 2) {
            UUID id = Ids.parseOrNull(p[0]);
            RecommendationService.Action action = switch (p[1]) {
                case "start" -> RecommendationService.Action.START;
                case "not-now" -> RecommendationService.Action.NOT_NOW;
                case "not-relevant" -> RecommendationService.Action.NOT_RELEVANT;
                case "already-know" -> RecommendationService.Action.ALREADY_KNOW;
                default -> null;
            };
            if (id == null || action == null) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            try {
                var next = app().recommendations().act(actor, id, action);
                if (next.isEmpty()) {
                    flash(req, "info", "That suggestion has already been updated.");
                    redirect(req, res, back(req));
                    return;
                }
                if (action == RecommendationService.Action.NOT_NOW) {
                    flash(req, "info", "Okay. We'll hold that one until tomorrow.");
                } else if (action == RecommendationService.Action.NOT_RELEVANT) {
                    flash(req, "info", "Got it. We won't suggest that topic for a week.");
                } else if (action == RecommendationService.Action.ALREADY_KNOW) {
                    flash(req, "info", "Show us! A few quick questions will update your level.");
                }
                redirect(req, res, next.get().equals("/app/next") ? back(req) : next.get());
            } catch (PracticeService.NoQuestionsException e) {
                flash(req, "info", "There are no questions for that yet, so we'll take your word for it.");
                app().recommendations().regenerate(actor);
                redirect(req, res, back(req));
            }
            return;
        }
        res.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    /** Return to the page the action came from (Today or the Recommendation Center). */
    private static String back(HttpServletRequest req) {
        return "today".equals(req.getParameter("from")) ? "/app/today" : "/app/next";
    }
}
