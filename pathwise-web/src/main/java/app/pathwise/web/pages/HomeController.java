package app.pathwise.web.pages;

import app.pathwise.core.security.Actor;
import app.pathwise.web.platform.http.PageServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code /}: the public landing page, or a redirect home for signed-in people. */
public class HomeController extends PageServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = actor(req);
        if (actor.isAuthenticated()) {
            redirect(req, res, actor.homePath());
            return;
        }
        render(req, res, "public/landing");
    }
}
