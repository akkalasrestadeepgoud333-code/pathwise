package app.pathwise.web.platform.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Set;

/**
 * Rejects state-changing requests that do not carry the session's CSRF token. Requests authenticated
 * with a bearer token are exempt: browsers never attach those automatically, so they cannot be forged
 * cross-site.
 */
public class CsrfFilter extends HttpFilter {
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        // Anonymous JSON API calls (API sign-up/sign-in) carry no ambient credentials and never set
        // cookies, so a forged cross-site request gains nothing. Cookie-authenticated API calls still need the token.
        boolean anonymousApiCall = Errors.isApi(req) && !AuthFilter.actor(req).isAuthenticated();
        if (SAFE.contains(req.getMethod()) || AuthFilter.viaBearer(req) || anonymousApiCall || Csrf.isValid(req)) {
            chain.doFilter(req, res);
            return;
        }
        Errors.send(req, res, HttpServletResponse.SC_FORBIDDEN, "CSRF_TOKEN_INVALID",
                "This form expired. Reload the page and try again.");
    }
}
