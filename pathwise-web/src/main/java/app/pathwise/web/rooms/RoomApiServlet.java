package app.pathwise.web.rooms;

import app.pathwise.core.ids.Ids;
import app.pathwise.core.security.Actor;
import app.pathwise.web.platform.http.ApiException;
import app.pathwise.web.platform.http.ApiServlet;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

/**
 * Student-side rooms API (for the Android app). Teaching stays on the web for now.
 * <ul>
 *   <li>{@code GET /api/v1/rooms}: my rooms</li>
 *   <li>{@code POST /api/v1/rooms/join} {"code": "K7M3-QX9P"}</li>
 *   <li>{@code GET /api/v1/rooms/<id>}: the room as I see it</li>
 * </ul>
 */
public class RoomApiServlet extends ApiServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Actor actor = requireActor(req);
        String sub = req.getPathInfo();
        if (sub == null || "/".equals(sub)) {
            writeJson(res, 200, app().rooms().myRooms(actor));
            return;
        }
        UUID id = Ids.parseOrNull(sub.substring(1));
        if (id == null) {
            throw new ApiException(404, "NOT_FOUND", "Not found.");
        }
        try {
            writeJson(res, 200, app().rooms().studentView(actor, id));
        } catch (RoomService.RoomNotFoundException e) {
            throw new ApiException(404, "NOT_FOUND", "Not found.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Actor actor = requireActor(req);
        if (!"/join".equals(req.getPathInfo())) {
            notFound();
            return;
        }
        JsonNode body = readBody(req, JsonNode.class);
        try {
            writeJson(res, 200, app().rooms().joinByCode(actor, body.path("code").asText("")));
        } catch (RoomService.RoomRuleException e) {
            throw new ApiException(409, "CANNOT_JOIN", e.getMessage());
        }
    }
}
