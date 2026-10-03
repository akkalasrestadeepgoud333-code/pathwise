package app.pathwise.web.learn;

import app.pathwise.core.security.Actor;
import app.pathwise.web.platform.http.ApiException;
import app.pathwise.web.platform.http.ApiServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/**
 * Learning API for the Android app.
 * <ul>
 *   <li>{@code GET /api/v1/subjects}</li>
 *   <li>{@code GET /api/v1/subjects/<subject>/map}</li>
 *   <li>{@code GET /api/v1/subjects/<subject>/topics/<topic>}</li>
 *   <li>{@code GET /api/v1/modules/<module>[/<section>]}</li>
 *   <li>{@code POST /api/v1/modules/<module>/<section>/complete}</li>
 * </ul>
 */
public class LearnApiServlet extends ApiServlet {
    private static final String KEY = "[a-z0-9][a-z0-9-]{0,80}";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Actor actor = requireActor(req);
        String[] p = parts(req);
        if ("/api/v1/subjects".equals(req.getServletPath())) {
            if (p.length == 0) {
                writeJson(res, 200, Map.of("items", app().learn().subjects(actor)));
                return;
            }
            if (p.length == 2 && "map".equals(p[1])) {
                writeJson(res, 200, app().learn().map(actor, p[0]).orElseThrow(LearnApiServlet::notFoundError));
                return;
            }
            if (p.length == 3 && "topics".equals(p[1])) {
                writeJson(res, 200, app().learn().topic(actor, p[0], p[2]).orElseThrow(LearnApiServlet::notFoundError));
                return;
            }
        }
        if ("/api/v1/modules".equals(req.getServletPath()) && (p.length == 1 || p.length == 2)) {
            var view = app().learn().player(actor, p[0], p.length == 2 ? p[1] : null).orElseThrow(LearnApiServlet::notFoundError);
            writeJson(res, 200, view);
            return;
        }
        notFound();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Actor actor = requireActor(req);
        String[] p = parts(req);
        if ("/api/v1/modules".equals(req.getServletPath()) && p.length == 3 && "complete".equals(p[2])) {
            var result = app().learn().completeSection(actor, p[0], p[1]).orElseThrow(LearnApiServlet::notFoundError);
            writeJson(res, 200, result);
            return;
        }
        notFound();
    }

    private static ApiException notFoundError() {
        return new ApiException(404, "NOT_FOUND", "No such subject, topic, module or section.");
    }

    private static String[] parts(HttpServletRequest req) {
        String info = req.getPathInfo();
        if (info == null || info.length() < 2) {
            return new String[0];
        }
        String[] parts = info.substring(1).split("/");
        for (String part : parts) {
            if (!part.matches(KEY)) {
                throw notFoundError();
            }
        }
        return parts;
    }
}
