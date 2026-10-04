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

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static app.pathwise.app.testing.Browser.location;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M10 end to end: the admin console (re-authentication for sensitive actions, suspension, roles, the last-admin
 * guard, closed sign-ups, game templates, rooms overview, audit viewer) and live doubt sessions (anonymous
 * aliases, queue order, votes, hands, host controls, removal, lock, end).
 */
class AdminConsoleAndLiveFlowTest {
    private static final String PASSWORD = "trees grow upside down";
    private static Database db;

    @BeforeAll
    static void setUp() {
        db = TestServer.database();
        var r = new app.pathwise.web.content.ContentPackService(db.tx(),
                new app.pathwise.web.content.ContentPackLoader(AdminConsoleAndLiveFlowTest.class.getClassLoader()),
                new EventBus(), Clock.systemUTC()).importPack("data-structures", null, false);
        assertThat(r.outcome()).isNotEqualTo(app.pathwise.web.content.ContentPackService.Outcome.INVALID);
    }

    @AfterAll
    static void tearDown() {
        db.close();
    }

    private record Person(Browser b, String name, String email, String id) {}

    private static String uniq() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static Person person(String name, String role) {
        String email = "m10-" + uniq() + "@example.com";
        Browser b = new Browser(TestServer.baseUrl());
        b.register(name, email, PASSWORD, role);
        return new Person(b, name, email, one("select id from users where email = ?", email));
    }

    private static Person admin(String name) {
        String email = "m10-admin-" + uniq() + "@example.com";
        new IdentityService(db.tx(), new PasswordHasher(1024, 1, 1), new EventBus(), Clock.systemUTC(), Duration.ofHours(12),
                Duration.ofDays(30)).createOrPromoteAdmin(email, name, PASSWORD);
        Browser b = new Browser(TestServer.baseUrl());
        b.login(email, PASSWORD);
        return new Person(b, name, email, one("select id from users where email = ?", email));
    }

    private static String one(String sql, Object arg) {
        return db.tx().read(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, arg);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        });
    }

    @Test
    void adminConsoleGuardsSensitiveActionsAndAuditsEverything() {
        Person admin = admin("Ada Admin");
        Person student = person("Sam Student", "student");
        Browser api = new Browser(TestServer.baseUrl());
        HttpResponse<String> login = api.send(HttpRequest.newBuilder(URI.create(TestServer.baseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + student.email() + "\",\"password\":\"" + PASSWORD + "\"}")));
        assertThat(login.statusCode()).isEqualTo(200);

        // Students can't reach any console page.
        for (String page : new String[] {"/admin/users", "/admin/rooms", "/admin/games", "/admin/audit", "/admin/settings"}) {
            assertThat(student.b().get(page).statusCode()).as(page).isEqualTo(403);
        }

        // Search finds the student; their page shows roles and status.
        assertThat(admin.b().get("/admin/users?q=" + student.email()).body()).contains("Sam Student");
        String userPage = "/admin/users/" + student.id();
        assertThat(admin.b().get(userPage).body()).contains("active").contains("Confirm now");

        // Suspending without a fresh password is refused and sends the admin to confirm.
        assertThat(location(admin.b().post(userPage, Map.of("action", "suspend", "reason", "Sharing assessment answers")))).startsWith("/admin/reauth");
        assertThat(one("select status from users where id = ?::uuid", student.id())).isEqualTo("active");
        assertThat(location(admin.b().post("/admin/reauth", Map.of("password", "wrong password here", "next", userPage)))).startsWith("/admin/reauth");
        assertThat(location(admin.b().post("/admin/reauth", Map.of("password", PASSWORD, "next", userPage)))).isEqualTo(userPage);
        assertThat(location(admin.b().post("/admin/reauth", Map.of("password", PASSWORD, "next", "https://evil.example/")))).isEqualTo("/admin");

        // A reason is required; then suspension ends every session, web and app.
        admin.b().post(userPage, Map.of("action", "suspend", "reason", "x"));
        assertThat(one("select status from users where id = ?::uuid", student.id())).isEqualTo("active");
        admin.b().post(userPage, Map.of("action", "suspend", "reason", "Sharing assessment answers"));
        assertThat(one("select status from users where id = ?::uuid", student.id())).isEqualTo("suspended");
        assertThat(location(student.b().get("/app/today"))).startsWith("/login");
        String token = login.body().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        assertThat(api.send(HttpRequest.newBuilder(URI.create(TestServer.baseUrl() + "/api/v1/me")).header("Authorization", "Bearer " + token).GET())
                .statusCode()).isEqualTo(401);
        assertThat(new Browser(TestServer.baseUrl()).post("/login", Map.of("email", student.email(), "password", PASSWORD)).body())
                .doesNotContain("Today");
        admin.b().post(userPage, Map.of("action", "reactivate", "reason", "Spoke with the student"));
        assertThat(one("select status from users where id = ?::uuid", student.id())).isEqualTo("active");

        // Roles: grant mentor; the admin can't remove their own admin role; can't suspend themselves.
        admin.b().post(userPage, Map.of("action", "grant", "role", "mentor"));
        assertThat(one("select count(*) from user_platform_roles where user_id = ?::uuid and role = 'mentor'", student.id())).isEqualTo("1");
        admin.b().post(userPage, Map.of("action", "grant", "role", "student"));
        admin.b().post("/admin/users/" + admin.id(), Map.of("action", "revoke", "role", "admin"));
        assertThat(one("select count(*) from user_platform_roles where user_id = ?::uuid and role = 'admin'", admin.id())).isEqualTo("1");
        admin.b().post("/admin/users/" + admin.id(), Map.of("action", "suspend", "reason", "Testing self suspension"));
        assertThat(one("select status from users where id = ?::uuid", admin.id())).isEqualTo("active");
        admin.b().post(userPage, Map.of("action", "verify"));
        assertThat(one("select is_verified_educator from users where id = ?::uuid", student.id())).isEqualTo("t");
        admin.b().post(userPage, Map.of("action", "trust", "level", "9"));
        assertThat(one("select trust_level from users where id = ?::uuid", student.id())).isEqualTo("0");

        // Every change above is in the audit log, and the viewer filters by prefix.
        assertThat(one("select count(*) from audit_log where entity_id = ? and action like 'admin.%'", student.id())).isEqualTo("4");
        String audit = admin.b().get("/admin/audit?action=admin.user_").body();
        assertThat(audit).contains("admin.user_suspended").contains("Sharing assessment answers").doesNotContain("identity.login");
        assertThat(admin.b().get(userPage).body()).contains("admin.role_granted");

        // Closed sign-ups stop new accounts (web and API) until reopened.
        try {
            admin.b().post("/admin/settings", Map.of());
            assertThat(admin.b().get("/admin/settings").body()).doesNotContain("checked>");
            String blocked = "m10-closed-" + uniq() + "@example.com";
            assertThat(new Browser(TestServer.baseUrl()).register("Late Comer", blocked, PASSWORD, "student").body())
                    .contains("sign-ups are closed");
            assertThat(one("select count(*) from users where email = ?", blocked)).isEqualTo("0");
            HttpResponse<String> apiReg = new Browser(TestServer.baseUrl()).send(HttpRequest.newBuilder(URI.create(TestServer.baseUrl() + "/api/v1/auth/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"displayName\":\"Late Comer\",\"email\":\"" + blocked + "\",\"password\":\"" + PASSWORD + "\"}")));
            assertThat(apiReg.statusCode()).isEqualTo(403);
            assertThat(apiReg.body()).contains("REGISTRATION_CLOSED");
        } finally {
            admin.b().post("/admin/settings", Map.of("registrationOpen", "on"));
        }
        assertThat(one("select value::text from platform_settings where key = ?", "registration.open")).isEqualTo("true");

        // Switching a game template off hides its games and stops new plays; switching on restores them.
        try {
            admin.b().post("/admin/games", Map.of("key", "bug_hunt"));
            assertThat(student.b().login(student.email(), PASSWORD).statusCode()).isLessThan(400);
            assertThat(student.b().get("/app/games").body()).doesNotContain("/app/games/bug-hunt");
            assertThat(student.b().get("/app/games/bug-hunt").statusCode()).isEqualTo(404);
        } finally {
            admin.b().post("/admin/games", Map.of("key", "bug_hunt", "enabled", "on"));
        }
        assertThat(admin.b().get("/admin/games").body()).contains("Bug Hunt");

        // Rooms: aggregates only, archive needs a reason.
        Person teacher = person("Tara Teacher", "teacher");
        teacher.b().get("/teach");
        String created = location(teacher.b().post("/teach", Map.of("name", "M10 Room " + uniq(), "subject", "data-structures", "joinPolicy", "open")));
        assertThat(created).startsWith("/teach/rooms/");
        String roomId = created.substring("/teach/rooms/".length());
        String rooms = admin.b().get("/admin/rooms?q=M10+Room").body();
        assertThat(rooms).contains("Tara Teacher").doesNotContain(teacher.email());
        admin.b().post("/admin/rooms", Map.of("roomId", roomId, "action", "archive", "reason", "no"));
        assertThat(one("select archived_at is null from rooms where id = ?::uuid", roomId)).isEqualTo("t");
        admin.b().post("/admin/rooms", Map.of("roomId", roomId, "action", "archive", "reason", "Duplicate of another class"));
        assertThat(one("select archived_at is not null from rooms where id = ?::uuid", roomId)).isEqualTo("t");
    }

    @Test
    void liveSessionsKeepEveryoneAnonymousAndGiveTheHostControl() {
        Person host = person("Hana Host", "teacher");
        Person ana = person("Ana Learner", "student");
        Person ben = person("Ben Learner", "student");
        Person cal = person("Cal Learner", "student");

        // Students can't host.
        ana.b().get("/app/live");
        assertThat(ana.b().get("/app/live").body()).doesNotContain("Host a session");
        assertThat(ana.b().post("/app/live", Map.of("title", "My own session")).statusCode()).isEqualTo(403);

        host.b().get("/app/live");
        String page = location(host.b().post("/app/live", Map.of("title", "Trees before Friday")));
        assertThat(page).startsWith("/app/live/");
        String id = page.substring("/app/live/".length());

        // Joining works before the start; asking doesn't.
        ana.b().get(page);
        ana.b().post(page, Map.of("action", "join"));
        ana.b().post(page, Map.of("action", "ask", "body", "Why is BST search O(log n) only sometimes?"));
        assertThat(one("select count(*) from session_questions where session_id = ?::uuid", id)).isEqualTo("0");

        host.b().post(page, Map.of("action", "start"));
        ben.b().get(page);
        ben.b().post(page, Map.of("action", "join"));
        ana.b().post(page, Map.of("action", "ask", "body", "Why is BST search O(log n) only sometimes?"));
        ben.b().post(page, Map.of("action", "ask", "body", "When do I use BFS instead of DFS?"));
        ben.b().post(page, Map.of("action", "hand-up"));
        String anaQ = one("select id from session_questions where session_id = ?::uuid and body like 'Why is BST%'", id);
        String benQ = one("select id from session_questions where session_id = ?::uuid and body like 'When do I%'", id);

        // Ben upvotes Ana's question; nobody can upvote their own.
        ben.b().post(page, Map.of("action", "vote", "questionId", anaQ));
        ana.b().post(page, Map.of("action", "vote", "questionId", anaQ));
        assertThat(one("select count(*) from session_question_votes where question_id = ?::uuid", anaQ)).isEqualTo("1");

        // Everyone, including the host, sees aliases only; the queue is ordered by votes.
        for (Person p : new Person[] {host, ana, ben}) {
            String body = p.b().get(page + "/queue").body();
            assertThat(body).contains("Participant 1").contains("Participant 2")
                    .doesNotContain("Ana Learner").doesNotContain("Ben Learner").doesNotContain(ana.email()).doesNotContain(ana.id());
            assertThat(body.indexOf("Why is BST")).isLessThan(body.indexOf("When do I use BFS"));
        }
        assertThat(host.b().get(page + "/queue").body()).contains("Hands up").contains("Answer now");
        assertThat(ana.b().get(page + "/queue").body()).doesNotContain("Answer now");

        // Only the host runs the queue; one question is answered at a time.
        ana.b().post(page, Map.of("action", "question", "questionId", benQ, "to", "answering"));
        assertThat(one("select status from session_questions where id = ?::uuid", benQ)).isEqualTo("queued");
        host.b().post(page, Map.of("action", "question", "questionId", anaQ, "to", "answering"));
        host.b().post(page, Map.of("action", "question", "questionId", benQ, "to", "answering"));
        assertThat(one("select status from session_questions where id = ?::uuid", anaQ)).isEqualTo("queued");
        host.b().post(page, Map.of("action", "question", "questionId", benQ, "to", "answered"));
        assertThat(one("select status from session_questions where id = ?::uuid", benQ)).isEqualTo("answered");
        assertThat(ben.b().get(page + "/queue").body()).contains("Answered");
        ben.b().get(page);

        // At most three waiting questions per person.
        for (int i = 0; i < 4; i++) {
            ana.b().post(page, Map.of("action", "ask", "body", "Follow-up question number " + i));
        }
        assertThat(one("select count(*) from session_questions where user_id = ?::uuid and status = 'queued'", ana.id())).isEqualTo("3");
        assertThat(ana.b().get(page).body()).contains("questions waiting");

        // Removal by alias: Ben can't take part or rejoin. Locking keeps newcomers out.
        host.b().post(page, Map.of("action", "remove", "alias", "2"));
        ben.b().post(page, Map.of("action", "join"));
        assertThat(ben.b().get(page).body()).contains("The host removed you");
        assertThat(one("select status from session_participants where session_id = ?::uuid and alias_number = 2", id)).isEqualTo("removed");
        host.b().post(page, Map.of("action", "lock"));
        cal.b().get(page);
        cal.b().post(page, Map.of("action", "join"));
        assertThat(one("select count(*) from session_participants where session_id = ?::uuid", id)).isEqualTo("2");
        assertThat(cal.b().get(page).body()).contains("locked");
        assertThat(one("select count(*) from audit_log where entity_id = ? and action = 'live.participant_removed'", id)).isEqualTo("1");

        // Ending stops everything; ended sessions can't restart.
        host.b().post(page, Map.of("action", "end"));
        ana.b().post(page, Map.of("action", "ask", "body", "Too late for this?"));
        assertThat(one("select count(*) from session_questions where body = ?", "Too late for this?")).isEqualTo("0");
        host.b().post(page, Map.of("action", "start"));
        assertThat(one("select status from live_sessions where id = ?::uuid", id)).isEqualTo("ended");
        assertThat(ana.b().get("/app/live").body()).contains("Trees before Friday").contains("Ended");
        assertThat(ana.b().get("/app/live/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
    }
}
