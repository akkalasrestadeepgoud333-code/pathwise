package app.pathwise.web.platform.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Gives every request a short id: in logs (MDC), in the X-Request-Id header and on error pages. */
public class RequestIdFilter extends HttpFilter {
    public static final String ATTRIBUTE = "requestId";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        String id = HexFormat.of().formatHex(bytes);
        req.setAttribute(ATTRIBUTE, id);
        res.setHeader("X-Request-Id", id);
        MDC.put(ATTRIBUTE, id);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove(ATTRIBUTE);
        }
    }

    public static String of(HttpServletRequest req) {
        Object id = req.getAttribute(ATTRIBUTE);
        return id == null ? "-" : id.toString();
    }
}
