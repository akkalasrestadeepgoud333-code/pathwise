package app.pathwise.app;

import app.pathwise.app.testing.Browser;
import app.pathwise.app.testing.TestServer;
import app.pathwise.core.identity.PasswordHasher;
import app.pathwise.data.Database;
import app.pathwise.web.identity.IdentityService;
import app.pathwise.web.platform.events.EventBus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Admin console, the CLI admin path, the audit trail and the outbox, against the running server's database. */
class AdminAndPlatformTest {
    private static final String PASSWORD = "an admin passphrase 2026";
    private static Database db;

    @BeforeAll
    static void connect() {
        db = TestServer.database();
    }

    @AfterAll
    static void close() {
        db.close();
    }

    private static IdentityService cliIdentity() {
        return new IdentityService(db.tx(), new PasswordHasher(1024, 1, 1), new EventBus(), Clock.systemUTC(),
                Duration.ofHours(12), Duration.ofDays(30));
    }

    @Test
    void cliCreatedAdminSeesLiveCountsAndTheAuditLog() {
        String email = "admin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        cliIdentity().createOrPromoteAdmin(email, "Platform Admin", PASSWORD);

        Browser admin = new Browser(TestServer.baseUrl());
        HttpResponse<String> login = admin.login(email, PASSWORD);
        assertThat(Browser.location(login)).isEqualTo("/admin");
        HttpResponse<String> page = admin.get("/admin");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("Platform overview").contains("identity.admin_granted").contains("identity.login");
        assertThat(page.body()).contains("href=\"/admin\"").contains("href=\"/teach\"");
    }

    @Test
    void promotingAnExistingAccountKeepsItsPasswordAndAddsTheRole() {
        String email = "promote-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        Browser b = new Browser(TestServer.baseUrl());
        b.register("Soon Admin", email, "trees grow upside down", "student");
        assertThat(b.get("/admin").statusCode()).isEqualTo(403);

        cliIdentity().createOrPromoteAdmin(email, "Ignored Name", "a different password here");
        // Roles are re-read on every request, so the existing session gains access immediately.
        assertThat(b.get("/admin").statusCode()).isEqualTo(200);
        assertThat(new Browser(TestServer.baseUrl()).login(email, "trees grow upside down").statusCode()).isEqualTo(303);
    }

    @Test
    void registrationWritesAnAuditEntryAndAProcessedDomainEvent() throws Exception {
        String email = "events-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        new Browser(TestServer.baseUrl()).register("Event Person", email, "trees grow upside down", "student");

        int audits = db.tx().read(c -> {
            try (ResultSet rs = c.createStatement().executeQuery(
                    "select count(*) from audit_log a join users u on u.id = a.actor_id "
                            + "where u.email = '" + email + "' and a.action in ('identity.register', 'identity.login')")) {
                rs.next();
                return rs.getInt(1);
            }
        });
        assertThat(audits).isEqualTo(2);

        // The outbox worker runs every second; wait up to 10 s for delivery.
        long deadline = System.currentTimeMillis() + 10_000;
        boolean processed = false;
        while (!processed && System.currentTimeMillis() < deadline) {
            processed = db.tx().read(c -> {
                try (ResultSet rs = c.createStatement().executeQuery(
                        "select count(*) from domain_events e join users u on e.payload->>'userId' = u.id::text "
                                + "where u.email = '" + email + "' and e.processed_at is not null")) {
                    rs.next();
                    return rs.getInt(1) == 1;
                }
            });
            if (!processed) {
                Thread.sleep(200);
            }
        }
        assertThat(processed).isTrue();
    }

    @Test
    void passwordsAndTokensAreNeverStoredInPlainText() {
        String email = "secrets-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        new Browser(TestServer.baseUrl()).register("Secret Keeper", email, "trees grow upside down", "student");
        String hash = db.tx().read(c -> {
            try (ResultSet rs = c.createStatement().executeQuery(
                    "select password_hash from users where email = '" + email + "'")) {
                rs.next();
                return rs.getString(1);
            }
        });
        assertThat(hash).startsWith("$argon2id$").doesNotContain("trees grow upside down");
        int tokenLengths = db.tx().read(c -> {
            try (ResultSet rs = c.createStatement().executeQuery(
                    "select count(*) from sessions where char_length(token_hash) <> 64")) {
                rs.next();
                return rs.getInt(1);
            }
        });
        assertThat(tokenLengths).isZero();
    }
}
