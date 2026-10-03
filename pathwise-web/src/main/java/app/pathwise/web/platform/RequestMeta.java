package app.pathwise.web.platform;

import app.pathwise.core.security.Tokens;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Request facts that services may record. The client IP is stored only as a keyed hash, so audit and
 * session records never contain raw IP addresses.
 */
public record RequestMeta(String userAgent, String ipHash, String rateKeyIp) {

    public static RequestMeta of(HttpServletRequest req, AppConfig config) {
        String ip = clientIp(req, config.trustProxyHeaders());
        String hash = Tokens.sha256Hex(config.secret() + "|ip|" + ip).substring(0, 32);
        return new RequestMeta(req.getHeader("User-Agent"), hash, hash);
    }

    /** For CLI-initiated actions (no HTTP request). */
    public static RequestMeta system() {
        return new RequestMeta("pathwise-cli", null, "cli");
    }

    private static String clientIp(HttpServletRequest req, boolean trustProxy) {
        if (trustProxy) {
            // Cloudflare Tunnel sets CF-Connecting-IP; generic proxies set X-Forwarded-For.
            String cf = req.getHeader("CF-Connecting-IP");
            if (cf != null && !cf.isBlank()) {
                return cf.trim();
            }
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return req.getRemoteAddr();
    }
}
