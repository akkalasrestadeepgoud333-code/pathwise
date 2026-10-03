package app.pathwise.web.platform.http;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Target of the container's {@code <error-page>} mappings (404s, uncaught exceptions). Produces the
 * JSON envelope for API paths and the friendly error page otherwise; never a stack trace.
 */
public class ErrorServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(ErrorServlet.class);

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Object statusAttr = req.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusAttr instanceof Integer i ? i : 500;
        Throwable error = (Throwable) req.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (error != null) {
            log.error("Unhandled error for {}", req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI), error);
        }
        String originalUri = String.valueOf(req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));
        boolean api = originalUri.startsWith(req.getContextPath() + "/api/");
        String code;
        String message;
        switch (status) {
            case 404 -> { code = "NOT_FOUND"; message = "We couldn't find that page."; }
            case 403 -> { code = "FORBIDDEN"; message = "You don't have access to this page."; }
            case 405 -> { code = "METHOD_NOT_ALLOWED"; message = "That action isn't supported here."; }
            case 400 -> { code = "BAD_REQUEST"; message = "That request wasn't understood."; }
            default -> { code = status >= 500 ? "INTERNAL" : "ERROR"; message = "Something went wrong on our side. Please try again."; }
        }
        if (api) {
            res.setStatus(status);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message.replace("\"", "'")
                    + "\",\"requestId\":\"" + RequestIdFilter.of(req) + "\"}}");
            return;
        }
        res.setStatus(status);
        res.setContentType("text/html;charset=UTF-8");
        req.setAttribute("errorStatus", status);
        req.setAttribute("errorCode", code);
        req.setAttribute("errorMessage", message);
        req.setAttribute("requestId", RequestIdFilter.of(req));
        req.getRequestDispatcher("/WEB-INF/views/errors/error.jsp").forward(req, res);
    }
}
