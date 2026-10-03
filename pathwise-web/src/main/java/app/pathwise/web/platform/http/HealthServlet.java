package app.pathwise.web.platform.http;

import app.pathwise.web.platform.AppContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code GET /healthz}: liveness plus a database check. Reveals no versions or configuration. */
public class HealthServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        boolean db = AppContext.from(getServletContext()).database().isHealthy();
        res.setStatus(db ? 200 : 503);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"status\":\"" + (db ? "ok" : "degraded") + "\",\"database\":" + db + "}");
    }
}
