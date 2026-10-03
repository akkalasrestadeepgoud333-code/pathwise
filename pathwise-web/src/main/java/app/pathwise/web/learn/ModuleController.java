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
        req.setAttribute("justCompleted", "1".equals(req.getParameter("completed")));
        req.setAttribute("nav", "learn");
        render(req, res, "learn/module");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        String[] parts = parts(req);
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
