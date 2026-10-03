package app.pathwise.web.platform;

import app.pathwise.core.identity.PlatformRole;
import app.pathwise.core.security.Actor;
import app.pathwise.web.identity.RegisterForm;
import app.pathwise.web.platform.view.CurrentUser;
import app.pathwise.web.platform.view.ViewModels;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebUnitTest {

    private static Map<String, String> baseConfig() {
        Map<String, String> m = new HashMap<>();
        m.put("pathwise.db.url", "jdbc:postgresql://localhost:5432/pathwise");
        return m;
    }

    @Test
    void configDefaultsAreSafeForDevelopment() {
        AppConfig c = AppConfig.load(baseConfig()::get);
        assertThat(c.environment()).isEqualTo(AppConfig.Environment.DEV);
        assertThat(c.secureCookies()).isFalse();
        assertThat(c.hashProfile()).isEqualTo(AppConfig.HashProfile.STANDARD);
        assertThat(c.toString()).doesNotContain(c.secret()).contains("secret=***");
    }

    @Test
    void productionRequiresARealSecretAndSecureCookies() {
        Map<String, String> m = baseConfig();
        m.put("pathwise.env", "production");
        assertThatThrownBy(() -> AppConfig.load(m::get)).hasMessageContaining("pathwise.secret");
        m.put("pathwise.secret", "x".repeat(40));
        AppConfig c = AppConfig.load(m::get);
        assertThat(c.secureCookies()).isTrue();
        assertThat(c.isProduction()).isTrue();
    }

    @Test
    void fastHashingIsOnlyAllowedInTests() {
        Map<String, String> m = baseConfig();
        m.put("pathwise.hash.profile", "fast");
        assertThatThrownBy(() -> AppConfig.load(m::get)).hasMessageContaining("only allowed");
        m.put("pathwise.env", "test");
        assertThat(AppConfig.load(m::get).hashProfile()).isEqualTo(AppConfig.HashProfile.FAST);
    }

    @Test
    void missingDatabaseUrlIsExplained() {
        assertThatThrownBy(() -> AppConfig.load(k -> null)).hasMessageContaining("PATHWISE_DB_URL");
    }

    @Test
    void registerFormValidation() {
        RegisterForm f = new RegisterForm();
        f.setDisplayName("A");
        f.setEmail("not-an-email");
        f.setPassword("");
        f.setRole("hacker");
        Map<String, String> errors = Validation.validate(f);
        assertThat(errors).containsKeys("displayName", "email", "password", "role");

        f.setDisplayName("Asha Rao");
        f.setEmail("asha@example.com");
        f.setPassword("trees grow upside down");
        f.setRole("teacher");
        assertThat(Validation.validate(f)).isEmpty();
        assertThat(f.isTeacher()).isTrue();
    }

    @Test
    void currentUserNeverNeedsIdsAndComputesInitials() {
        Actor actor = new Actor(UUID.randomUUID(), "Asha  Rao Kumar", Set.of(PlatformRole.STUDENT, PlatformRole.TEACHER), UUID.randomUUID());
        CurrentUser u = CurrentUser.of(actor, "dark");
        assertThat(u.getInitials()).isEqualTo("AK");
        assertThat(u.getFirstName()).isEqualTo("Asha");
        assertThat(u.isTeacher()).isTrue();
        assertThat(u.isAdmin()).isFalse();
        assertThat(u.getTheme()).isEqualTo("dark");
    }

    @Test
    void timeLabelsAreHumanAndUseTheLearnerZone() {
        java.time.Instant at = java.time.Instant.parse("2026-10-03T17:46:00Z");
        java.time.ZoneId ist = app.pathwise.web.platform.view.TimeLabels.DEFAULT_ZONE;
        assertThat(app.pathwise.web.platform.view.TimeLabels.dateTime(at, ist)).isEqualTo("3 Oct 2026, 11:16 pm");
        assertThat(app.pathwise.web.platform.view.TimeLabels.relative(at, at.plusSeconds(30), ist)).isEqualTo("just now");
        assertThat(app.pathwise.web.platform.view.TimeLabels.relative(at, at.plusSeconds(300), ist)).isEqualTo("5 min ago");
        assertThat(app.pathwise.web.platform.view.TimeLabels.relative(at, at.plusSeconds(3600), ist)).isEqualTo("1 hour ago");
        assertThat(app.pathwise.web.platform.view.TimeLabels.relative(at, at.plusSeconds(86_400 + 60), ist)).isEqualTo("yesterday");
        assertThat(app.pathwise.web.platform.view.TimeLabels.relative(at, at.plusSeconds(30 * 86_400L), ist)).isEqualTo("3 Oct 2026");
    }

    record Sample(String name, int count) {}

    @Test
    void viewModelsExposeRecordComponents() {
        assertThat(ViewModels.of(new Sample("x", 3))).containsEntry("name", "x").containsEntry("count", 3);
        assertThat(ViewModels.list(List.of(new Sample("a", 1), new Sample("b", 2)))).hasSize(2);
    }
}
