package app.pathwise.web.admin;

import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.web.content.ContentPackService;
import app.pathwise.web.platform.http.PageServlet;
import app.pathwise.web.platform.view.TimeLabels;
import app.pathwise.web.platform.view.ViewModels;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** {@code /admin/content}: bundled content packs, their validation status, and import. */
public class AdminContentController extends PageServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_ADMIN_AREA);
        req.setAttribute("packs", app().contentPacks().statuses(actor).stream().map(p -> {
            Map<String, Object> view = new LinkedHashMap<>(ViewModels.of(p));
            view.put("importedLabel", TimeLabels.dateTime(p.importedAt(), TimeLabels.DEFAULT_ZONE));
            return view;
        }).toList());
        req.setAttribute("nav", "admin-content");
        render(req, res, "admin/content");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_ADMIN_AREA);
        String key = param(req, "pack");
        if (!key.matches("[a-z0-9][a-z0-9-]{1,80}")) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        ContentPackService.ImportResult result = app().contentPacks().importPack(actor, key, "1".equals(req.getParameter("force")));
        switch (result.outcome()) {
            case IMPORTED -> flash(req, "success", "Imported " + key + ": " + result.topics() + " topics, "
                    + result.modules() + " modules, " + result.sections() + " sections.");
            case UNCHANGED -> flash(req, "info", key + " is already up to date.");
            case INVALID -> flash(req, "error", key + " was not imported: " + String.join("; ",
                    result.errors().subList(0, Math.min(3, result.errors().size()))));
        }
        redirect(req, res, "/admin/content");
    }
}
