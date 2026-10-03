package app.pathwise.web.platform.http;

import java.util.Map;

/** An expected API failure with an HTTP status and a stable machine-readable code. */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final Map<String, String> details;

    public ApiException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(int status, String code, String message, Map<String, String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public int status() { return status; }
    public String code() { return code; }
    public Map<String, String> details() { return details; }
}
