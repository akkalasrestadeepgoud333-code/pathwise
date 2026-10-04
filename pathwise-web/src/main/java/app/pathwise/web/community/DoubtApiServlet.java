package app.pathwise.web.community;

import app.pathwise.core.ids.Ids;
import app.pathwise.core.security.Actor;
import app.pathwise.web.platform.http.ApiException;
import app.pathwise.web.platform.http.ApiServlet;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Doubts API. Responses use the same id-free view types as the pages, so anonymous authors stay anonymous.
 * <ul>
 *   <li>{@code GET /api/v1/doubts[?q=]}, {@code GET /api/v1/doubts/<id>}</li>
 *   <li>{@code POST /api/v1/doubts} {"roomId"?, "category", "title", "body", "topic"?, "showName"?}</li>
 *   <li>{@code POST /api/v1/doubts/<id>/answers} {"body", "showName"?}</li>
 * </ul>
 */
public class DoubtApiServlet extends ApiServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Actor actor = requireActor(req);
        String sub = req.getPathInfo();
        try {
            if (sub == null || "/".equals(sub)) {
                writeJson(res, 200, app().doubts().lists(actor, req.getParameter("q")));
                return;
            }
            UUID id = Ids.parseOrNull(sub.substring(1));
            if (id == null) {
                throw new DoubtService.DoubtNotFoundException();
            }
            writeJson(res, 200, app().doubts().view(actor, id));
        } catch (DoubtService.DoubtNotFoundException e) {
            throw new ApiException(404, "NOT_FOUND", "Not found.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Actor actor = requireActor(req);
        String sub = req.getPathInfo();
        JsonNode body = readBody(req, JsonNode.class);
        try {
            if (sub == null || "/".equals(sub)) {
                UUID id = app().doubts().ask(actor, Ids.parseOrNull(body.path("roomId").asText(null)), body.path("category").asText(""),
                        body.path("title").asText(""), body.path("body").asText(""), body.path("topic").asText(null),
                        body.path("showName").asBoolean(false));
                writeJson(res, 201, Map.of("id", id));
                return;
            }
            String[] p = sub.substring(1).split("/");
            UUID id = p.length == 2 && "answers".equals(p[1]) ? Ids.parseOrNull(p[0]) : null;
            if (id == null) {
                throw new DoubtService.DoubtNotFoundException();
            }
            app().doubts().answer(actor, id, body.path("body").asText(""), body.path("showName").asBoolean(false));
            writeJson(res, 201, app().doubts().view(actor, id));
        } catch (DoubtService.DoubtNotFoundException e) {
            throw new ApiException(404, "NOT_FOUND", "Not found.");
        } catch (DoubtService.DoubtRuleException e) {
            throw new ApiException(400, "VALIDATION_FAILED", e.getMessage());
        }
    }
}
