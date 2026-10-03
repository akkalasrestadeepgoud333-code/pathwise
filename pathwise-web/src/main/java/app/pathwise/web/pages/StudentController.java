package app.pathwise.web.pages;

import app.pathwise.core.security.PlatformAction;
import app.pathwise.web.platform.http.PageServlet;
import app.pathwise.web.platform.view.Greeting;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.ZoneId;

/** {@code /app/today}: the student home. Learning content arrives with the catalogue (M1) and engine (M2). */
public class StudentController extends PageServlet {
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        var actor = require(req, PlatformAction.USE_STUDENT_AREA);
        if (!"/app/today".equals(req.getServletPath())) {
            redirect(req, res, "/app/today");
            return;
        }
        req.setAttribute("greeting", Greeting.forNow(DEFAULT_ZONE));
        req.setAttribute("today", app.pathwise.web.platform.view.ViewModels.of(app().learn().today(actor)));
        req.setAttribute("nav", "today");
        render(req, res, "student/today");
    }
}
