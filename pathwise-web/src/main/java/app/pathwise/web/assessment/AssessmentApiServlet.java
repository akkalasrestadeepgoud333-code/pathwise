package app.pathwise.web.assessment;

import app.pathwise.core.ids.Ids;
import app.pathwise.core.security.Actor;
import app.pathwise.web.platform.http.ApiException;
import app.pathwise.web.platform.http.ApiServlet;
import app.pathwise.web.questions.QuestionService.Response;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Assessment API for the Android app (same rules as the web pages: the server keeps the clock and grades).
 * <ul>
 *   <li>{@code GET /api/v1/assessments}, {@code GET /api/v1/assessments/<key>}, {@code POST /api/v1/assessments/<key>/start}</li>
 *   <li>{@code GET /api/v1/assessments/attempts/<id>}, {@code POST .../save}, {@code POST .../submit}, {@code GET .../result}</li>
 * </ul>
 * Answers: {@code {"answers": [{"itemId": "...", "options": ["..."], "text": "..."}]}}.
 */
public class AssessmentApiServlet extends ApiServlet {

    public static class AnswerBody {
        public String itemId;
        @Size(max = 8)
        public List<String> options;
        @Size(max = 500)
        public String text;
    }

    public static class AnswersBody {
        @Valid
        @Size(max = 60)
        public List<AnswerBody> answers;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Actor actor = requireActor(req);
        String[] p = parts(req);
        AssessmentService svc = app().assessments();
        if (p.length == 0) {
            writeJson(res, 200, Map.of("items", svc.list(actor)));
            return;
        }
        if (p.length == 2 && "attempts".equals(p[0])) {
            UUID id = Ids.parseOrNull(p[1]);
            if (id != null) {
                var view = svc.attempt(actor, id);
                if (view.isPresent()) {
                    writeJson(res, 200, view.get());
                    return;
                }
                if (svc.result(actor, id).isPresent()) {
                    throw new ApiException(409, "ATTEMPT_FINISHED", "This attempt has been submitted. Fetch its result.");
                }
            }
            throw notFoundError();
        }
        if (p.length == 3 && "attempts".equals(p[0]) && "result".equals(p[2])) {
            UUID id = Ids.parseOrNull(p[1]);
            if (id != null) {
                var result = svc.result(actor, id);
                if (result.isPresent()) {
                    writeJson(res, 200, result.get());
                    return;
                }
                if (svc.attempt(actor, id).isPresent()) {
                    throw new ApiException(409, "ATTEMPT_IN_PROGRESS", "This attempt hasn't been submitted yet.");
                }
            }
            throw notFoundError();
        }
        if (p.length == 1) {
            writeJson(res, 200, svc.rules(actor, p[0]).orElseThrow(AssessmentApiServlet::notFoundError));
            return;
        }
        notFound();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Actor actor = requireActor(req);
        String[] p = parts(req);
        AssessmentService svc = app().assessments();
        if (p.length == 2 && "start".equals(p[1]) && !"attempts".equals(p[0])) {
            try {
                writeJson(res, 201, Map.of("attemptId", svc.start(actor, p[0])));
            } catch (AssessmentService.RuleException e) {
                throw new ApiException(409, "CANNOT_START", e.getMessage());
            }
            return;
        }
        UUID id = p.length == 3 && "attempts".equals(p[0]) ? Ids.parseOrNull(p[1]) : null;
        if (id == null) {
            throw notFoundError();
        }
        AnswersBody body = readBody(req, AnswersBody.class);
        Map<UUID, Response> answers = answers(body);
        if ("save".equals(p[2])) {
            if (!svc.save(actor, id, answers)) {
                throw new ApiException(409, "NOT_ACCEPTING_ANSWERS", "This attempt is finished or its time is up.");
            }
            writeJson(res, 200, Map.of("saved", answers.size()));
            return;
        }
        if ("submit".equals(p[2])) {
            if (!svc.submit(actor, id, answers)) {
                if (svc.result(actor, id).isEmpty()) {
                    throw notFoundError();
                }
                // Already graded (e.g. auto-submitted): the result stands.
            }
            writeJson(res, 200, svc.result(actor, id).orElseThrow(AssessmentApiServlet::notFoundError));
            return;
        }
        notFound();
    }

    private static Map<UUID, Response> answers(AnswersBody body) {
        Map<UUID, Response> out = new LinkedHashMap<>();
        if (body == null || body.answers == null) {
            return out;
        }
        for (AnswerBody a : body.answers) {
            UUID item = a == null ? null : Ids.parseOrNull(a.itemId);
            if (item == null) {
                continue;
            }
            Set<UUID> options = new HashSet<>();
            if (a.options != null) {
                a.options.forEach(o -> {
                    UUID u = Ids.parseOrNull(o);
                    if (u != null) {
                        options.add(u);
                    }
                });
            }
            out.put(item, new Response(options, a.text == null ? "" : a.text.strip()));
        }
        return out;
    }

    private static String[] parts(HttpServletRequest req) {
        String info = req.getPathInfo();
        return info == null || info.length() < 2 ? new String[0] : info.substring(1).split("/");
    }

    private static ApiException notFoundError() {
        return new ApiException(404, "NOT_FOUND", "Not found.");
    }
}
