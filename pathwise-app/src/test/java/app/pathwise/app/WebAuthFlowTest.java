package app.pathwise.app;

import app.pathwise.app.testing.Browser;
import app.pathwise.app.testing.TestServer;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static app.pathwise.app.testing.Browser.location;
import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end HTML flows through the real server: Tomcat + JSP + filters + PostgreSQL. */
class WebAuthFlowTest {
    private static final String PASSWORD = "trees grow upside down";

    private static String email() {
        return "u-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private Browser browser() {
        return new Browser(TestServer.baseUrl());
    }

    @Test
    void landingPageRendersWithSecurityHeaders() {
        HttpResponse<String> r = browser().get("/");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("a different path").contains("Get started");
        assertThat(r.headers().firstValue("Content-Security-Policy")).hasValueSatisfying(csp ->
                assertThat(csp).contains("script-src 'self'").contains("frame-ancestors 'none'").doesNotContain("unsafe-inline"));
        assertThat(r.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(r.headers().firstValue("X-Request-Id")).isPresent();
        assertThat(r.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(r.headers().firstValue("Server")).isEmpty();
    }

    @Test
    void staticAssetsAreServedAndCacheable() {
        HttpResponse<String> r = browser().get("/static/css/tokens.css");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("--primary");
        assertThat(r.headers().firstValue("Cache-Control")).hasValueSatisfying(v -> assertThat(v).contains("public"));
        assertThat(browser().get("/static/img/icons.svg").body()).contains("i-compass");
    }

    @Test
    void studentRegistersLandsOnTodayAndSeesTheirName() {
        Browser b = browser();
        String before = null;
        b.get("/register");
        before = b.sessionCookie();
        HttpResponse<String> r = b.post("/register", Map.of("displayName", "Asha Rao", "email", email(),
                "password", PASSWORD, "role", "student"));
        assertThat(before).isNotNull();
        assertThat(r.statusCode()).isEqualTo(303);
        assertThat(location(r)).isEqualTo("/app/today");
        assertThat(b.sessionCookie()).as("session id rotates at login (fixation defence)").isNotEqualTo(before);

        HttpResponse<String> today = b.get("/app/today");
        assertThat(today.statusCode()).isEqualTo(200);
        assertThat(today.body()).contains("Asha.").contains("Welcome to Pathwise, Asha.").contains("No subjects yet");
        assertThat(today.body()).doesNotContain("href=\"/teach\"").doesNotContain("href=\"/admin\"");
    }

    @Test
    void userSuppliedTextIsEscapedInPages() {
        Browser b = browser();
        b.register("<script>alert(1)</script> Kid", email(), PASSWORD, "student");
        String body = b.get("/app/today").body();
        assertThat(body).doesNotContain("<script>alert(1)</script>");
        assertThat(body).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    void registrationErrorsAreShownWithTheFormKept() {
        Browser b = browser();
        String taken = email();
        b.register("First Person", taken, PASSWORD, "student");

        Browser other = browser();
        HttpResponse<String> dup = other.register("Second Person", taken.toUpperCase(), PASSWORD, "student");
        assertThat(dup.statusCode()).isEqualTo(422);
        assertThat(dup.body()).contains("already exists").contains("value=\"Second Person\"");

        HttpResponse<String> weak = other.register("Weak Password", email(), "short", "student");
        assertThat(weak.statusCode()).isEqualTo(422);
        assertThat(weak.body()).contains("at least 10 characters").contains("aria-invalid=\"true\"");
        assertThat(weak.body()).as("passwords are never echoed back").doesNotContain("value=\"short\"");

        HttpResponse<String> badRole = other.register("Role Hacker", email(), PASSWORD, "admin");
        assertThat(badRole.statusCode()).isEqualTo(422);
    }

    @Test
    void loginFailuresAreGenericAndRateLimited() {
        Browser b = browser();
        String e = email();
        b.register("Rate Limit", e, PASSWORD, "student");

        Browser attacker = browser();
        HttpResponse<String> wrong = attacker.login(e, "not the password");
        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(wrong.body()).contains("don&#039;t match an account");
        HttpResponse<String> unknown = attacker.login("nobody-" + e, "whatever password");
        assertThat(unknown.body()).as("same message for unknown emails").contains("don&#039;t match an account");

        int status = 0;
        for (int i = 0; i < 6; i++) {
            status = attacker.login(e, "still wrong " + i).statusCode();
        }
        assertThat(status).isEqualTo(429);
    }

    @Test
    void formsWithoutAValidCsrfTokenAreRejected() {
        Browser b = browser();
        b.get("/register");
        HttpResponse<String> r = b.postRaw("/register", Map.of("displayName", "No Token", "email", email(),
                "password", PASSWORD, "role", "student"));
        assertThat(r.statusCode()).isEqualTo(403);
        assertThat(r.body()).contains("This form expired");

        HttpResponse<String> forged = b.postRaw("/register", Map.of("_csrf", "forged-token", "displayName", "Forged",
                "email", email(), "password", PASSWORD, "role", "student"));
        assertThat(forged.statusCode()).isEqualTo(403);
    }

    @Test
    void anonymousVisitorsAreSentToSignInWithASafeReturnPath() {
        HttpResponse<String> r = browser().get("/app/settings");
        assertThat(r.statusCode()).isEqualTo(303);
        assertThat(location(r)).isEqualTo("/login?next=%2Fapp%2Fsettings");

        Browser b = browser();
        String e = email();
        b.register("Next Path", e, PASSWORD, "student");
        b.post("/logout", Map.of());

        b.get("/login?next=/app/settings");
        HttpResponse<String> ok = b.post("/login", Map.of("email", e, "password", PASSWORD, "next", "/app/settings"));
        assertThat(location(ok)).isEqualTo("/app/settings");

        b.get("/app/settings"); // pick up the CSRF token rotated at sign-in
        b.post("/logout", Map.of());
        b.get("/login");
        HttpResponse<String> evil = b.post("/login", Map.of("email", e, "password", PASSWORD, "next", "https://evil.example/"));
        assertThat(location(evil)).isEqualTo("/app/today");
    }

    @Test
    void rolesControlAccessToAreas() {
        Browser student = browser();
        student.register("Only Student", email(), PASSWORD, "student");
        assertThat(student.get("/teach").statusCode()).isEqualTo(403);
        HttpResponse<String> admin = student.get("/admin");
        assertThat(admin.statusCode()).isEqualTo(403);
        assertThat(admin.body()).contains("off-limits").doesNotContain("Platform overview");

        Browser teacher = browser();
        HttpResponse<String> r = teacher.register("Prof Teacher", email(), PASSWORD, "teacher");
        assertThat(location(r)).isEqualTo("/teach");
        HttpResponse<String> teach = teacher.get("/teach");
        assertThat(teach.statusCode()).isEqualTo(200);
        assertThat(teach.body()).contains("No rooms yet");
        assertThat(teacher.get("/app/today").statusCode()).as("teachers can learn too").isEqualTo(200);
        assertThat(teacher.get("/admin").statusCode()).isEqualTo(403);
    }

    @Test
    void signOutEndsTheSessionServerSide() {
        Browser b = browser();
        b.register("Sign Out", email(), PASSWORD, "student");
        assertThat(b.get("/app/today").statusCode()).isEqualTo(200);
        HttpResponse<String> out = b.post("/logout", Map.of());
        assertThat(location(out)).isEqualTo("/login?signedOut=1");
        assertThat(b.get("/app/today").statusCode()).isEqualTo(303);
        assertThat(b.get("/login?signedOut=1").body()).contains("signed out");
    }

    @Test
    void signingOutOtherDevicesTakesEffectImmediately() {
        String e = email();
        Browser laptop = browser();
        laptop.register("Two Devices", e, PASSWORD, "student");
        Browser phone = browser().userAgent("Mozilla/5.0 (Linux; Android 14) Chrome/130.0 Mobile Safari/537.36");
        phone.login(e, PASSWORD);
        assertThat(phone.get("/app/today").statusCode()).isEqualTo(200);

        HttpResponse<String> settings = laptop.get("/app/settings");
        assertThat(settings.body()).contains("Chrome on Windows").contains("Chrome on Android").contains("This device");
        HttpResponse<String> r = laptop.post("/app/settings/sessions/revoke-others", Map.of());
        assertThat(r.statusCode()).isEqualTo(303);
        assertThat(laptop.get("/app/settings").body()).contains("Signed out 1 other session.");

        assertThat(phone.get("/app/today").statusCode()).isEqualTo(303);
        assertThat(laptop.get("/app/today").statusCode()).isEqualTo(200);
    }

    @Test
    void themePreferenceIsSavedAndRenderedBeforePaint() {
        Browser b = browser();
        b.register("Night Owl", email(), PASSWORD, "student");
        assertThat(b.get("/app/today").body().strip()).startsWith("<!DOCTYPE html>").contains("<html lang=\"en\">");
        b.get("/app/settings");
        assertThat(b.post("/app/settings/theme", Map.of("theme", "dark")).statusCode()).isEqualTo(303);
        assertThat(b.get("/app/today").body()).contains("<html lang=\"en\" data-theme=\"dark\">");
        assertThat(b.post("/app/settings/theme", Map.of("theme", "neon")).statusCode()).isEqualTo(400);
    }

    @Test
    void unknownPagesGetAFriendlyErrorWithAReference() {
        HttpResponse<String> r = browser().get("/no/such/page");
        assertThat(r.statusCode()).isEqualTo(404);
        assertThat(r.body()).contains("doesn't lead anywhere").contains("Reference:");
        assertThat(r.body()).doesNotContain("Apache Tomcat").doesNotContain("Exception");
    }

    @Test
    void healthCheckReportsTheDatabase() {
        HttpResponse<String> r = browser().get("/healthz");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).isEqualTo("{\"status\":\"ok\",\"database\":true}");
    }
}
