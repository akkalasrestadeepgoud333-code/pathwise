package app.pathwise.app.testing;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A minimal cookie-keeping browser for HTTP tests. Does not follow redirects, so tests can assert on
 * them. Remembers the CSRF token from the last HTML page, like a real form would.
 */
public final class Browser {
    private static final Pattern CSRF_META = Pattern.compile("<meta name=\"csrf-token\" content=\"([^\"]*)\">");

    private final String base;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient client;
    private String csrf = "";
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0) Chrome/130.0 Safari/537.36";

    public Browser(String base) {
        this.base = base;
        this.client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public Browser userAgent(String ua) {
        this.userAgent = ua;
        return this;
    }

    public HttpResponse<String> get(String path) {
        HttpResponse<String> r = send(HttpRequest.newBuilder(URI.create(base + path)).GET());
        Matcher m = CSRF_META.matcher(r.body());
        if (m.find()) {
            csrf = m.group(1);
        }
        return r;
    }

    /** Posts a form including the current CSRF token (unless the map already sets _csrf). */
    public HttpResponse<String> post(String path, Map<String, String> form) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("_csrf", csrf);
        body.putAll(form);
        return postRaw(path, body);
    }

    /** Posts repeated fields (e.g. several {@code item} values) with the current CSRF token. */
    public HttpResponse<String> postPairs(String path, List<Map.Entry<String, String>> pairs) {
        List<Map.Entry<String, String>> all = new ArrayList<>();
        all.add(Map.entry("_csrf", csrf));
        all.addAll(pairs);
        String encoded = all.stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encoded)));
    }

    public HttpResponse<String> postRaw(String path, Map<String, String> form) {
        String encoded = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encoded)));
    }

    /** Signs up through the real form and returns the redirect response. */
    public HttpResponse<String> register(String name, String email, String password, String role) {
        get("/register");
        return followUp(post("/register", Map.of("displayName", name, "email", email, "password", password, "role", role)));
    }

    public HttpResponse<String> login(String email, String password) {
        get("/login");
        return followUp(post("/login", Map.of("email", email, "password", password)));
    }

    /** After a successful sign-in redirect, load the next page so the rotated CSRF token is known. */
    private HttpResponse<String> followUp(HttpResponse<String> r) {
        if (r.statusCode() == 303 && location(r) != null && location(r).startsWith("/")) {
            get(location(r));
        }
        return r;
    }

    /** The CSRF token from the last page, for requests a page's script would send (header {@code X-CSRF-Token}). */
    public String csrf() {
        return csrf;
    }

    public String sessionCookie() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("PW_SESSION")).map(HttpCookie::getValue).findFirst().orElse(null);
    }

    public HttpResponse<String> send(HttpRequest.Builder builder) {
        try {
            return client.send(builder.header("User-Agent", userAgent).build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public static String location(HttpResponse<?> r) {
        return r.headers().firstValue("Location").orElse(null);
    }
}
