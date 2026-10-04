package app.pathwise.web.games;

import app.pathwise.core.games.Game.GameAction;
import app.pathwise.core.ids.Ids;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.web.platform.Json;
import app.pathwise.web.platform.http.PageServlet;
import app.pathwise.web.platform.view.ViewModels;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Game pages.
 * <ul>
 *   <li>{@code GET /app/games}: gallery; {@code GET /app/games/<key>}: intro; {@code POST /app/games/<key>/start}</li>
 *   <li>{@code GET /app/games/play/<id>}: the game (needs JavaScript); {@code POST .../finish} with the action log as JSON</li>
 *   <li>{@code GET /app/games/result/<id>}</li>
 * </ul>
 */
public class GameController extends PageServlet {
    static final int MAX_BODY = 256 * 1024;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        req.setAttribute("nav", "practice");
        String[] p = parts(req);
        GameService svc = app().games();
        if (p.length == 0) {
            req.setAttribute("cards", ViewModels.list(svc.list(actor)));
            render(req, res, "games/index");
            return;
        }
        if (p.length == 2 && "play".equals(p[0])) {
            UUID id = Ids.parseOrNull(p[1]);
            var view = id == null ? java.util.Optional.<GameService.PlayView>empty() : svc.play(actor, id);
            if (view.isEmpty()) {
                if (id != null && svc.result(actor, id).isPresent()) {
                    redirect(req, res, "/app/games/result/" + id);
                } else {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                }
                return;
            }
            req.setAttribute("game", ViewModels.of(view.get()));
            render(req, res, "games/play");
            return;
        }
        if (p.length == 2 && "result".equals(p[0])) {
            UUID id = Ids.parseOrNull(p[1]);
            var result = id == null ? java.util.Optional.<GameService.Result>empty() : svc.result(actor, id);
            if (result.isEmpty()) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            req.setAttribute("result", ViewModels.of(result.get()));
            render(req, res, "games/result");
            return;
        }
        if (p.length == 1) {
            var intro = svc.intro(actor, p[0]);
            if (intro.isEmpty()) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            req.setAttribute("intro", ViewModels.of(intro.get()));
            render(req, res, "games/intro");
            return;
        }
        res.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        String[] p = parts(req);
        GameService svc = app().games();
        if (p.length == 2 && "start".equals(p[1]) && !"play".equals(p[0])) {
            try {
                UUID id = svc.start(actor, p[0], "1".equals(req.getParameter("relaxed")));
                redirect(req, res, "/app/games/play/" + id);
            } catch (GameService.NotFoundException e) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
            return;
        }
        if (p.length == 3 && "play".equals(p[0]) && "finish".equals(p[2])) {
            UUID id = Ids.parseOrNull(p[1]);
            List<GameAction> actions = readActions(req);
            if (id == null || actions == null) {
                res.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            try {
                svc.finish(actor, id, actions);
            } catch (GameService.NotFoundException e) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            res.setContentType("application/json");
            res.getWriter().write(Json.write(Map.of("redirect", req.getContextPath() + "/app/games/result/" + id)));
            return;
        }
        res.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    /** {@code {"actions": [{"type", "a", "b", "t"}, ...]}}, or null if malformed or too large. */
    static List<GameAction> readActions(HttpServletRequest req) throws IOException {
        if (req.getContentLength() > MAX_BODY) {
            return null;
        }
        try (InputStream in = req.getInputStream()) {
            byte[] body = in.readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY) {
                return null;
            }
            JsonNode root = Json.mapper().readTree(body);
            return parseActions(root == null ? null : root.get("actions"));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
    }

    static List<GameAction> parseActions(JsonNode list) {
        if (list == null || !list.isArray() || list.size() > 2000) {
            return null;
        }
        List<GameAction> out = new ArrayList<>();
        for (JsonNode a : list) {
            if (!a.isObject() || !a.path("type").isTextual() || !a.path("t").isNumber()) {
                return null;
            }
            out.add(new GameAction(clip(a.path("type").asText()), a.hasNonNull("a") ? clip(a.get("a").asText()) : null,
                    a.hasNonNull("b") ? clip(a.get("b").asText()) : null, a.get("t").asLong()));
        }
        return out;
    }

    private static String clip(String s) {
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    private static String[] parts(HttpServletRequest req) {
        String info = req.getPathInfo();
        return info == null || info.length() < 2 ? new String[0] : info.substring(1).split("/");
    }
}
