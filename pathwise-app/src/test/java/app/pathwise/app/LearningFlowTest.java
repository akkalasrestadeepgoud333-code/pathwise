package app.pathwise.app;

import app.pathwise.app.testing.Browser;
import app.pathwise.app.testing.TestServer;
import app.pathwise.data.Database;
import app.pathwise.web.content.ContentPackLoader;
import app.pathwise.web.content.ContentPackService;
import app.pathwise.web.platform.events.EventBus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.ResultSet;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static app.pathwise.app.testing.Browser.location;
import static org.assertj.core.api.Assertions.assertThat;

/** M1 end to end: content import → subjects → Learning Map → topic → module player → completion. */
class LearningFlowTest {
    private static final String PASSWORD = "trees grow upside down";
    private static Database db;
    private static ContentPackService packs;

    @BeforeAll
    static void importContent() {
        db = TestServer.database();
        packs = new ContentPackService(db.tx(), new ContentPackLoader(LearningFlowTest.class.getClassLoader()),
                new EventBus(), Clock.systemUTC());
        var result = packs.importPack("data-structures", null, false);
        assertThat(result.outcome()).isIn(ContentPackService.Outcome.IMPORTED, ContentPackService.Outcome.UNCHANGED);
    }

    @AfterAll
    static void close() {
        db.close();
    }

    private static Browser student() {
        Browser b = new Browser(TestServer.baseUrl());
        b.register("Map Learner", "learn-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com", PASSWORD, "student");
        return b;
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    @Test
    void reimportingAnUnchangedPackIsANoOp() {
        assertThat(packs.importPack("data-structures", null, false).outcome()).isEqualTo(ContentPackService.Outcome.UNCHANGED);
    }

    @Test
    void subjectsAndTheLearningMapRender() {
        Browser b = student();
        HttpResponse<String> learn = b.get("/app/learn");
        assertThat(learn.statusCode()).isEqualTo(200);
        assertThat(learn.body()).contains("Data Structures").contains("13 topics").contains("href=\"/app/map/data-structures\"");

        HttpResponse<String> map = b.get("/app/map/data-structures");
        assertThat(map.statusCode()).isEqualTo(200);
        String body = map.body();
        assertThat(count(body, "class=\"map-node ")).isEqualTo(13);
        // Regression: Jasper drops whitespace-only text between two tags, which once broke these attributes.
        assertThat(body).containsPattern("viewBox=\"0 0 \\d+ \\d+\"").containsPattern("transform=\"translate\\(\\d+ \\d+\\)\"");
        assertThat(body).contains("href=\"/app/topics/data-structures/tree-traversals\"");
        assertThat(body).contains("class=\"map-edge recommended\"").contains("class=\"map-edge\"");
        assertThat(body).as("trees builds on recursion, which isn't studied").contains("Builds on Recursion");
        assertThat(body).as("auto view renders the trail too, for phones").contains("Stage 1");

        HttpResponse<String> list = b.get("/app/map/data-structures?view=list");
        assertThat(list.body()).contains("Stage 1").doesNotContain("class=\"map-node ");
        HttpResponse<String> graph = b.get("/app/map/data-structures?view=graph");
        assertThat(graph.body()).contains("class=\"map-node ").doesNotContain("Stage 1</div>");

        assertThat(b.get("/app/map/no-such-subject").statusCode()).isEqualTo(404);
    }

    @Test
    void topicPageExplainsPrerequisitesAndModules() {
        Browser b = student();
        HttpResponse<String> topic = b.get("/app/topics/data-structures/tree-traversals");
        assertThat(topic.statusCode()).isEqualTo(200);
        assertThat(topic.body())
                .contains("Tree Traversals")
                .contains("Perform a preorder traversal")
                .contains("Binary Trees").contains("Needed first")
                .contains("Stacks and Queues").contains("Helpful first")
                .contains("This topic builds on")
                .contains("href=\"/app/modules/tree-traversals\"")
                .contains("Start learning");
        assertThat(b.get("/app/topics/data-structures/nope").statusCode()).isEqualTo(404);
    }

    @Test
    void studentWorksThroughAModuleToCompletion() throws Exception {
        Browser b = student();
        HttpResponse<String> first = b.get("/app/modules/tree-traversals");
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body()).contains("Why traversal order matters").contains("Section 1 of 9").contains("<table>");

        String path = "/app/modules/tree-traversals/overview";
        int guard = 0;
        boolean sawVisual = false;
        boolean sawCards = false;
        HttpResponse<String> done = null;
        while (guard++ < 20) {
            HttpResponse<String> page = b.get(path);
            assertThat(page.statusCode()).isEqualTo(200);
            sawVisual |= page.body().contains("data-visual data-spec=");
            sawCards |= page.body().contains("class=\"flashcard\"");
            String section = path.substring(path.lastIndexOf('/') + 1);
            done = b.post("/app/modules/tree-traversals/" + section + "/done", Map.of());
            assertThat(done.statusCode()).isEqualTo(303);
            if (location(done).contains("completed=1")) {
                break;
            }
            path = location(done);
        }
        assertThat(sawVisual).as("interactive traversal visual rendered").isTrue();
        assertThat(sawCards).as("flashcards rendered").isTrue();
        assertThat(location(done)).contains("?completed=1");

        HttpResponse<String> finished = b.get(location(done));
        assertThat(finished.body()).contains("Module complete").contains("progress-success");

        HttpResponse<String> topic = b.get("/app/topics/data-structures/tree-traversals");
        assertThat(topic.body()).contains("Studied").contains("Review this topic");

        // Completion published exactly one event.
        int events = db.tx().read(c -> {
            try (ResultSet rs = c.createStatement().executeQuery(
                    "select count(*) from domain_events where type = 'content.module_completed' and payload->>'moduleKey' = 'tree-traversals'")) {
                rs.next();
                return rs.getInt(1);
            }
        });
        assertThat(events).isGreaterThanOrEqualTo(1);
        b.post("/app/modules/tree-traversals/overview/done", Map.of());
        int after = db.tx().read(c -> {
            try (ResultSet rs = c.createStatement().executeQuery(
                    "select count(*) from domain_events where type = 'content.module_completed' and payload->>'moduleKey' = 'tree-traversals'")) {
                rs.next();
                return rs.getInt(1);
            }
        });
        assertThat(after).as("redoing a section does not complete the module again").isEqualTo(events);
    }

    @Test
    void todaySuggestsAStartingPointWithAReasonThenOffersToContinue() {
        Browser b = student();
        HttpResponse<String> today = b.get("/app/today");
        assertThat(today.body()).contains("Suggested next step").contains("Why this?")
                .contains("Arrays has no prerequisites, so it&#039;s a natural place to begin.");
        b.get("/app/modules/linked-lists-essentials");
        HttpResponse<String> again = b.get("/app/today");
        assertThat(again.body()).contains("Continue where you left off").contains("Linked lists: nodes and pointers");
    }

    @Test
    void sectionAndModuleUrlsAreValidated() {
        Browser b = student();
        assertThat(b.get("/app/modules/no-such-module").statusCode()).isEqualTo(404);
        assertThat(b.get("/app/modules/tree-traversals/no-such-section").statusCode()).isEqualTo(404);
        assertThat(b.get("/app/modules/BAD..KEY").statusCode()).isEqualTo(404);
        b.get("/app/modules/tree-traversals");
        assertThat(b.post("/app/modules/tree-traversals/nope/done", Map.of()).statusCode()).isEqualTo(404);
        assertThat(new Browser(TestServer.baseUrl()).get("/app/modules/tree-traversals").statusCode())
                .as("sign-in required").isEqualTo(303);
    }

    @Test
    void learningApiServesSubjectsMapsAndProgress() {
        String email = "api-learn-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        Browser http = new Browser(TestServer.baseUrl());
        HttpResponse<String> reg = http.send(HttpRequest.newBuilder(URI.create(TestServer.baseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"displayName\":\"Api Learner\",\"email\":\"" + email
                        + "\",\"password\":\"" + PASSWORD + "\"}")));
        Matcher m = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(reg.body());
        assertThat(m.find()).isTrue();
        String token = m.group(1);

        HttpResponse<String> subjects = api(http, "GET", "/api/v1/subjects", token);
        assertThat(subjects.statusCode()).isEqualTo(200);
        assertThat(subjects.body()).contains("\"key\":\"data-structures\"");

        HttpResponse<String> map = api(http, "GET", "/api/v1/subjects/data-structures/map", token);
        assertThat(map.body()).contains("\"nodes\":[").contains("\"edges\":[").contains("\"coverageLabel\"");

        HttpResponse<String> module = api(http, "GET", "/api/v1/modules/bst-essentials", token);
        assertThat(module.statusCode()).isEqualTo(200);
        assertThat(module.body()).contains("\"moduleTitle\":\"Binary search trees: search, insert, delete\"");

        HttpResponse<String> complete = api(http, "POST", "/api/v1/modules/bst-essentials/overview/complete", token);
        assertThat(complete.statusCode()).isEqualTo(200);
        assertThat(complete.body()).contains("\"nextKey\":\"explanation\"").contains("\"moduleCompletedNow\":false");

        assertThat(api(http, "GET", "/api/v1/modules/nope", token).statusCode()).isEqualTo(404);
        assertThat(api(http, "GET", "/api/v1/subjects", null).statusCode()).isEqualTo(401);
    }

    private static HttpResponse<String> api(Browser http, String method, String path, String token) {
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(TestServer.baseUrl() + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            r.header("Authorization", "Bearer " + token);
        }
        return http.send(r);
    }

    @Test
    void adminSeesPackStatusAndStudentsCannot() {
        Browser s = student();
        assertThat(s.get("/admin/content").statusCode()).isEqualTo(403);
        String email = "content-admin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        new app.pathwise.web.identity.IdentityService(db.tx(), new app.pathwise.core.identity.PasswordHasher(1024, 1, 1),
                new EventBus(), Clock.systemUTC(), java.time.Duration.ofHours(1), java.time.Duration.ofDays(1))
                .createOrPromoteAdmin(email, "Content Admin", "an admin passphrase 2026");
        Browser admin = new Browser(TestServer.baseUrl());
        admin.login(email, "an admin passphrase 2026");
        HttpResponse<String> page = admin.get("/admin/content");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("data-structures").contains("Up to date");
        HttpResponse<String> reimport = admin.post("/admin/content", Map.of("pack", "data-structures"));
        assertThat(location(reimport)).isEqualTo("/admin/content");
        assertThat(admin.get("/admin/content").body()).contains("already up to date");
    }
}
