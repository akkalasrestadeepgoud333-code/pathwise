package app.pathwise.web.platform.http;

import app.pathwise.core.security.Tokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Synchronizer-token CSRF protection. One token per HttpSession, rotated on login. */
public final class Csrf {
    public static final String SESSION_KEY = "pw.csrf";
    public static final String PARAM = "_csrf";
    public static final String HEADER = "X-CSRF-Token";

    private Csrf() {}

    /** Returns the session's token, creating the session and token if needed. Used by pages that render forms. */
    public static String token(HttpServletRequest req) {
        HttpSession session = req.getSession(true);
        Object existing = session.getAttribute(SESSION_KEY);
        if (existing instanceof String s) {
            return s;
        }
        String token = Tokens.newToken();
        session.setAttribute(SESSION_KEY, token);
        return token;
    }

    public static void rotate(HttpServletRequest req) {
        req.getSession(true).setAttribute(SESSION_KEY, Tokens.newToken());
    }

    static boolean isValid(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null || !(session.getAttribute(SESSION_KEY) instanceof String expected)) {
            return false;
        }
        String supplied = req.getHeader(HEADER);
        if (supplied == null) {
            supplied = req.getParameter(PARAM);
        }
        return Tokens.constantTimeEquals(expected, supplied);
    }
}
