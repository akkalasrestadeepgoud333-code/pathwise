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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static app.pathwise.app.testing.Browser.location;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4 end to end: a game is generated on the server, played (its action log posted the way the page's script
 * does), replayed and verified, and only then turned into informal, capped evidence.
 */
class GameFlowTest {
    private static final String PASSWORD = "trees grow upside down";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static Database db;

    @BeforeAll
    static void setUp() {
        db = TestServer.database();
        var r = new ContentPackService(db.tx(), new ContentPackLoader(GameFlowTest.class.getClassLoader()),
                new EventBus(), Clock.systemUTC()).importPack("data-structures", null, false);
        assertThat(r.outcome()).isNotEqualTo(ContentPackService.Outcome.INVALID);
    }

    @AfterAll
    static void tearDown() {
        db.close();
    }

    private static String newEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static Browser student(String email) {
        Browser b = new Browser(TestServer.baseUrl());
        b.register("Game Player", email, PASSWORD, "student");
        return b;
    }

    private static UUID start(Browser b, String key) {
        b.get("/app/games/" + key);
        HttpResponse<String> started = b.post("/app/games/" + key + "/start", Map.of());
        assertThat(location(started)).startsWith("/app/games/play/");
        UUID id = UUID.fromString(location(started).substring("/app/games/play/".length()));
        b.get("/app/games/play/" + id); // as the browser would: also refreshes the CSRF token
        backdate(id);
        return id;
    }

    /** Tests post a whole game at once; pretend it started minutes ago so the play time is plausible. */
    private static void backdate(UUID attempt) {
        db.tx().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("update activity_attempts set started_at = now() - interval '5 minutes' where id = ?")) {
                ps.setObject(1, attempt);
                ps.executeUpdate();
            }
        });
    }

    private static JsonNode puzzle(UUID attempt) {
        String json = db.tx().read(c -> {
            try (PreparedStatement ps = c.prepareStatement("select puzzle::text from activity_attempts where id = ?")) {
                ps.setObject(1, attempt);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getString(1);
                }
            }
        });
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A perfect play of the stored puzzle, one action every {@code gapMs}. */
    private static ArrayNode perfect(String template, JsonNode puzzle, long gapMs) {
        ArrayNode out = JSON.createArrayNode();
        long[] t = {500};
        java.util.function.BiConsumer<String[], Void> add = (a, unused) -> {
            ObjectNode o = out.addObject();
            o.put("type", a[0]);
            o.put("a", a[1]);
            if (a.length > 2) {
                o.put("b", a[2]);
            }
            o.put("t", t[0]);
            t[0] += gapMs;
        };
        switch (template) {
            case "traversal_race" -> puzzle.get("rounds").forEach(r -> r.get("answer").forEach(v -> add.accept(new String[] {"tap", v.asText()}, null)));
            case "match_pairs" -> puzzle.get("items").forEach(i -> add.accept(new String[] {"match", i.get("id").asText(), i.get("id").asText()}, null));
            default -> puzzle.get("rounds").forEach(r -> {
                add.accept(new String[] {"line", r.get("bugLine").asText()}, null);
                add.accept(new String[] {"class", r.get("bugClass").asText()}, null);
            });
        }
        return out;
    }

    private static HttpResponse<String> finish(Browser b, UUID attempt, ArrayNode actions) {
        ObjectNode body = JSON.createObjectNode();
        body.set("actions", actions);
        return b.send(HttpRequest.newBuilder(URI.create(TestServer.baseUrl() + "/app/games/play/" + attempt + "/finish"))
                .header("Content-Type", "application/json")
                .header("X-CSRF-Token", b.csrf())
                .header("X-Requested-With", "fetch")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())));
    }

    private static int count(String sql, Object... args) {
        return db.tx().read(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) {
                    ps.setObject(i + 1, args[i]);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    @Test
    void everyGameCanBePlayedVerifiedAndTurnedIntoInformalEvidence() {
        String email = newEmail("player");
        Browser b = student(email);
        assertThat(b.get("/app/practice").body()).contains("href=\"/app/games\"");
        String gallery = b.get("/app/games").body();
        assertThat(gallery).contains("Traversal Race").contains("Match Pairs: structures and strengths")
                .contains("Match Pairs: tree words").contains("Bug Hunt").contains("Not played yet");
        String intro = b.get("/app/games/traversal-race").body();
        assertThat(intro).contains("What it trains").contains("Perform a preorder traversal").contains("Relaxed mode");

        Map<String, String> templates = Map.of("traversal-race", "traversal_race", "match-structures", "match_pairs",
                "match-tree-words", "match_pairs", "bug-hunt", "bug_hunt");
        for (var game : templates.entrySet()) {
            UUID attempt = start(b, game.getKey());
            String page = b.get("/app/games/play/" + attempt).body();
            assertThat(page).contains("data-template=\"" + game.getValue() + "\"").contains("data-puzzle=")
                    .contains("/static/js/games/shell.js");
            HttpResponse<String> done = finish(b, attempt, perfect(game.getValue(), puzzle(attempt), 350));
            assertThat(done.statusCode()).as(done.body()).isEqualTo(200);
            assertThat(done.body()).contains("/app/games/result/" + attempt);
            assertThat(count("select count(*) from activity_attempts where id = ? and status = 'completed' and verified", attempt)).isEqualTo(1);
            String result = b.get("/app/games/result/" + attempt).body();
            assertThat(result).contains("Verified").contains("100% right first time").contains("What this showed")
                    .contains("Look back").contains("Practise this");
        }

        // Evidence: only from verified games, informal, light, never official.
        assertThat(count("""
                select count(*) from learning_evidence e join users u on u.id = e.user_id
                where u.email = ? and e.source_kind = 'game' and not e.is_official and e.base_weight = 0.3
                """, email)).isGreaterThanOrEqualTo(4 + 6 + 6 + 5);
        assertThat(count("""
                select count(*) from learning_evidence e join users u on u.id = e.user_id
                where u.email = ? and (e.is_official or e.source_kind <> 'game')
                """, email)).isZero();
        assertThat(b.get("/app/progress").body()).contains("Perform a preorder traversal");
        assertThat(b.get("/app/games").body()).contains("Played 1×");
    }

    @Test
    void tamperedOrUnfinishedGamesAreRejectedAndProduceNothing() {
        String email = newEmail("tamper");
        Browser b = student(email);

        UUID tooFast = start(b, "traversal-race");
        HttpResponse<String> r1 = finish(b, tooFast, perfect("traversal_race", puzzle(tooFast), 5));
        assertThat(r1.statusCode()).isEqualTo(200);
        assertThat(count("select count(*) from activity_attempts where id = ? and status = 'rejected' and reject_reason like '%plausible%'", tooFast))
                .isEqualTo(1);
        assertThat(b.get("/app/games/result/" + tooFast).body()).contains("couldn't verify").contains("timing");

        UUID unfinished = start(b, "bug-hunt");
        ArrayNode partial = perfect("bug_hunt", puzzle(unfinished), 400);
        partial.remove(partial.size() - 1);
        finish(b, unfinished, partial);
        assertThat(b.get("/app/games/result/" + unfinished).body()).contains("finished.");

        UUID forged = start(b, "match-structures");
        ArrayNode fake = JSON.createArrayNode();
        fake.addObject().put("type", "match").put("a", "p1").put("b", "p99").put("t", 600);
        finish(b, forged, fake);
        assertThat(b.get("/app/games/result/" + forged).body()).contains("match the game");

        assertThat(count("select count(*) from learning_evidence e join users u on u.id = e.user_id where u.email = ?", email)).isZero();
        assertThat(count("select count(*) from activity_attempt_items where attempt_id in (?, ?, ?)", tooFast, unfinished, forged)).isZero();

        // A rejected game can't be "finished" again with a good log.
        finish(b, tooFast, perfect("traversal_race", puzzle(tooFast), 350));
        assertThat(count("select count(*) from activity_attempts where id = ? and status = 'rejected'", tooFast)).isEqualTo(1);
    }

    @Test
    void finishingTwiceCountsOnceAndOthersCannotTouchTheAttempt() {
        String email = newEmail("twice");
        Browser b = student(email);
        UUID attempt = start(b, "match-tree-words");
        ArrayNode actions = perfect("match_pairs", puzzle(attempt), 300);
        finish(b, attempt, actions);
        int evidence = count("select count(*) from learning_evidence e join users u on u.id = e.user_id where u.email = ?", email);
        assertThat(evidence).isEqualTo(6);
        finish(b, attempt, actions);
        assertThat(count("select count(*) from learning_evidence e join users u on u.id = e.user_id where u.email = ?", email)).isEqualTo(evidence);
        assertThat(location(b.get("/app/games/play/" + attempt))).isEqualTo("/app/games/result/" + attempt);

        Browser intruder = student(newEmail("intruder"));
        intruder.get("/app/games");
        assertThat(intruder.get("/app/games/play/" + attempt).statusCode()).isEqualTo(404);
        assertThat(intruder.get("/app/games/result/" + attempt).statusCode()).isEqualTo(404);
        intruder.get("/app/games"); // a valid CSRF token, so this tests ownership rather than CSRF
        assertThat(finish(intruder, attempt, actions).statusCode()).isEqualTo(404);
        assertThat(b.get("/app/games/no-such-game").statusCode()).isEqualTo(404);
    }

    @Test
    void gameEvidenceIsCappedPerSkillPerDay() {
        String email = newEmail("cap");
        Browser b = student(email);
        UUID last = null;
        for (int i = 0; i < 13; i++) {
            last = start(b, "traversal-race");
            HttpResponse<String> done = finish(b, last, perfect("traversal_race", puzzle(last), 130));
            assertThat(done.statusCode()).isEqualTo(200);
        }
        assertThat(count("select count(*) from activity_attempts a join users u on u.id = a.user_id where u.email = ? and a.status = 'completed'", email))
                .isEqualTo(13);
        assertThat(count("""
                select count(*) from learning_evidence e join users u on u.id = e.user_id join skills s on s.id = e.skill_id
                where u.email = ? and s.key = 'preorder'
                """, email)).isEqualTo(12);
        assertThat(b.get("/app/games/result/" + last).body()).contains("games count at most 12 times per skill per day");
    }

    @Test
    void apiPlaysTheSameWay() throws Exception {
        String email = newEmail("api-game");
        Browser http = new Browser(TestServer.baseUrl());
        HttpResponse<String> reg = http.send(HttpRequest.newBuilder(URI.create(TestServer.baseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"displayName\":\"Api Player\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")));
        Matcher tm = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(reg.body());
        assertThat(tm.find()).isTrue();
        String token = tm.group(1);

        assertThat(api(http, "GET", "/api/v1/games", token, null).body()).contains("\"key\":\"bug-hunt\"");
        HttpResponse<String> start = api(http, "POST", "/api/v1/games/bug-hunt/start", token, null);
        assertThat(start.statusCode()).isEqualTo(201);
        String attempt = JSON.readTree(start.body()).get("attemptId").asText();
        backdate(UUID.fromString(attempt));
        JsonNode view = JSON.readTree(api(http, "GET", "/api/v1/games/attempts/" + attempt, token, null).body());
        assertThat(view.get("template").asText()).isEqualTo("bug_hunt");
        assertThat(view.get("puzzle").get("rounds")).hasSize(5);

        ObjectNode body = JSON.createObjectNode();
        body.set("actions", perfect("bug_hunt", view.get("puzzle"), 400));
        HttpResponse<String> done = api(http, "POST", "/api/v1/games/attempts/" + attempt + "/finish", token, body.toString());
        assertThat(done.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(done.body()).get("score").asInt()).isEqualTo(100);
        assertThat(JSON.readTree(done.body()).get("status").asText()).isEqualTo("completed");
        assertThat(api(http, "POST", "/api/v1/games/attempts/" + attempt + "/finish", token, "{\"actions\": 5}").statusCode()).isEqualTo(400);
    }

    private static HttpResponse<String> api(Browser http, String method, String path, String token, String json) {
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(TestServer.baseUrl() + path))
                .header("Authorization", "Bearer " + token)
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (json != null) {
            r.header("Content-Type", "application/json");
        }
        return http.send(r);
    }
}
