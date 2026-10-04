package app.pathwise.app;

import app.pathwise.app.testing.Browser;
import app.pathwise.app.testing.TestServer;
import app.pathwise.data.Database;
import app.pathwise.web.content.ContentPackLoader;
import app.pathwise.web.content.ContentPackService;
import app.pathwise.web.platform.events.EventBus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static app.pathwise.app.testing.Browser.location;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M7 end to end: XP from verified play and assessments (with caps and idempotency), streak days, a badge
 * awarded from recorded evidence, community XP reversed by moderation, and notifications.
 */
class RewardsFlowTest {
    private static final String PASSWORD = "trees grow upside down";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static Database db;

    @BeforeAll
    static void setUp() {
        db = TestServer.database();
        var r = new ContentPackService(db.tx(), new ContentPackLoader(RewardsFlowTest.class.getClassLoader()),
                new EventBus(), Clock.systemUTC()).importPack("data-structures", null, false);
        assertThat(r.outcome()).isNotEqualTo(ContentPackService.Outcome.INVALID);
    }

    @AfterAll
    static void tearDown() {
        db.close();
    }

    private record Person(Browser b, String email, String id) {}

    private static Person student(String name) {
        String email = "rw-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        Browser b = new Browser(TestServer.baseUrl());
        b.register(name, email, PASSWORD, "student");
        return new Person(b, email, one("select id from users where email = ?", email));
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

    private static int xp(Person p) {
        return Integer.parseInt(one("select coalesce(sum(xp), 0) from xp_events where user_id = ?::uuid", p.id()));
    }

    /** Handlers run after commit, from the outbox (every second): wait for the effect. */
    private static void eventually(Supplier<Boolean> condition) {
        long end = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < end) {
            if (condition.get()) {
                return;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertThat(condition.get()).as("condition within 20 s").isTrue();
    }

    private static void quiet() {
        eventually(() -> "0".equals(one("select count(*) from domain_events where processed_at is null and failed_at is null and ? is not null", "x")));
    }

    /** Plays a game perfectly (backdating its start so the timing is plausible). */
    private static UUID playPerfect(Person p, String key) throws Exception {
        p.b().get("/app/games/" + key);
        UUID attempt = UUID.fromString(location(p.b().post("/app/games/" + key + "/start", Map.of())).substring("/app/games/play/".length()));
        p.b().get("/app/games/play/" + attempt);
        db.tx().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("update activity_attempts set started_at = now() - interval '5 minutes' where id = ?")) {
                ps.setObject(1, attempt);
                ps.executeUpdate();
            }
        });
        JsonNode puzzle = JSON.readTree(one("select puzzle::text from activity_attempts where id = ?", attempt));
        ArrayNode actions = JSON.createArrayNode();
        long[] t = {500};
        java.util.function.Consumer<String[]> add = a -> {
            ObjectNode o = actions.addObject().put("type", a[0]).put("a", a[1]);
            if (a.length > 2) {
                o.put("b", a[2]);
            }
            o.put("t", t[0]);
            t[0] += 400;
        };
        String template = one("select game_template from activities where key = ?", key);
        switch (template) {
            case "match_pairs" -> puzzle.get("items").forEach(i -> add.accept(new String[] {"match", i.get("id").asText(), i.get("id").asText()}));
            case "bug_hunt" -> puzzle.get("rounds").forEach(r -> {
                add.accept(new String[] {"line", r.get("bugLine").asText()});
                add.accept(new String[] {"class", r.get("bugClass").asText()});
            });
            default -> puzzle.get("rounds").forEach(r -> r.get("answer").forEach(v -> add.accept(new String[] {"tap", v.asText()})));
        }
        ObjectNode body = JSON.createObjectNode();
        body.set("actions", actions);
        HttpResponse<String> r = p.b().send(HttpRequest.newBuilder(URI.create(TestServer.baseUrl() + "/app/games/play/" + attempt + "/finish"))
                .header("Content-Type", "application/json").header("X-CSRF-Token", p.b().csrf()).header("X-Requested-With", "fetch")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())));
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(one("select status from activity_attempts where id = ?", attempt)).isEqualTo("completed");
        return attempt;
    }

    @Test
    void verifiedGamesEarnCappedXpAStreakDayAndABadge() throws Exception {
        Person s = student("Gamer Gita");
        for (int i = 0; i < 4; i++) {
            playPerfect(s, "match-structures");
        }
        // 30 XP per perfect game, but only the first 3 per game per day count.
        eventually(() -> "3".equals(one("select count(*) from xp_events where user_id = ?::uuid and rule = 'GAME_COMPLETE'", s.id())));
        quiet();
        assertThat(one("select count(*) from xp_events where user_id = ?::uuid and rule = 'GAME_COMPLETE'", s.id())).isEqualTo("3");
        assertThat(one("select coalesce(sum(xp), 0) from xp_events where user_id = ?::uuid and rule = 'GAME_COMPLETE'", s.id())).isEqualTo("90");
        assertThat(one("select count(*) from activity_days where user_id = ?::uuid and meaningful", s.id())).isEqualTo("1");

        // Five perfect Bug Hunts covering several skills earn Debug Detective, with badge XP and a notification.
        for (int i = 0; i < 5; i++) {
            playPerfect(s, "bug-hunt");
        }
        eventually(() -> one("select badge_key from badge_awards where user_id = ?::uuid and badge_key = 'debug-detective'", s.id()) != null);
        assertThat(one("select xp from xp_events where unique_key = ?", "badge:" + s.id() + ":debug-detective")).isEqualTo("50");
        String page = s.b().get("/app/achievements").body();
        assertThat(page).contains("Debug Detective").contains("earned").contains("1 day").contains("Practice Pro").contains("locked");
        assertThat(s.b().get("/app/today").body()).contains("1-day streak").contains("XP");
        String inbox = s.b().get("/app/notifications").body();
        assertThat(inbox).contains("You earned Debug Detective");
        assertThat(s.b().get("/app/today").body()).contains("bell-count");
        s.b().post("/app/notifications", Map.of());
        assertThat(s.b().get("/app/today").body()).doesNotContain("bell-count");
    }

    @Test
    void assessmentsGiveXpOnceAndRedeliveredEventsChangeNothing() {
        Person s = student("Exam Esha");
        s.b().get("/app/assessments/ds-diagnostic");
        String attempt = location(s.b().post("/app/assessments/ds-diagnostic/start", Map.of())).substring("/app/assessments/attempt/".length());
        s.b().get("/app/assessments/attempt/" + attempt);
        s.b().post("/app/assessments/attempt/" + attempt + "/submit", Map.of("confirm", "yes"));
        eventually(() -> one("select xp from xp_events where user_id = ?::uuid and rule = 'ASSESSMENT_SUBMIT'", s.id()) != null);
        quiet();
        int before = xp(s);
        assertThat(before).isGreaterThanOrEqualTo(30);
        // Deliver the same event again: unique keys make it a no-op.
        String payload = one("select payload::text from domain_events where type = 'assessment.graded' and payload->>'attemptId' = ?", attempt);
        db.tx().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("insert into domain_events (type, payload) values ('assessment.graded', ?::jsonb)")) {
                ps.setString(1, payload);
                ps.executeUpdate();
            }
        });
        quiet();
        assertThat(xp(s)).isEqualTo(before);
        // Results notification links to the result, and opening it marks it read.
        String id = one("select id from notifications where user_id = ?::uuid and type = 'assessment_result'", s.id());
        assertThat(location(s.b().get("/app/notifications/" + id))).isEqualTo("/app/assessments/result/" + attempt);
        assertThat(one("select read_at is not null from notifications where id = ?::uuid", id)).isEqualTo("t");
        // Someone else's notification id goes nowhere.
        Person other = student("Other Om");
        assertThat(location(other.b().get("/app/notifications/" + id))).isEqualTo("/app/notifications");
    }

    @Test
    void acceptedAnswersEarnXpThatModerationReverses() {
        Person asker = student("Asker Anu");
        Person helper = student("Helper Hari");
        asker.b().get("/app/doubts/ask");
        String thread = location(asker.b().post("/app/doubts/ask", Map.of("action", "post", "room", "", "category", "concept",
                "title", "How do I pick the inorder successor?", "body", "In BST delete with two children, which node replaces it?", "topic", "bst")))
                .substring("/app/doubts/".length());
        helper.b().get("/app/doubts/" + thread);
        helper.b().post("/app/doubts/" + thread, Map.of("action", "answer", "body", "Take the smallest value in the right subtree."));
        eventually(() -> one("select id from notifications where user_id = ?::uuid and type = 'doubt_answered'", asker.id()) != null);
        String post = one("select id from posts where thread_id = ?::uuid", thread);
        asker.b().get("/app/doubts/" + thread);
        asker.b().post("/app/doubts/" + thread, Map.of("action", "accept", "postId", post));
        eventually(() -> one("select xp from xp_events where unique_key = ?", "accepted:" + post) != null);
        assertThat(xp(helper)).isEqualTo(25);
        assertThat(xp(asker)).as("no XP for asking").isZero();

        // A moderator removes the answer: the community XP is reversed (learning XP never is).
        asker.b().get("/app/doubts/" + thread);
        asker.b().post("/app/doubts/" + thread, Map.of("action", "report", "kind", "post", "targetId", post, "reason", "misinformation"));
        String mod = "mod-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        new app.pathwise.web.identity.IdentityService(db.tx(), new app.pathwise.core.identity.PasswordHasher(1024, 1, 1), new EventBus(),
                Clock.systemUTC(), java.time.Duration.ofHours(12), java.time.Duration.ofDays(30)).createOrPromoteAdmin(mod, "Mod", PASSWORD);
        Browser m = new Browser(TestServer.baseUrl());
        m.login(mod, PASSWORD);
        m.get("/admin/moderation");
        m.post("/admin/moderation", Map.of("action", "moderate", "reportId", one("select id from reports where target_id = ?::uuid", post),
                "decision", "remove", "reason", "Wrong explanation"));
        eventually(() -> one("select xp from xp_events where unique_key = ?", "accepted-reversed:" + post) != null);
        assertThat(xp(helper)).isZero();
    }
}
