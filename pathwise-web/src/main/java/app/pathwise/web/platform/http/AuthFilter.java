package app.pathwise.web.platform.http;

import app.pathwise.core.ids.Ids;
import app.pathwise.core.security.Actor;
import app.pathwise.web.platform.AppContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the {@link Actor} for every request. Bearer tokens (Android/API) take precedence; otherwise
 * the web HttpSession's Pathwise session id is checked against the {@code sessions} table on each
 * request, so "sign out other devices" takes effect immediately.
 */
public class AuthFilter extends HttpFilter {
    public static final String ACTOR = "actor";
    public static final String VIA_BEARER = "pw.viaBearer";
    public static final String SESSION_KEY = "pw.sid";

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (req.getRequestURI().startsWith(req.getContextPath() + "/static/")) {
            chain.doFilter(req, res); // static assets never need an identity lookup
            return;
        }
        AppContext app = AppContext.from(getServletContext());
        Actor actor = Actor.anonymous();
        String authorization = req.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            req.setAttribute(VIA_BEARER, Boolean.TRUE);
            actor = app.identity().resolveBearer(authorization.substring(7).trim()).orElse(Actor.anonymous());
        } else {
            HttpSession session = req.getSession(false);
            if (session != null && session.getAttribute(SESSION_KEY) instanceof String sid) {
                UUID id = Ids.parseOrNull(sid);
                Optional<Actor> resolved = id == null ? Optional.empty() : app.identity().resolveSession(id);
                if (resolved.isPresent()) {
                    actor = resolved.get();
                } else {
                    // Revoked or expired: drop the Pathwise login but keep the HttpSession (CSRF token).
                    session.removeAttribute(SESSION_KEY);
                }
            }
        }
        req.setAttribute(ACTOR, actor);
        chain.doFilter(req, res);
    }

    public static Actor actor(HttpServletRequest req) {
        Object a = req.getAttribute(ACTOR);
        return a instanceof Actor actor ? actor : Actor.anonymous();
    }

    public static boolean viaBearer(HttpServletRequest req) {
        return Boolean.TRUE.equals(req.getAttribute(VIA_BEARER));
    }
}
