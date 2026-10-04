package app.pathwise.app;

import app.pathwise.app.testing.Browser;
import app.pathwise.app.testing.TestServer;
import app.pathwise.core.identity.PasswordHasher;
import app.pathwise.data.Database;
import app.pathwise.web.content.ContentPackLoader;
import app.pathwise.web.content.ContentPackService;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static app.pathwise.app.testing.Browser.location;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M6 end to end: the anonymous doubt network (with a leak scan over pages and the API), private doubts to a
 * room's teachers, answers, votes, acceptance, verification, reports, moderation, identity reveal, blocking,
 * duplicate suggestions and the knowledge base.
 */
class DoubtFlowTest {
    private static final String PASSWORD = "trees grow upside down";
    private static Database db;

    @BeforeAll
    static void setUp() {
        db = TestServer.database();
        var r = new ContentPackService(db.tx(), new ContentPackLoader(DoubtFlowTest.class.getClassLoader()),
                new EventBus(), Clock.systemUTC()).importPack("data-structures", null, false);
        assertThat(r.outcome()).isNotEqualTo(ContentPackService.Outcome.INVALID);
    }

    @AfterAll
    static void tearDown() {
        db.close();
    }

    private static String uniq() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Person(Browser b, String name, String email, String id) {}

    private static Person person(String name, String role) {
        String email = "d-" + uniq() + "@example.com";
        Browser b = new Browser(TestServer.baseUrl());
        b.register(name, email, PASSWORD, role);
        return new Person(b, name, email, one("select id from users where email = ?", email));
    }

    private static Person admin() {
        String email = "mod-" + uniq() + "@example.com";
        new IdentityService(db.tx(), new PasswordHasher(1024, 1, 1), new EventBus(), Clock.systemUTC(), Duration.ofHours(12),
                Duration.ofDays(30)).createOrPromoteAdmin(email, "Mod Erator", PASSWORD);
        Browser b = new Browser(TestServer.baseUrl());
        b.login(email, PASSWORD);
        return new Person(b, "Mod Erator", email, one("select id from users where email = ?", email));
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

    private static String ask(Browser b, String room, String title, String body, String topic, boolean showName) {
        b.get("/app/doubts/ask");
        Map<String, String> form = new java.util.LinkedHashMap<>(Map.of("action", "post", "room", room, "category", "concept",
                "title", title, "body", body, "topic", topic));
        if (showName) {
            form.put("showName", "1");
        }
        HttpResponse<String> r = b.post("/app/doubts/ask", form);
        assertThat(location(r)).as(r.body()).startsWith("/app/doubts/");
        return location(r).substring("/app/doubts/".length());
    }

    private static void act(Browser b, String thread, Map<String, String> form) {
        b.get("/app/doubts/" + thread);
        b.post("/app/doubts/" + thread, form);
    }

    private static void assertNoLeak(String text, Person who) {
        assertThat(text).doesNotContain(who.name()).doesNotContain(who.email()).doesNotContain(who.id());
    }

    @Test
    void anonymousNetworkVotesAcceptanceAndNoIdentityLeaks() throws Exception {
        String tag = uniq();
        Person asker = person("Zara Q" + tag, "student");
        Person helper = person("Helper H" + tag, "student");
        String thread = ask(asker.b(), "", "Why does postorder visit the root last " + tag + "?",
                "In postorder we print the node after both subtrees. I don't see why that is useful.", "tree-traversals", false);

        // The helper sees an alias, never the asker's identity; and answers anonymously.
        String seen = helper.b().get("/app/doubts/" + thread).body();
        assertThat(seen).contains("Anonymous asker #").contains("Why does postorder");
        assertNoLeak(seen, asker);
        act(helper.b(), thread, Map.of("action", "answer", "body", "Because deleting a tree needs children freed **before** the parent."));
        String askerView = asker.b().get("/app/doubts/" + thread).body();
        assertThat(askerView).contains("Anonymous helper #").contains("You (shown to others as Anonymous asker #");
        assertNoLeak(askerView, helper);

        // Lists and the API carry no ids, names or emails of anonymous authors.
        String list = helper.b().get("/app/doubts").body();
        assertThat(list).contains("Why does postorder visit the root last " + tag);
        assertNoLeak(list, asker);
        Browser api0 = new Browser(TestServer.baseUrl()); // a cookie-less client, as the Android app would be
        HttpResponse<String> reg = api0.send(HttpRequest.newBuilder(URI.create(TestServer.baseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + helper.email() + "\",\"password\":\"" + PASSWORD + "\"}")));
        Matcher tm = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(reg.body());
        assertThat(tm.find()).as(reg.body()).isTrue();
        for (String path : new String[] {"/api/v1/doubts/" + thread, "/api/v1/doubts?q=postorder"}) {
            HttpResponse<String> api = api0.send(HttpRequest.newBuilder(URI.create(TestServer.baseUrl() + path))
                    .header("Authorization", "Bearer " + tm.group(1)).GET());
            assertThat(api.statusCode()).isEqualTo(200);
            assertNoLeak(api.body(), asker);
            assertThat(api.body()).doesNotContain("authorId").doesNotContain("userId");
        }

        // Votes (not on your own answer), acceptance by the asker only, then a practice prompt.
        String post = one("select id from posts where thread_id = ?::uuid", thread);
        act(helper.b(), thread, Map.of("action", "vote", "postId", post));
        assertThat(one("select count(*) from post_votes where post_id = ?::uuid", post)).isEqualTo("0");
        act(helper.b(), thread, Map.of("action", "accept", "postId", post));
        assertThat(helper.b().get("/app/doubts/" + thread).statusCode()).isEqualTo(200);
        assertThat(one("select status from threads where id = ?::uuid", thread)).isEqualTo("open");
        act(asker.b(), thread, Map.of("action", "vote", "postId", post));
        act(asker.b(), thread, Map.of("action", "accept", "postId", post));
        String resolved = asker.b().get("/app/doubts/" + thread).body();
        assertThat(resolved).contains("Accepted").contains("Helpful · 1").contains("Resolved").contains("Practise this topic");

        // A Verified Educator can mark public answers teacher verified; others can't.
        Person educator = person("Edu Cator" + tag, "teacher");
        act(asker.b(), thread, Map.of("action", "verify", "postId", post));
        assertThat(one("select teacher_verified from posts where id = ?::uuid", post)).isEqualTo("f");
        db.tx().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("update users set is_verified_educator = true where email = ?")) {
                ps.setString(1, educator.email());
                ps.executeUpdate();
            }
        });
        act(educator.b(), thread, Map.of("action", "verify", "postId", post));
        assertThat(one("select teacher_verified from posts where id = ?::uuid", post)).isEqualTo("t");
    }

    @Test
    void privateDoubtsReachOnlyTheRoomsTeachers() {
        String tag = uniq();
        Person teacher = person("Mr Teach" + tag, "teacher");
        teacher.b().get("/teach");
        String room = location(teacher.b().post("/teach", Map.of("name", "Doubt Room", "subject", "data-structures", "joinPolicy", "open")))
                .substring("/teach/rooms/".length());
        String code = one("select code from rooms where id = ?::uuid", room);
        Person student = person("Private Pria" + tag, "student");
        Person classmate = person("Classmate Cal" + tag, "student");
        for (Person p : new Person[] {student, classmate}) {
            p.b().get("/app/rooms");
            p.b().post("/app/rooms/join", Map.of("code", code));
        }
        String thread = ask(student.b(), room, "I am stuck on the BST delete case " + tag, "When the node has two children, which one replaces it?", "bst", false);

        assertThat(classmate.b().get("/app/doubts/" + thread).statusCode()).isEqualTo(404);
        Person stranger = person("Stranger S" + tag, "student");
        assertThat(stranger.b().get("/app/doubts/" + thread).statusCode()).isEqualTo(404);
        assertThat(teacher.b().get("/teach/rooms/" + room).body()).contains("1 unanswered doubt");
        assertThat(teacher.b().get("/teach/rooms/" + room + "/doubts").body()).contains(student.name()).contains("BST delete case");
        assertThat(classmate.b().get("/teach/rooms/" + room + "/doubts").statusCode()).isEqualTo(403);

        String tv = teacher.b().get("/app/doubts/" + thread).body();
        assertThat(tv).contains(student.name()).contains("Ask your teacher");
        act(teacher.b(), thread, Map.of("action", "answer", "body", "Use the inorder successor: the smallest value in the right subtree."));
        String sv = student.b().get("/app/doubts/" + thread).body();
        assertThat(sv).contains(teacher.name() + " · Teacher");
        String post = one("select id from posts where thread_id = ?::uuid", thread);
        act(teacher.b(), thread, Map.of("action", "verify", "postId", post));
        assertThat(one("select teacher_verified from posts where id = ?::uuid", post)).as("no verifying your own answer").isEqualTo("f");
        assertThat(teacher.b().get("/teach/rooms/" + room).body()).doesNotContain("unanswered doubt");
        // A classmate can't answer or report it either.
        classmate.b().get("/app/doubts");
        assertThat(classmate.b().post("/app/doubts/" + thread, Map.of("action", "answer", "body", "sneaking in")).statusCode()).isEqualTo(404);
        // And private doubts never appear in the public network.
        assertThat(stranger.b().get("/app/doubts?q=BST+delete").body()).doesNotContain("BST delete case " + tag);
    }

    @Test
    void reportsModerationRevealBlockingAndTheKnowledgeBase() {
        String tag = uniq();
        Person asker = person("Rude Rex" + tag, "student");
        Person reader = person("Reader Rae" + tag, "student");
        Person mod = admin();
        String thread = ask(asker.b(), "", "Give me the answers to the trees test " + tag, "Post the answers for the topic test please, all of them.", "", false);

        // A reader reports it; reporting twice counts once.
        act(reader.b(), thread, Map.of("action", "report", "kind", "thread", "targetId", thread, "reason", "cheating", "details", "Asking for test answers"));
        act(reader.b(), thread, Map.of("action", "report", "kind", "thread", "targetId", thread, "reason", "cheating"));
        assertThat(one("select count(*) from reports where target_id = ?::uuid", thread)).isEqualTo("1");
        assertThat(reader.b().get("/admin/moderation").statusCode()).isEqualTo(403);
        String queue = mod.b().get("/admin/moderation").body();
        assertThat(queue).contains("Give me the answers to the trees test " + tag).contains("cheating").contains("Asking for test answers");
        assertNoLeak(queue, asker);

        // Hide it (reason required): readers lose it, the author sees why, it's logged.
        String reportId = one("select id from reports where target_id = ?::uuid", thread);
        mod.b().post("/admin/moderation", Map.of("action", "moderate", "reportId", reportId, "decision", "hide", "reason", ""));
        assertThat(one("select status from threads where id = ?::uuid", thread)).isEqualTo("open");
        mod.b().get("/admin/moderation");
        mod.b().post("/admin/moderation", Map.of("action", "moderate", "reportId", reportId, "decision", "hide", "reason", "Asks for assessment answers"));
        assertThat(one("select status from threads where id = ?::uuid", thread)).isEqualTo("hidden");
        assertThat(reader.b().get("/app/doubts/" + thread).statusCode()).isEqualTo(404);
        assertThat(asker.b().get("/app/doubts/" + thread).body()).contains("Hidden by a moderator");
        assertThat(one("select count(*) from moderation_actions where target_id = ?::uuid and action = 'hide'", thread)).isEqualTo("1");
        assertThat(one("select count(*) from audit_log where action = 'moderation.hide' and entity_id = ?", thread)).isEqualTo("1");

        // Revealing the person behind the alias needs a reason and is logged.
        String alias = one("select alias from anonymous_aliases where thread_id = ?::uuid", thread);
        mod.b().get("/admin/moderation");
        // Revealing is sensitive: without a fresh password confirmation it is refused.
        assertThat(location(mod.b().post("/admin/moderation", Map.of("action", "reveal", "threadId", thread, "alias", alias,
                "reason", "Repeated cheating requests in the network")))).startsWith("/admin/reauth");
        assertThat(location(mod.b().post("/admin/reauth", Map.of("password", PASSWORD, "next", "/admin/moderation")))).isEqualTo("/admin/moderation");
        mod.b().post("/admin/moderation", Map.of("action", "reveal", "threadId", thread, "alias", alias, "reason", "short"));
        assertThat(mod.b().get("/admin/moderation").body()).doesNotContain(asker.email());
        mod.b().post("/admin/moderation", Map.of("action", "reveal", "threadId", thread, "alias", alias, "reason", "Repeated cheating requests in the network"));
        assertThat(mod.b().get("/admin/moderation").body()).contains(asker.email());
        assertThat(one("select count(*) from moderation_actions where target_id = ?::uuid and action = 'reveal_identity'", asker.id())).isEqualTo("1");

        // Blocking by alias hides that person's doubts from the blocker.
        String other = ask(asker.b(), "", "Another doubt about heaps " + tag, "How does sift-down choose which child to swap with?", "heaps", false);
        assertThat(reader.b().get("/app/doubts").body()).contains("Another doubt about heaps " + tag);
        act(reader.b(), other, Map.of("action", "block", "alias", one("select alias from anonymous_aliases where thread_id = ?::uuid", other)));
        assertThat(reader.b().get("/app/doubts").body()).doesNotContain("Another doubt about heaps " + tag);

        // A moderator promotes a resolved doubt into the KB; asking something similar then suggests it.
        mod.b().get("/admin/moderation/promote/" + other);
        HttpResponse<String> pub = mod.b().post("/admin/moderation", Map.of("action", "publish-article", "threadId", other, "topic", "heaps",
                "title", "Sift-down in a min-heap " + tag, "body", "Swap the parent with its **smaller** child, then repeat until the heap property holds."));
        assertThat(location(pub)).startsWith("/app/kb/");
        assertThat(reader.b().get(location(pub)).body()).contains("Sift-down in a min-heap " + tag).contains("<strong>smaller</strong>");
        reader.b().get("/app/doubts/ask");
        String check = reader.b().post("/app/doubts/ask", Map.of("action", "check", "room", "", "category", "concept",
                "title", "How does sift-down work in a heap?", "body", "Which child gets swapped?", "topic", "")).body();
        assertThat(check).contains("These might already answer it").contains("Sift-down in a min-heap " + tag);
        assertThat(reader.b().get("/app/kb?q=sift").body()).contains("Sift-down in a min-heap " + tag);
    }
}
