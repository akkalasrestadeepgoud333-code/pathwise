package app.pathwise.web.pages;

import app.pathwise.core.security.PlatformAction;
import app.pathwise.web.platform.http.PageServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code /teach}: the teacher home. Rooms arrive in M5. */
public class TeacherController extends PageServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        require(req, PlatformAction.USE_TEACHER_AREA);
        req.setAttribute("nav", "teach");
        render(req, res, "teacher/home");
    }
}
