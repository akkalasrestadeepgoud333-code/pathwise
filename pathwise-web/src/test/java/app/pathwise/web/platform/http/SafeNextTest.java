package app.pathwise.web.platform.http;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Post-login redirects must never leave the site (open-redirect protection). */
class SafeNextTest {

    private static String safe(String next) {
        return new PageServlet() {
            String check(String n) {
                return safeNext(n, "/fallback");
            }
        }.check(next);
    }

    @Test
    void allowsSameSiteRelativePaths() {
        assertThat(safe("/app/today")).isEqualTo("/app/today");
        assertThat(safe("/app/settings?tab=1#sessions")).isEqualTo("/app/settings?tab=1#sessions");
    }

    @Test
    void rejectsExternalAndMalformedTargets() {
        assertThat(safe(null)).isEqualTo("/fallback");
        assertThat(safe("")).isEqualTo("/fallback");
        assertThat(safe("https://evil.example")).isEqualTo("/fallback");
        assertThat(safe("//evil.example/x")).isEqualTo("/fallback");
        assertThat(safe("/\\evil.example")).isEqualTo("/fallback");
        assertThat(safe("/ok\r\nSet-Cookie: x=1")).isEqualTo("/fallback");
        assertThat(safe("javascript:alert(1)")).isEqualTo("/fallback");
        assertThat(safe("/" + "a".repeat(600))).isEqualTo("/fallback");
    }
}
