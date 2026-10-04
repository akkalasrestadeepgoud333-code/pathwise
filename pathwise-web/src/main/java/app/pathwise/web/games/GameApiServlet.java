package app.pathwise.web.games;

import app.pathwise.core.games.Game.GameAction;
import app.pathwise.core.ids.Ids;
import app.pathwise.core.security.Actor;
import app.pathwise.web.platform.Json;
import app.pathwise.web.platform.http.ApiException;
import app.pathwise.web.platform.http.ApiServlet;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Games API (for the Android app, which hosts the same JS games in a WebView).
 * <ul>
 *   <li>{@code GET /api/v1/games}, {@code GET /api/v1/games/<key>}, {@code POST /api/v1/games/<key>/start} {"relaxed": false}</li>
 *   <li>{@code GET /api/v1/games/attempts/<id>} (the puzzle), {@code POST .../finish} {"actions": [...]}, {@code GET .../result}</li>
 * </ul>
 */
public class GameApiServlet extends ApiServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Actor actor = requireActor(req);
        String[] p = parts(req);
        GameService svc = app().games();
        if (p.length == 0) {
            writeJson(res, 200, Map.of("items", svc.list(actor)));
            return;
        }
        if (p.length == 2 && "attempts".equals(p[0])) {
            UUID id = Ids.parseOrNull(p[1]);
            var view = id == null ? java.util.Optional.<GameService.PlayView>empty() : svc.play(actor, id);
            if (view.isEmpty()) {
                throw notFoundError();
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("attemptId", view.get().attemptId());
            body.put("template", view.get().template());
            body.put("title", view.get().title());
            body.put("relaxed", view.get().relaxed());
            body.put("minGapMillis", view.get().minGapMillis());
            body.put("puzzle", Json.mapper().readTree(view.get().puzzleJson()));
            writeJson(res, 200, body);
            return;
        }
        if (p.length == 3 && "attempts".equals(p[0]) && "result".equals(p[2])) {
            UUID id = Ids.parseOrNull(p[1]);
            writeJson(res, 200, (id == null ? java.util.Optional.<GameService.Result>empty() : svc.result(actor, id))
                    .orElseThrow(GameApiServlet::notFoundError));
            return;
        }
        if (p.length == 1) {
            writeJson(res, 200, svc.intro(actor, p[0]).orElseThrow(GameApiServlet::notFoundError));
            return;
        }
        notFound();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Actor actor = requireActor(req);
        String[] p = parts(req);
        GameService svc = app().games();
        if (p.length == 2 && "start".equals(p[1]) && !"attempts".equals(p[0])) {
            JsonNode body = req.getContentType() == null ? null : readBody(req, JsonNode.class); // body optional
            boolean relaxed = body != null && body.path("relaxed").asBoolean(false);
            try {
                writeJson(res, 201, Map.of("attemptId", svc.start(actor, p[0], relaxed)));
            } catch (GameService.NotFoundException e) {
                throw notFoundError();
            }
            return;
        }
        if (p.length == 3 && "attempts".equals(p[0]) && "finish".equals(p[2])) {
            UUID id = Ids.parseOrNull(p[1]);
            JsonNode body = readBody(req, JsonNode.class);
            List<GameAction> actions = GameController.parseActions(body == null ? null : body.get("actions"));
            if (id == null) {
                throw notFoundError();
            }
            if (actions == null) {
                throw new ApiException(400, "VALIDATION_FAILED", "actions must be a list of {type, a, b, t} (at most 2000)");
            }
            try {
                svc.finish(actor, id, actions);
            } catch (GameService.NotFoundException e) {
                throw notFoundError();
            }
            writeJson(res, 200, svc.result(actor, id).orElseThrow(GameApiServlet::notFoundError));
            return;
        }
        notFound();
    }

    private static String[] parts(HttpServletRequest req) {
        String info = req.getPathInfo();
        return info == null || info.length() < 2 ? new String[0] : info.substring(1).split("/");
    }

    private static ApiException notFoundError() {
        return new ApiException(404, "NOT_FOUND", "Not found.");
    }
}
