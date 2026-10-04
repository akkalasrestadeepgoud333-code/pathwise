package app.pathwise.web.engine;

import app.pathwise.core.ids.Ids;
import app.pathwise.core.security.Actor;
import app.pathwise.web.platform.http.ApiException;
import app.pathwise.web.platform.http.ApiServlet;
import app.pathwise.web.practice.PracticeService;
import app.pathwise.web.questions.QuestionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Size;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Learner API for the Android app.
 * <ul>
 *   <li>{@code GET /api/v1/me/recommendations}, {@code POST /api/v1/recommendations/<id>/<action>}</li>
 *   <li>{@code GET /api/v1/me/mastery}</li>
 *   <li>{@code POST /api/v1/practice} {"mode": "practice|challenge|review|quick", "skillId": "..."}</li>
 *   <li>{@code GET /api/v1/practice/<id>}, {@code POST /api/v1/practice/<id>/<n>/answer}, {@code POST .../<n>/hint}</li>
 * </ul>
 */
public class LearnerApiServlet extends ApiServlet {

    public static class StartBody {
        public String mode;
        public String skillId;
    }

    public static class AnswerBody {
        @Size(max = 8)
        public List<String> options;
        @Size(max = 500)
        public String text;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Actor actor = requireActor(req);
        String path = req.getServletPath() + (req.getPathInfo() == null ? "" : req.getPathInfo());
        if ("/api/v1/me/recommendations".equals(path)) {
            writeJson(res, 200, Map.of("items", app().recommendations().current(actor)));
            return;
        }
        if ("/api/v1/me/mastery".equals(path)) {
            var mastery = app().learner().mastery(actor);
            var refs = app().catalog().skillRefs(mastery.keySet());
            writeJson(res, 200, Map.of("items", refs.stream().map(s -> {
                var m = mastery.get(s.id());
                return Map.of("skillId", s.id(), "skill", s.title(), "topic", s.topicTitle(), "state", m.state(),
                        "evidence", m.evidenceCount(), "confidence", m.confidence());
            }).toList(), "model", app.pathwise.core.learner.MasteryModel.VERSION));
            return;
        }
        if (path.startsWith("/api/v1/practice/")) {
            UUID id = Ids.parseOrNull(path.substring("/api/v1/practice/".length()));
            var view = id == null ? java.util.Optional.<PracticeService.SessionView>empty() : app().practice().view(actor, id);
            writeJson(res, 200, view.orElseThrow(LearnerApiServlet::notFoundError));
            return;
        }
        notFound();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Actor actor = requireActor(req);
        String path = req.getServletPath() + (req.getPathInfo() == null ? "" : req.getPathInfo());
        String[] p = path.substring(1).split("/");
        // recommendations/<id>/<action>
        if (p.length == 5 && "recommendations".equals(p[2])) {
            UUID id = Ids.parseOrNull(p[3]);
            RecommendationService.Action action;
            try {
                action = RecommendationService.Action.valueOf(p[4].toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException e) {
                throw notFoundError();
            }
            if (id == null) {
                throw notFoundError();
            }
            try {
                String next = app().recommendations().act(actor, id, action).orElseThrow(LearnerApiServlet::notFoundError);
                writeJson(res, 200, Map.of("next", next));
            } catch (PracticeService.NoQuestionsException e) {
                throw new ApiException(409, "NO_QUESTIONS", e.getMessage());
            }
            return;
        }
        if ("/api/v1/practice".equals(path)) {
            StartBody body = readBody(req, StartBody.class);
            PracticeService.Mode mode;
            try {
                mode = PracticeService.Mode.valueOf(body.mode == null ? "QUICK" : body.mode.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ApiException(400, "VALIDATION_FAILED", "mode must be practice, challenge, review or quick");
            }
            UUID skill = Ids.parseOrNull(body.skillId);
            try {
                UUID attempt = app().practice().start(actor, skill == null ? PracticeService.Mode.QUICK : mode, skill, null);
                writeJson(res, 201, Map.of("attemptId", attempt));
            } catch (PracticeService.NoQuestionsException e) {
                throw new ApiException(409, "NO_QUESTIONS", e.getMessage());
            }
            return;
        }
        // practice/<id>/<n>/<answer|hint>
        if (p.length == 6 && "practice".equals(p[2]) && p[4].matches("\\d{1,2}")) {
            UUID id = Ids.parseOrNull(p[3]);
            int position = Integer.parseInt(p[4]);
            if (id == null) {
                throw notFoundError();
            }
            if ("hint".equals(p[5])) {
                app().practice().revealHint(actor, id, position);
                writeJson(res, 200, app().practice().view(actor, id).orElseThrow(LearnerApiServlet::notFoundError));
                return;
            }
            if ("answer".equals(p[5])) {
                AnswerBody body = readBody(req, AnswerBody.class);
                Set<UUID> options = new HashSet<>();
                if (body.options != null) {
                    body.options.forEach(o -> {
                        UUID u = Ids.parseOrNull(o);
                        if (u != null) {
                            options.add(u);
                        }
                    });
                }
                var result = app().practice().answer(actor, id, position, new QuestionService.Response(options, body.text))
                        .orElseThrow(() -> new ApiException(409, "ALREADY_ANSWERED", "This question was already answered or does not exist."));
                writeJson(res, 200, result);
                return;
            }
        }
        notFound();
    }

    private static ApiException notFoundError() {
        return new ApiException(404, "NOT_FOUND", "Not found.");
    }
}
