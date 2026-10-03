package app.pathwise.web.platform.http;

import app.pathwise.core.security.AccessDeniedException;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.NotAuthenticatedException;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.core.security.PlatformPolicy;
import app.pathwise.web.platform.AppContext;
import app.pathwise.web.platform.RequestMeta;
import app.pathwise.web.platform.view.CurrentUser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Base class for HTML controllers (MVC: parse request → call service → put view beans in request
 * scope → forward to JSP, or redirect after POST). Translates domain exceptions into the right response.
 */
public abstract class PageServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(PageServlet.class);
    private static final String FLASH = "pw.flash";

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            super.service(req, res);
        } catch (NotAuthenticatedException e) {
            if ("GET".equals(req.getMethod())) {
                String next = req.getRequestURI().substring(req.getContextPath().length());
                if (req.getQueryString() != null) {
                    next += "?" + req.getQueryString();
                }
                redirect(req, res, "/login?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8));
            } else {
                redirect(req, res, "/login");
            }
        } catch (AccessDeniedException e) {
            Errors.send(req, res, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                    "You don't have access to this page.");
        } catch (RuntimeException e) {
            log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), e);
            Errors.send(req, res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "INTERNAL",
                    "Something went wrong on our side. Please try again.");
        }
    }

    protected AppContext app() {
        return AppContext.from(getServletContext());
    }

    protected Actor actor(HttpServletRequest req) {
        return AuthFilter.actor(req);
    }

    protected RequestMeta meta(HttpServletRequest req) {
        return RequestMeta.of(req, app().config());
    }

    /** Ensures the actor may perform {@code action}; otherwise redirects to sign-in or shows 403. */
    protected Actor require(HttpServletRequest req, PlatformAction action) {
        Actor actor = actor(req);
        PlatformPolicy.require(actor, action);
        return actor;
    }

    /** Forwards to {@code /WEB-INF/views/<view>.jsp} with the shared view attributes set. */
    protected void render(HttpServletRequest req, HttpServletResponse res, String view) throws ServletException, IOException {
        Actor actor = actor(req);
        req.setAttribute("csrfToken", Csrf.token(req));
        if (actor.isAuthenticated()) {
            req.setAttribute("currentUser", CurrentUser.of(actor, app().identity().theme(actor.userId())));
        }
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute(FLASH) != null) {
            req.setAttribute("flash", session.getAttribute(FLASH));
            session.removeAttribute(FLASH);
        }
        res.setContentType("text/html;charset=UTF-8");
        req.getRequestDispatcher("/WEB-INF/views/" + view + ".jsp").forward(req, res);
    }

    /** Context-path-aware redirect (303 so the browser follows with GET after a POST). */
    protected void redirect(HttpServletRequest req, HttpServletResponse res, String path) throws IOException {
        res.setStatus(HttpServletResponse.SC_SEE_OTHER);
        res.setHeader("Location", req.getContextPath() + path);
    }

    /** A one-time message shown on the next rendered page. {@code kind} is success, info or error. */
    protected void flash(HttpServletRequest req, String kind, String message) {
        req.getSession(true).setAttribute(FLASH, Map.of("kind", kind, "message", message));
    }

    /** Only same-site relative paths are allowed as post-login destinations (prevents open redirects). */
    protected static String safeNext(String next, String fallback) {
        if (next == null || next.isBlank() || !next.startsWith("/") || next.startsWith("//")
                || next.contains("\\") || next.contains("\r") || next.contains("\n") || next.length() > 500) {
            return fallback;
        }
        return next;
    }

    protected static String param(HttpServletRequest req, String name) {
        String value = req.getParameter(name);
        return value == null ? "" : value;
    }
}
