package app.pathwise.core.security;

/** The actor is signed in but not allowed to perform the action (HTTP 403). */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String action) {
        super("Not allowed: " + action);
    }
}
