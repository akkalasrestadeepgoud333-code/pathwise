package app.pathwise.core.security;

/** The request has no valid session (HTTP 401, or a redirect to the sign-in page for HTML). */
public class NotAuthenticatedException extends RuntimeException {
    public NotAuthenticatedException() {
        super("Sign-in required");
    }
}
