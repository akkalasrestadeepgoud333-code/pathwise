package app.pathwise.web.admin;

import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.web.platform.http.PageServlet;
import app.pathwise.web.platform.view.TimeLabels;
import app.pathwise.web.platform.view.ViewModels;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code /admin}: platform overview with live counts and the latest audit entries. */
public class AdminController extends PageServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_ADMIN_AREA);
        req.setAttribute("overview", ViewModels.of(app().admin().overview(actor)));
        req.setAttribute("audit", app().admin().recentAudit(actor, 15).stream().map(e -> {
            java.util.Map<String, Object> view = new java.util.LinkedHashMap<>(ViewModels.of(e));
            view.put("atLabel", TimeLabels.dateTime(e.at(), TimeLabels.DEFAULT_ZONE));
            return view;
        }).toList());
        req.setAttribute("nav", "admin");
        render(req, res, "admin/home");
    }
}
