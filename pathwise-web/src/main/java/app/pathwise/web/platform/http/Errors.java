package app.pathwise.web.platform.http;

import app.pathwise.web.platform.Json;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** One way to report errors: a JSON envelope for the API, the error page for HTML. */
public final class Errors {
    private Errors() {}

    public static boolean isApi(HttpServletRequest req) {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        return path.startsWith("/api/");
    }

    public static void send(HttpServletRequest req, HttpServletResponse res, int status, String code, String message)
            throws IOException, ServletException {
        send(req, res, status, code, message, null);
    }

    public static void send(HttpServletRequest req, HttpServletResponse res, int status, String code, String message,
                            Map<String, String> details) throws IOException, ServletException {
        if (res.isCommitted()) {
            return;
        }
        res.resetBuffer();
        res.setStatus(status);
        if (isApi(req)) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", code);
            error.put("message", message);
            if (details != null && !details.isEmpty()) {
                error.put("details", details);
            }
            error.put("requestId", RequestIdFilter.of(req));
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write(Json.write(Map.of("error", error)));
            return;
        }
        req.setAttribute("errorStatus", status);
        req.setAttribute("errorCode", code);
        req.setAttribute("errorMessage", message);
        req.setAttribute("requestId", RequestIdFilter.of(req));
        req.getRequestDispatcher("/WEB-INF/views/errors/error.jsp").forward(req, res);
    }
}
