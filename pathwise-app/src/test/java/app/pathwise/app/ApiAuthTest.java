package app.pathwise.app;

import app.pathwise.app.testing.Browser;
import app.pathwise.app.testing.TestServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The JSON API used by the Android app: bearer tokens, error envelope, validation. */
class ApiAuthTest {
    private static final Pattern TOKEN = Pattern.compile("\"token\":\"([^\"]+)\"");
    private final Browser http = new Browser(TestServer.baseUrl());

    private HttpResponse<String> json(String method, String path, String body, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(TestServer.baseUrl() + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json");
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return http.send(b);
    }

    private static String token(HttpResponse<String> r) {
        Matcher m = TOKEN.matcher(r.body());
        assertThat(m.find()).as("token in " + r.body()).isTrue();
        return m.group(1);
    }

    private static String email() {
        return "api-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    @Test
    void registerLoginMeAndLogout() {
        String e = email();
        HttpResponse<String> reg = json("POST", "/api/v1/auth/register",
                "{\"displayName\":\"Api Student\",\"email\":\"" + e + "\",\"password\":\"trees grow upside down\"}", null);
        assertThat(reg.statusCode()).isEqualTo(201);
        assertThat(reg.body()).contains("\"roles\":[\"student\"]").doesNotContain("password");

        HttpResponse<String> login = json("POST", "/api/v1/auth/login",
                "{\"email\":\"" + e.toUpperCase() + "\",\"password\":\"trees grow upside down\"}", null);
        assertThat(login.statusCode()).isEqualTo(200);
        String token = token(login);

        HttpResponse<String> me = json("GET", "/api/v1/me", null, token);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"displayName\":\"Api Student\"").contains("\"email\":\"" + e + "\"");

        // Bearer requests need no CSRF token: browsers never attach bearer tokens automatically.
        HttpResponse<String> sessions = json("GET", "/api/v1/auth/sessions", null, token);
        assertThat(sessions.body()).contains("\"current\":true").contains("\"client\":\"android\"");

        assertThat(json("POST", "/api/v1/auth/logout", null, token).statusCode()).isEqualTo(204);
        HttpResponse<String> after = json("GET", "/api/v1/me", null, token);
        assertThat(after.statusCode()).isEqualTo(401);
        assertThat(after.body()).contains("\"code\":\"UNAUTHENTICATED\"").contains("\"requestId\"");
    }

    @Test
    void validationAndContentTypeErrorsUseTheEnvelope() {
        HttpResponse<String> invalid = json("POST", "/api/v1/auth/register",
                "{\"displayName\":\"A\",\"email\":\"nope\",\"password\":\"x\"}", null);
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.body()).contains("\"code\":\"VALIDATION_FAILED\"").contains("\"displayName\"").contains("\"email\"");

        HttpResponse<String> weak = json("POST", "/api/v1/auth/register",
                "{\"displayName\":\"Weak Pw\",\"email\":\"" + email() + "\",\"password\":\"1234567890\"}", null);
        assertThat(weak.statusCode()).isEqualTo(400);
        assertThat(weak.body()).contains("too common");

        assertThat(json("POST", "/api/v1/auth/login", "{not json", null).body()).contains("INVALID_JSON");
        assertThat(json("POST", "/api/v1/auth/login",
                "{\"email\":\"a@b.co\",\"password\":\"x\",\"isAdmin\":true}", null).body())
                .as("unknown properties are rejected").contains("INVALID_JSON");

        HttpResponse<String> form = http.send(HttpRequest.newBuilder(URI.create(TestServer.baseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("email=a&password=b")));
        assertThat(form.statusCode()).isEqualTo(415);
    }

    @Test
    void duplicateEmailIsAConflictAndBadCredentialsAre401() {
        String e = email();
        String body = "{\"displayName\":\"Dup\",\"email\":\"" + e + "\",\"password\":\"trees grow upside down\"}";
        assertThat(json("POST", "/api/v1/auth/register", body, null).statusCode()).isEqualTo(201);
        HttpResponse<String> dup = json("POST", "/api/v1/auth/register", body, null);
        assertThat(dup.statusCode()).isEqualTo(409);
        assertThat(dup.body()).contains("EMAIL_TAKEN");

        HttpResponse<String> bad = json("POST", "/api/v1/auth/login",
                "{\"email\":\"" + e + "\",\"password\":\"wrong password here\"}", null);
        assertThat(bad.statusCode()).isEqualTo(401);
        assertThat(bad.body()).contains("INVALID_CREDENTIALS");
    }

    @Test
    void usersCannotRevokeOtherPeoplesSessions() {
        String a = token(json("POST", "/api/v1/auth/register",
                "{\"displayName\":\"Alice\",\"email\":\"" + email() + "\",\"password\":\"trees grow upside down\"}", null));
        HttpResponse<String> bobReg = json("POST", "/api/v1/auth/register",
                "{\"displayName\":\"Bob\",\"email\":\"" + email() + "\",\"password\":\"trees grow upside down\"}", null);
        String b = token(bobReg);
        String bobSessions = json("GET", "/api/v1/auth/sessions", null, b).body();
        Matcher m = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(bobSessions);
        assertThat(m.find()).isTrue();
        String bobSessionId = m.group(1);

        HttpResponse<String> attempt = json("DELETE", "/api/v1/auth/sessions/" + bobSessionId, null, a);
        assertThat(attempt.statusCode()).isEqualTo(404);
        assertThat(json("GET", "/api/v1/me", null, b).statusCode()).as("Bob is still signed in").isEqualTo(200);
        assertThat(json("DELETE", "/api/v1/auth/sessions/not-a-uuid", null, a).statusCode()).isEqualTo(404);
    }

    @Test
    void cookieAuthenticatedApiCallsStillNeedTheCsrfToken() {
        Browser web = new Browser(TestServer.baseUrl());
        web.register("Cookie User", email(), "trees grow upside down", "student");
        HttpResponse<String> forged = web.postRaw("/api/v1/auth/sessions/revoke-others", java.util.Map.of());
        assertThat(forged.statusCode()).isEqualTo(403);
        assertThat(forged.body()).contains("CSRF_TOKEN_INVALID");
        assertThat(web.get("/app/today").statusCode()).as("still signed in").isEqualTo(200);
    }

    @Test
    void garbageBearerTokensAreRejected() {
        assertThat(json("GET", "/api/v1/me", null, "x").statusCode()).isEqualTo(401);
        assertThat(json("GET", "/api/v1/me", null, "a".repeat(43)).statusCode()).isEqualTo(401);
        assertThat(json("GET", "/api/v1/me", null, "a".repeat(500)).statusCode()).isEqualTo(401);
    }

    @Test
    void unknownApiPathsReturnJson404() {
        HttpResponse<String> r = json("GET", "/api/v1/nothing-here", null, null);
        assertThat(r.statusCode()).isEqualTo(404);
        assertThat(r.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).contains("application/json"));
        assertThat(r.body()).contains("\"code\":\"NOT_FOUND\"");
        HttpResponse<String> sub = json("GET", "/api/v1/auth/whatever", null, null);
        assertThat(sub.statusCode()).isEqualTo(404);
        assertThat(sub.body()).contains("\"code\":\"NOT_FOUND\"");
    }
}
