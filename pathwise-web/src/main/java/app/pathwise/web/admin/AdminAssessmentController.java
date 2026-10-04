package app.pathwise.web.admin;

import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.web.assessment.AssessmentService;
import app.pathwise.web.platform.http.PageServlet;
import app.pathwise.web.platform.view.ViewModels;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Optional;

/** {@code /admin/assessments}: published assessments and, per assessment, item analysis. */
public class AdminAssessmentController extends PageServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_ADMIN_AREA);
        req.setAttribute("nav", "admin-assessments");
        String info = req.getPathInfo();
        if (info == null || info.equals("/")) {
            req.setAttribute("rows", ViewModels.list(app().assessments().adminList(actor)));
            render(req, res, "admin/assessments");
            return;
        }
        String key = info.substring(1);
        Optional<AssessmentService.Analysis> analysis = key.matches("[a-z0-9][a-z0-9-]{1,80}")
                ? app().assessments().analysis(actor, key) : Optional.empty();
        if (analysis.isEmpty()) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        req.setAttribute("analysis", ViewModels.of(analysis.get()));
        render(req, res, "admin/assessment-analysis");
    }
}
