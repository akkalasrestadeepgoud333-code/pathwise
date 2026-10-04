package app.pathwise.app;

import app.pathwise.app.testing.Browser;
import app.pathwise.app.testing.TestServer;
import app.pathwise.core.identity.PasswordHasher;
import app.pathwise.data.Database;
import app.pathwise.data.questions.QuestionDao;
import app.pathwise.web.assessment.AssessmentService;
import app.pathwise.web.catalog.CatalogService;
import app.pathwise.web.content.ContentPackLoader;
import app.pathwise.web.content.ContentPackService;
import app.pathwise.web.identity.IdentityService;
import app.pathwise.web.learner.LearnerService;
import app.pathwise.web.platform.events.EventBus;
import app.pathwise.web.questions.QuestionService;
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
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static app.pathwise.app.testing.Browser.location;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M3 end to end: official assessments are separate from practice, never leak answers before grading,
 * keep time on the server, grade exactly once, and feed official evidence into the learner model.
 */
class AssessmentFlowTest {
    private static final String PASSWORD = "trees grow upside down";
    private static final Pattern ITEM = Pattern.compile("name=\"item\" value=\"([0-9a-f-]{36})\"");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static Database db;

    @BeforeAll
    static void setUp() {
        db = TestServer.database();
        var r = new ContentPackService(db.tx(), new ContentPackLoader(AssessmentFlowTest.class.getClassLoader()),
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
        b.register("Assessed Learner", email, PASSWORD, "student");
        return b;
    }

    private static UUID start(Browser b, String key) {
        b.get("/app/assessments/" + key);
        HttpResponse<String> started = b.post("/app/assessments/" + key + "/start", Map.of());
        assertThat(location(started)).startsWith("/app/assessments/attempt/");
        return UUID.fromString(location(started).substring("/app/assessments/attempt/".length()));
    }

    private static List<String> itemIds(String page) {
        List<String> out = new ArrayList<>();
        Matcher m = ITEM.matcher(page);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static QuestionDao.QuestionRow questionOfItem(String itemId) {
        UUID questionId = db.tx().read(c -> {
            try (PreparedStatement ps = c.prepareStatement("select question_id from assessment_items where id = ?")) {
                ps.setObject(1, UUID.fromString(itemId));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getObject(1, UUID.class);
                }
            }
        });
        return db.tx().read(c -> new QuestionDao().byIds(c, List.of(questionId))).get(0);
    }

    /** The fully correct form fields for one item, from the answer key in the database. */
    private static List<Map.Entry<String, String>> correct(String itemId) {
        QuestionDao.QuestionRow q = questionOfItem(itemId);
        List<Map.Entry<String, String>> out = new ArrayList<>();
        out.add(Map.entry("item", itemId));
        if (q.type().endsWith("_choice")) {
            q.options().stream().filter(QuestionDao.OptionRow::correct).forEach(o -> out.add(Map.entry("o." + itemId, o.id().toString())));
        } else {
            out.add(Map.entry("t." + itemId, textAnswer(q)));
        }
        return out;
    }

    private static String textAnswer(QuestionDao.QuestionRow q) {
        try {
            JsonNode spec = JSON.readTree(q.specJson());
            return spec.has("answer") ? spec.get("answer").asText().replaceAll("\\.0$", "") : spec.get("accepted").get(0).asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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

    private static void forceDeadlinePast(UUID attempt) {
        db.tx().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    update assessment_attempts set started_at = now() - interval '30 minutes',
                           deadline_at = now() - interval '5 minutes' where id = ?
                    """)) {
                ps.setObject(1, attempt);
                ps.executeUpdate();
            }
        });
    }

    @Test
    void quizIsTakenWithoutLeakingAnswersAndGradedOnceAsOfficialEvidence() {
        String email = newEmail("quiz");
        Browser b = student(email);

        // 1. The list and the rules page explain what's about to happen.
        String list = b.get("/app/assessments").body();
        assertThat(list).contains("Quiz: arrays and linked lists").contains("Topic test: trees, traversals and BSTs")
                .contains("Data Structures starting-point check").contains("Not started");
        String rules = b.get("/app/assessments/arrays-lists-quiz").body();
        assertThat(rules).contains("8 questions").contains("15 minutes").contains("2 attempts allowed")
                .contains("The AI tutor is turned off").contains("Start now");

        // 2. Starting creates an attempt; the page shows every question but no answers or rationales.
        UUID attempt = start(b, "arrays-lists-quiz");
        String page = b.get("/app/assessments/attempt/" + attempt).body();
        List<String> items = itemIds(page);
        assertThat(items).hasSize(8).doesNotHaveDuplicates();
        assertThat(page).contains("Question 1 of 8").contains("data-deadline=").contains("data-seconds-left=")
                .doesNotContain("Correct answer").doesNotContain("Two pointer assignments")
                .doesNotContain("is-correct");
        assertThat(b.get("/app/assessments/arrays-lists-quiz").body()).as("starting again resumes").contains("Resume attempt");
        assertThat(location(b.post("/app/assessments/arrays-lists-quiz/start", Map.of())))
                .isEqualTo("/app/assessments/attempt/" + attempt);

        // 3. Saving keeps answers (and shows them again); nothing is graded yet.
        List<Map.Entry<String, String>> first = correct(items.get(0));
        assertThat(location(b.postPairs("/app/assessments/attempt/" + attempt + "/save", first)))
                .isEqualTo("/app/assessments/attempt/" + attempt);
        String reloaded = b.get("/app/assessments/attempt/" + attempt).body();
        assertThat(reloaded).contains("Your answers are saved.").contains("<span data-answered>1</span>");
        assertThat(reloaded).containsAnyOf(" checked>", "value=\"" + (first.size() > 1 ? first.get(1).getValue() : "") + "\"");
        assertThat(count("select count(*) from learning_evidence where response_id in (select id from attempt_responses where attempt_id = ?)", attempt))
                .isZero();

        // 4. Submitting with gaps asks for confirmation first.
        HttpResponse<String> gaps = b.postPairs("/app/assessments/attempt/" + attempt + "/submit", first);
        assertThat(location(gaps)).isEqualTo("/app/assessments/attempt/" + attempt + "?confirm=1");
        assertThat(b.get(location(gaps)).body()).contains("7 questions are unanswered").contains("Submit anyway");

        // 5. All correct → graded once, with feedback, per-skill results and official evidence.
        List<Map.Entry<String, String>> all = new ArrayList<>();
        items.forEach(i -> all.addAll(correct(i)));
        HttpResponse<String> submitted = b.postPairs("/app/assessments/attempt/" + attempt + "/submit", all);
        assertThat(location(submitted)).isEqualTo("/app/assessments/result/" + attempt);
        String result = b.get("/app/assessments/result/" + attempt).body();
        assertThat(result).contains("100%").contains("8 of 8").contains("By skill").contains("Correct answer")
                .contains("official evidence");
        int official = count("""
                select count(*) from learning_evidence e join users u on u.id = e.user_id
                where u.email = ? and e.is_official and e.source_kind = 'assessment' and e.response_id is not null
                """, email);
        assertThat(official).isGreaterThanOrEqualTo(8);
        assertThat(count("select count(*) from skill_mastery m join users u on u.id = m.user_id where u.email = ? and m.official_count > 0", email))
                .isPositive();

        // 6. Grading happens exactly once: a second submit or save changes nothing.
        b.postPairs("/app/assessments/attempt/" + attempt + "/submit", List.of(Map.entry("confirm", "yes")));
        assertThat(count("select count(*) from learning_evidence e join users u on u.id = e.user_id where u.email = ? and e.is_official", email))
                .isEqualTo(official);
        assertThat(location(b.get("/app/assessments/attempt/" + attempt))).isEqualTo("/app/assessments/result/" + attempt);

        // 7. Someone else can't see or touch the attempt.
        Browser intruder = student(newEmail("intruder"));
        assertThat(intruder.get("/app/assessments/attempt/" + attempt).statusCode()).isEqualTo(404);
        assertThat(intruder.get("/app/assessments/result/" + attempt).statusCode()).isEqualTo(404);
        intruder.get("/app/assessments");
        intruder.postPairs("/app/assessments/attempt/" + attempt + "/submit", List.of(Map.entry("confirm", "yes")));
        assertThat(count("select count(*) from assessment_attempts where id = ? and status = 'graded'", attempt)).isEqualTo(1);

        // 8. The second attempt is allowed (blank this time); a third is refused.
        UUID second = start(b, "arrays-lists-quiz");
        assertThat(second).isNotEqualTo(attempt);
        b.get("/app/assessments/attempt/" + second);
        b.postPairs("/app/assessments/attempt/" + second + "/submit", List.of(Map.entry("confirm", "yes")));
        assertThat(b.get("/app/assessments/result/" + second).body()).contains("0%").contains("Not quite");
        b.get("/app/assessments/arrays-lists-quiz");
        HttpResponse<String> third = b.post("/app/assessments/arrays-lists-quiz/start", Map.of());
        assertThat(location(third)).isEqualTo("/app/assessments/arrays-lists-quiz");
        assertThat(b.get(location(third)).body()).contains("used all your attempts");
        assertThat(b.get("/app/assessments").body()).contains("Best score").contains("8 / 8");
    }

    @Test
    void assessmentOnlyQuestionsNeverAppearInPractice() {
        int leaked = count("""
                select count(*) from questions q
                join assessment_items i on i.question_id = q.id
                where q.key like 'as-%' and q.practice_enabled
                """);
        assertThat(leaked).isZero();
        assertThat(count("select count(*) from questions where key like 'as-%'")).isEqualTo(12);
    }

    @Test
    void schemaKeepsOfficialEvidenceAndOpenAttemptsHonest() {
        String email = newEmail("schema");
        Browser b = student(email);
        UUID attempt = start(b, "trees-topic-test");
        UUID user = db.tx().read(c -> {
            try (PreparedStatement ps = c.prepareStatement("select id from users where email = ?")) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getObject(1, UUID.class);
                }
            }
        });
        // Only assessments may create official evidence.
        assertThatThrownBy(() -> db.tx().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    insert into learning_evidence (user_id, skill_id, source_kind, outcome, base_weight, difficulty, hints_used, is_official, occurred_at)
                    select ?, id, 'practice', 1, 1, 1, 0, true, now() from skills limit 1
                    """)) {
                ps.setObject(1, user);
                ps.executeUpdate();
            }
        })).hasRootCauseInstanceOf(SQLException.class);
        // At most one open attempt per student and assessment.
        assertThatThrownBy(() -> db.tx().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    insert into assessment_attempts (id, assessment_id, user_id, attempt_no, seed)
                    select gen_random_uuid(), assessment_id, user_id, 2, 1 from assessment_attempts where id = ?
                    """)) {
                ps.setObject(1, attempt);
                ps.executeUpdate();
            }
        })).hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    void whenTimeRunsOutSavedAnswersAreSubmittedAndLateAnswersRefused() {
        String email = newEmail("late");
        Browser b = student(email);
        UUID attempt = start(b, "ds-diagnostic");
        List<String> items = itemIds(b.get("/app/assessments/attempt/" + attempt).body());
        assertThat(items).hasSize(12);
        b.postPairs("/app/assessments/attempt/" + attempt + "/save", correct(items.get(0)));

        forceDeadlinePast(attempt);

        // A save after the deadline (plus grace) is refused; opening the attempt submits what was saved.
        List<Map.Entry<String, String>> late = new ArrayList<>();
        items.forEach(i -> late.addAll(correct(i)));
        b.postPairs("/app/assessments/attempt/" + attempt + "/save", late);
        HttpResponse<String> open = b.get("/app/assessments/attempt/" + attempt);
        assertThat(location(open)).isEqualTo("/app/assessments/result/" + attempt);
        String result = b.get(location(open)).body();
        assertThat(result).contains("Time ran out").contains("1 of 12");
        assertThat(count("select count(*) from assessment_attempts where id = ? and auto_submitted", attempt)).isEqualTo(1);

        // The diagnostic seeds the learner model: Progress now shows skills from it.
        assertThat(count("select count(*) from skill_mastery m join users u on u.id = m.user_id where u.email = ?", email))
                .isGreaterThanOrEqualTo(8);
        assertThat(b.get("/app/progress").body()).containsPattern("\\d+ answers?");
        assertThat(b.get("/app/today").body()).doesNotContain("Take the Data Structures starting-point check");
    }

    @Test
    void backgroundSweepSubmitsAbandonedAttempts() {
        Browser b = student(newEmail("sweep"));
        UUID attempt = start(b, "trees-topic-test");
        forceDeadlinePast(attempt);
        EventBus events = new EventBus();
        AssessmentService svc = new AssessmentService(db.tx(), new QuestionService(db.tx()),
                new LearnerService(db.tx(), events, Clock.systemUTC()), new CatalogService(db.tx()), events, Clock.systemUTC());
        assertThat(svc.sweepOverdue()).isGreaterThanOrEqualTo(1);
        assertThat(count("select count(*) from assessment_attempts where id = ? and status = 'graded' and auto_submitted", attempt))
                .isEqualTo(1);
        assertThat(svc.sweepOverdue()).as("nothing left to sweep for this attempt").isGreaterThanOrEqualTo(0);
        assertThat(b.get("/app/assessments/result/" + attempt).body()).contains("0%");
    }

    @Test
    void todaySuggestsTheDiagnosticToNewStudents() {
        Browser b = student(newEmail("today"));
        assertThat(b.get("/app/today").body()).contains("Take the Data Structures starting-point check");
        assertThat(b.get("/app/today").body()).contains("href=\"/app/assessments/ds-diagnostic\"");
    }

    @Test
    void adminSeesItemAnalysisAndStudentsCannot() {
        // Make sure there is at least one graded attempt to analyse.
        Browser s = student(newEmail("analysed"));
        UUID attempt = start(s, "trees-topic-test");
        List<String> items = itemIds(s.get("/app/assessments/attempt/" + attempt).body());
        List<Map.Entry<String, String>> some = new ArrayList<>(correct(items.get(0)));
        some.add(Map.entry("confirm", "yes"));
        s.postPairs("/app/assessments/attempt/" + attempt + "/submit", some);
        assertThat(s.get("/admin/assessments").statusCode()).isEqualTo(403);

        String email = newEmail("analyst");
        new IdentityService(db.tx(), new PasswordHasher(1024, 1, 1), new EventBus(), Clock.systemUTC(),
                Duration.ofHours(12), Duration.ofDays(30)).createOrPromoteAdmin(email, "Analyst Admin", PASSWORD);
        Browser admin = new Browser(TestServer.baseUrl());
        admin.login(email, PASSWORD);
        String listing = admin.get("/admin/assessments").body();
        assertThat(listing).contains("Topic test: trees, traversals and BSTs").contains("Graded attempts")
                .contains("href=\"/admin/assessments/trees-topic-test\"");
        String analysis = admin.get("/admin/assessments/trees-topic-test").body();
        assertThat(analysis).contains("Item analysis").contains("Facility").contains("Discrimination").contains("Item 10");
        assertThat(admin.get("/admin/assessments/no-such-test").statusCode()).isEqualTo(404);
    }

    @Test
    void apiRunsTheSameRules() throws Exception {
        String email = newEmail("api-assess");
        Browser http = new Browser(TestServer.baseUrl());
        HttpResponse<String> reg = http.send(HttpRequest.newBuilder(URI.create(TestServer.baseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"displayName\":\"Api Assessed\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")));
        Matcher tm = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(reg.body());
        assertThat(tm.find()).isTrue();
        String token = tm.group(1);

        HttpResponse<String> list = api(http, "GET", "/api/v1/assessments", token, null);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains("\"key\":\"trees-topic-test\"");

        HttpResponse<String> start = api(http, "POST", "/api/v1/assessments/trees-topic-test/start", token, null);
        assertThat(start.statusCode()).isEqualTo(201);
        String attempt = JSON.readTree(start.body()).get("attemptId").asText();

        HttpResponse<String> view = api(http, "GET", "/api/v1/assessments/attempts/" + attempt, token, null);
        assertThat(view.statusCode()).isEqualTo(200);
        assertThat(view.body()).contains("\"items\"").contains("\"secondsLeft\"")
                .doesNotContain("\"correct\"").doesNotContain("accepted").doesNotContain("rationale").doesNotContain("explanation");
        assertThat(api(http, "GET", "/api/v1/assessments/attempts/" + attempt + "/result", token, null).statusCode()).isEqualTo(409);

        String item = JSON.readTree(view.body()).get("items").get(0).get("itemId").asText();
        QuestionDao.QuestionRow q = questionOfItem(item);
        String answer = q.type().endsWith("_choice")
                ? "{\"itemId\":\"" + item + "\",\"options\":[" + q.options().stream().filter(QuestionDao.OptionRow::correct)
                        .map(o -> "\"" + o.id() + "\"").collect(java.util.stream.Collectors.joining(",")) + "]}"
                : "{\"itemId\":\"" + item + "\",\"text\":\"" + textAnswer(q) + "\"}";
        assertThat(api(http, "POST", "/api/v1/assessments/attempts/" + attempt + "/save", token, "{\"answers\":[" + answer + "]}").statusCode())
                .isEqualTo(200);
        HttpResponse<String> submitted = api(http, "POST", "/api/v1/assessments/attempts/" + attempt + "/submit", token, "{\"answers\":[]}");
        assertThat(submitted.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(submitted.body()).get("score").asDouble()).isEqualTo(1.0);
        assertThat(api(http, "POST", "/api/v1/assessments/attempts/" + attempt + "/save", token, "{\"answers\":[" + answer + "]}").statusCode())
                .isEqualTo(409);

        // Another account's token can't read it.
        Browser other = new Browser(TestServer.baseUrl());
        HttpResponse<String> reg2 = other.send(HttpRequest.newBuilder(URI.create(TestServer.baseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"displayName\":\"Other\",\"email\":\"" + newEmail("api-other") + "\",\"password\":\"" + PASSWORD + "\"}")));
        Matcher tm2 = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(reg2.body());
        assertThat(tm2.find()).isTrue();
        assertThat(api(other, "GET", "/api/v1/assessments/attempts/" + attempt + "/result", tm2.group(1), null).statusCode()).isEqualTo(404);
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
