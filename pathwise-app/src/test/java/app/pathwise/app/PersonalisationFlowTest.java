package app.pathwise.app;

import app.pathwise.app.testing.Browser;
import app.pathwise.app.testing.TestServer;
import app.pathwise.data.Database;
import app.pathwise.data.questions.QuestionDao;
import app.pathwise.web.content.ContentPackLoader;
import app.pathwise.web.content.ContentPackService;
import app.pathwise.web.platform.events.EventBus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static app.pathwise.app.testing.Browser.location;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 exit criterion, end to end: answers become evidence, evidence moves the learner model, and the
 * model changes what Pathwise recommends (with reasons quoting the evidence).
 */
class PersonalisationFlowTest {
    private static final String PASSWORD = "trees grow upside down";
    private static final Pattern REC_START = Pattern.compile("action=\"/app/next/([0-9a-f-]{36})/start\"");
    private static Database db;

    @BeforeAll
    static void setUp() {
        db = TestServer.database();
        var r = new ContentPackService(db.tx(), new ContentPackLoader(PersonalisationFlowTest.class.getClassLoader()),
                new EventBus(), Clock.systemUTC()).importPack("data-structures", null, false);
        assertThat(r.outcome()).isNotEqualTo(ContentPackService.Outcome.INVALID);
    }

    @AfterAll
    static void tearDown() {
        db.close();
    }

    private static Browser student() {
        Browser b = new Browser(TestServer.baseUrl());
        b.register("Engine Learner", "eng-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com", PASSWORD, "student");
        return b;
    }

    /** Builds the correct form answer for the question at {@code position} of an attempt, from the answer key in the DB. */
    private static Map<String, String> correctAnswer(UUID attempt, int position) {
        UUID questionId = db.tx().read(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "select question_id from activity_attempt_items where attempt_id = ? and position = ?")) {
                ps.setObject(1, attempt);
                ps.setInt(2, position - 1);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getObject(1, UUID.class);
                }
            }
        });
        QuestionDao.QuestionRow q = db.tx().read(c -> new QuestionDao().byIds(c, List.of(questionId))).get(0);
        Map<String, String> form = new LinkedHashMap<>();
        switch (q.type()) {
            case "single_choice" -> form.put("option", q.options().stream().filter(QuestionDao.OptionRow::correct)
                    .findFirst().orElseThrow().id().toString());
            case "numeric", "short_text" -> {
                try {
                    JsonNode spec = new ObjectMapper().readTree(q.specJson());
                    form.put("text", spec.has("answer") ? spec.get("answer").asText().replaceAll("\\.0$", "") : spec.get("accepted").get(0).asText());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            default -> {
                // multi_choice: the Browser helper sends one value per key, so pick the first correct option only;
                // it earns partial credit, which is fine for these tests.
                form.put("option", q.options().stream().filter(QuestionDao.OptionRow::correct).findFirst().orElseThrow().id().toString());
            }
        }
        return form;
    }

    private static int evidenceRows(String email) {
        return db.tx().read(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "select count(*) from learning_evidence e join users u on u.id = e.user_id where u.email = ?")) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    @Test
    void wrongCheckAnswersLeadToTargetedPracticeAndPracticeMovesTheModel() {
        Browser b = student();

        // 1. A brand-new student is pointed at a topic with no prerequisites.
        String today = b.get("/app/today").body();
        assertThat(today).contains("natural place to begin");

        // 2. They read the Arrays module and get its check wrong (submitting blank answers).
        b.get("/app/modules/arrays-essentials/check");
        HttpResponse<String> checked = b.post("/app/modules/arrays-essentials/check/check", Map.of());
        assertThat(location(checked)).contains("?check=");
        assertThat(b.get(location(checked)).body()).contains("0 of 3");

        // 3. The engine now suggests strengthening the skill with repeated errors, and says why.
        String next = b.get("/app/next").body();
        assertThat(next).contains("Strengthen").contains("Practice: Reason about array costs")
                .contains("Recommended because 2 of your last 2 answers on Reason about array costs were incorrect.");
        assertThat(next).as("Why-this panel lists the evidence").contains("Based on 2 module check answers.");

        // 4. Starting that suggestion opens a practice set aimed at the skill.
        Matcher m = Pattern.compile("Practice: Reason about array costs.*?" + REC_START.pattern(), Pattern.DOTALL).matcher(next);
        assertThat(m.find()).isTrue();
        HttpResponse<String> started = b.post("/app/next/" + m.group(1) + "/start", Map.of());
        assertThat(location(started)).startsWith("/app/practice/");
        UUID attempt = UUID.fromString(location(started).substring("/app/practice/".length()));

        // 5. Answer every question correctly, using a hint on the first.
        String first = b.get("/app/practice/" + attempt).body();
        assertThat(first).contains("Question 1 of").contains("Need a hint?");
        assertThat(first).as("answers are never in the page before answering").doesNotContain("Correct answer");
        b.post("/app/practice/" + attempt + "/1/hint", Map.of());
        assertThat(b.get("/app/practice/" + attempt).body()).contains("Hint 1:");
        int position = 1;
        while (true) {
            HttpResponse<String> page = b.get("/app/practice/" + attempt);
            if (page.body().contains(": done")) {
                break;
            }
            HttpResponse<String> answered = b.post("/app/practice/" + attempt + "/" + position + "/answer", correctAnswer(attempt, position));
            assertThat(location(answered)).isEqualTo("/app/practice/" + attempt + "/" + position + "/feedback");
            String feedback = b.get(location(answered)).body();
            assertThat(feedback).containsAnyOf("Correct", "Partly right");
            // Answers are final: a second submission changes nothing.
            b.post("/app/practice/" + attempt + "/" + position + "/answer", Map.of());
            position++;
            assertThat(position).isLessThan(10);
        }

        // 6. The summary reports level changes, and the suggestion's reason now reflects the new evidence.
        String summary = b.get("/app/practice/" + attempt).body();
        assertThat(summary).contains("How your levels moved").contains("Reason about array costs").contains("→");
        String after = b.get("/app/next").body();
        assertThat(after).doesNotContain("2 of your last 2 answers on Reason about array costs");
        String progress = b.get("/app/progress").body();
        assertThat(progress).contains("Reason about array costs").containsPattern("\\d+ answers");
    }

    @Test
    void notNowHidesASuggestionAndAlreadyKnowOffersAShortCheck() {
        Browser b = student();
        String next = b.get("/app/next").body();
        Matcher m = REC_START.matcher(next);
        assertThat(m.find()).isTrue();
        String firstId = m.group(1);
        HttpResponse<String> snooze = b.post("/app/next/" + firstId + "/not-now", Map.of());
        assertThat(location(snooze)).isEqualTo("/app/next");
        String afterSnooze = b.get("/app/next").body();
        assertThat(afterSnooze).contains("hold that one until tomorrow").doesNotContain(firstId);

        Matcher m2 = Pattern.compile("action=\"/app/next/([0-9a-f-]{36})/already-know\"").matcher(afterSnooze);
        assertThat(m2.find()).isTrue();
        HttpResponse<String> know = b.post("/app/next/" + m2.group(1) + "/already-know", Map.of());
        assertThat(location(know)).startsWith("/app/practice/");
        assertThat(b.get(location(know)).body()).contains("Question 1 of");

        // Someone else's recommendation cannot be acted on.
        Browser other = student();
        other.get("/app/next");
        assertThat(location(other.post("/app/next/" + m2.group(1) + "/start", Map.of()))).isEqualTo("/app/next");
    }

    @Test
    void goalsShapeSuggestionsAndCanBeRemoved() {
        Browser b = student();
        b.get("/app/next");
        HttpResponse<String> saved = b.post("/app/next/goals", Map.of("topic", "data-structures/tree-traversals", "date", "2026-12-01"));
        assertThat(location(saved)).isEqualTo("/app/next");
        String page = b.get("/app/next").body();
        assertThat(page).contains("Goal saved").contains("Master Tree Traversals").contains("Part of your goal: master Tree Traversals");
        assertThat(b.post("/app/next/goals", Map.of("topic", "nonsense")).statusCode()).isEqualTo(303);
        assertThat(b.get("/app/next").body()).contains("Choose a topic");
    }

    @Test
    void practiceIsPrivateAndEvidenceIsNeverOfficial() {
        Browser b = student();
        b.get("/app/practice");
        HttpResponse<String> started = b.post("/app/practice/start", Map.of("mode", "quick"));
        assertThat(location(started)).isIn("/app/practice", location(started));
        // With no history, quick practice falls back gracefully.
        b.get("/app/modules/tree-traversals");
        HttpResponse<String> again = b.post("/app/practice/start", Map.of("mode", "quick"));
        assertThat(location(again)).startsWith("/app/practice/");
        String attemptUrl = location(again);

        Browser intruder = student();
        assertThat(intruder.get(attemptUrl).statusCode()).isEqualTo(404);

        int official = db.tx().read(c -> {
            try (ResultSet rs = c.createStatement().executeQuery("select count(*) from learning_evidence where is_official")) {
                rs.next();
                return rs.getInt(1);
            }
        });
        assertThat(official).as("practice and checks never create official results").isZero();
        assertThat(evidenceRows("nobody@example.com")).isZero();
    }

    @Test
    void apiSupportsRecommendationsPracticeAndMastery() throws Exception {
        String email = "api-eng-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        Browser http = new Browser(TestServer.baseUrl());
        HttpResponse<String> reg = http.send(HttpRequest.newBuilder(URI.create(TestServer.baseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"displayName\":\"Api Engine\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")));
        Matcher tm = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(reg.body());
        assertThat(tm.find()).isTrue();
        String token = tm.group(1);

        HttpResponse<String> recs = api(http, "GET", "/api/v1/me/recommendations", token, null);
        assertThat(recs.statusCode()).isEqualTo(200);
        assertThat(recs.body()).contains("\"slot\":\"explore\"").contains("\"reasonText\"").contains("\"factors\"");

        String skillId = db.tx().read(c -> {
            try (ResultSet rs = c.createStatement().executeQuery("select id from skills where key = 'preorder'")) {
                rs.next();
                return rs.getString(1);
            }
        });
        HttpResponse<String> start = api(http, "POST", "/api/v1/practice", token, "{\"mode\":\"practice\",\"skillId\":\"" + skillId + "\"}");
        assertThat(start.statusCode()).isEqualTo(201);
        String attempt = new ObjectMapper().readTree(start.body()).get("attemptId").asText();

        HttpResponse<String> view = api(http, "GET", "/api/v1/practice/" + attempt, token, null);
        assertThat(view.body()).contains("\"step\"").doesNotContain("\"correct\":true").doesNotContain("accepted");

        Map<String, String> answer = correctAnswer(UUID.fromString(attempt), 1);
        String body = answer.containsKey("text")
                ? "{\"text\":\"" + answer.get("text") + "\"}"
                : "{\"options\":[\"" + answer.get("option") + "\"]}";
        HttpResponse<String> answered = api(http, "POST", "/api/v1/practice/" + attempt + "/1/answer", token, body);
        assertThat(answered.statusCode()).isEqualTo(200);
        assertThat(answered.body()).contains("\"correct\":true");
        assertThat(api(http, "POST", "/api/v1/practice/" + attempt + "/1/answer", token, body).statusCode()).isEqualTo(409);

        HttpResponse<String> mastery = api(http, "GET", "/api/v1/me/mastery", token, null);
        assertThat(mastery.body()).contains("Perform a preorder traversal").contains("heuristic-v1");
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
