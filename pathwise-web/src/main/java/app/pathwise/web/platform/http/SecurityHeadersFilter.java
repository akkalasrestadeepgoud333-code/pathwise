package app.pathwise.web.platform.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Strict browser security headers on every response. The CSP allows no inline scripts and no
 * third-party origins: every script, style and font is served by Pathwise itself.
 */
public class SecurityHeadersFilter extends HttpFilter {
    static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self' data:",
            "font-src 'self'",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    private final boolean hsts;

    public SecurityHeadersFilter(boolean hsts) {
        this.hsts = hsts;
    }

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        res.setHeader("Content-Security-Policy", CSP);
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
        if (hsts) {
            res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (path.startsWith("/static/")) {
            res.setHeader("Cache-Control", "public, max-age=3600");
        } else {
            // Pages can contain personal data: never let shared caches keep them.
            res.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(req, res);
    }
}
