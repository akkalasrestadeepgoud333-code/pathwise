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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static app.pathwise.app.testing.Browser.location;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The demonstration flow (blueprint §39a), end to end over HTTP against a real server and database: one
 * student's journey from sign-up to a changed learner model, with a teacher and a helper. Each step asserts
 * what the system actually does and adds a line to a transcript ({@code target/demo-transcript.txt}).
 */
class DemoFlowTest {
    private static final String PASSWORD = "trees grow upside down";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern ITEM = Pattern.compile("name=\"item\" value=\"([0-9a-f-]{36})\"");
    private static final List<String> STATES = List.of("not_started", "exploring", "needs_support", "developing", "proficient", "mastered");
    private static Database db;
    private final List<String> transcript = new ArrayList<>();

    @BeforeAll
    static void setUp() {
        db = TestServer.database();
        var r = new ContentPackService(db.tx(), new ContentPackLoader(DemoFlowTest.class.getClassLoader()),
                new EventBus(), Clock.systemUTC()).importPack("data-structures", null, false);
        assertThat(r.outcome()).isNotEqualTo(ContentPackService.Outcome.INVALID);
    }

    @AfterAll
    static void tearDown() {
        db.close();
    }

    private void step(String n, String text) {
        transcript.add(String.format("%-6s %s", n, text));
    }

    private static String uniq() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String one(String sql, Object... args) {
        return db.tx().read(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) {
                    ps.setObject(i + 1, args[i]);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        });
    }

    private static List<String> rows(String sql, Object... args) {
        return db.tx().read(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) {
                    ps.setObject(i + 1, args[i]);
                }
                List<String> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(rs.getString(1));
                    }
                }
                return out;
            }
        });
    }

    /** Outbox handlers run after commit (every second): wait for their effect. */
    private static void eventually(Supplier<Boolean> condition, String what) {
        long end = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < end && !condition.get()) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertThat(condition.get()).as(what + " within 20 s").isTrue();
    }

    private static QuestionDao.QuestionRow question(UUID id) {
        return db.tx().read(c -> new QuestionDao().byIds(c, List.of(id))).get(0);
    }

    private static String textAnswer(QuestionDao.QuestionRow q) {
        try {
            JsonNode spec = JSON.readTree(q.specJson());
            return spec.has("answer") ? spec.get("answer").asText().replaceAll("\\.0$", "") : spec.get("accepted").get(0).asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Takes an assessment, answering correctly except for the question keys in {@code skip}; returns the attempt id. */
    private static String takeAssessment(Browser b, String key, Set<String> skip) {
        b.get("/app/assessments/" + key);
        String loc = location(b.post("/app/assessments/" + key + "/start", Map.of()));
        assertThat(loc).startsWith("/app/assessments/attempt/");
        String attempt = loc.substring("/app/assessments/attempt/".length());
        Matcher m = ITEM.matcher(b.get(loc).body());
        List<Map.Entry<String, String>> form = new ArrayList<>();
        form.add(Map.entry("confirm", "yes"));
        while (m.find()) {
            String item = m.group(1);
            QuestionDao.QuestionRow q = question(UUID.fromString(one("select question_id::text from assessment_items where id = ?::uuid", item)));
            form.add(Map.entry("item", item));
            if (skip.contains(q.key())) {
                continue;
            }
            if (q.type().endsWith("_choice")) {
                q.options().stream().filter(QuestionDao.OptionRow::correct).forEach(o -> form.add(Map.entry("o." + item, o.id().toString())));
            } else {
                form.add(Map.entry("t." + item, textAnswer(q)));
            }
        }
        b.postPairs("/app/assessments/attempt/" + attempt + "/submit", form);
        assertThat(one("select status from assessment_attempts where id = ?::uuid", attempt)).isEqualTo("graded");
        return attempt;
    }

    private static Map<String, String> practiceAnswer(String attempt, int position) {
        QuestionDao.QuestionRow q = question(UUID.fromString(one(
                "select question_id::text from activity_attempt_items where attempt_id = ?::uuid and position = ?", attempt, position - 1)));
        Map<String, String> form = new LinkedHashMap<>();
        switch (q.type()) {
            case "numeric", "short_text" -> form.put("text", textAnswer(q));
            default -> form.put("option", q.options().stream().filter(QuestionDao.OptionRow::correct).findFirst().orElseThrow().id().toString());
        }
        return form;
    }

    private static String state(String userId, String skillKey) {
        String s = one("select m.state from skill_mastery m join skills k on k.id = m.skill_id where m.user_id = ?::uuid and k.key = ?", userId, skillKey);
        return s == null ? "not_started" : s;
    }

    /** Plays a game perfectly, with its start backdated so the pace is plausible to the replay check. */
    private static String playPerfect(Browser b, String key) throws Exception {
        b.get("/app/games/" + key);
        String attempt = location(b.post("/app/games/" + key + "/start", Map.of())).substring("/app/games/play/".length());
        b.get("/app/games/play/" + attempt);
        db.tx().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("update activity_attempts set started_at = now() - interval '5 minutes' where id = ?::uuid")) {
                ps.setString(1, attempt);
                ps.executeUpdate();
            }
        });
        JsonNode puzzle = JSON.readTree(one("select puzzle::text from activity_attempts where id = ?::uuid", attempt));
        ArrayNode actions = JSON.createArrayNode();
        long[] t = {500};
        Consumer<String[]> add = a -> {
            ObjectNode o = actions.addObject().put("type", a[0]).put("a", a[1]);
            if (a.length > 2) {
                o.put("b", a[2]);
            }
            o.put("t", t[0]);
            t[0] += 400;
        };
        switch (one("select game_template from activities where key = ?", key)) {
            case "match_pairs" -> puzzle.get("items").forEach(i -> add.accept(new String[] {"match", i.get("id").asText(), i.get("id").asText()}));
            case "bug_hunt" -> puzzle.get("rounds").forEach(r -> {
                add.accept(new String[] {"line", r.get("bugLine").asText()});
                add.accept(new String[] {"class", r.get("bugClass").asText()});
            });
            default -> puzzle.get("rounds").forEach(r -> r.get("answer").forEach(v -> add.accept(new String[] {"tap", v.asText()})));
        }
        ObjectNode body = JSON.createObjectNode();
        body.set("actions", actions);
        HttpResponse<String> r = b.send(HttpRequest.newBuilder(URI.create(TestServer.baseUrl() + "/app/games/play/" + attempt + "/finish"))
                .header("Content-Type", "application/json").header("X-CSRF-Token", b.csrf()).header("X-Requested-With", "fetch")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())));
        assertThat(r.statusCode()).isEqualTo(200);
        return attempt;
    }

    @Test
    void theTwentyOneStepDemonstrationRunsEndToEnd() throws Exception {
        String tag = uniq();

        // Setup: a teacher with a class.
        String teacherEmail = "demo-teacher-" + tag + "@example.com";
        Browser teacher = new Browser(TestServer.baseUrl());
        teacher.register("Prof. Meera", teacherEmail, PASSWORD, "teacher");
        teacher.get("/teach");
        String roomLoc = location(teacher.post("/teach", Map.of("name", "DSA – Section A", "subject", "data-structures", "joinPolicy", "approval")));
        String room = roomLoc.substring("/teach/rooms/".length());
        String code = one("select code from rooms where id = ?::uuid", room);
        String teacherId = one("select id::text from users where email = ?", teacherEmail);

        // 1. Student registers.
        String email = "demo-student-" + tag + "@example.com";
        Browser s = new Browser(TestServer.baseUrl());
        s.register("Asha", email, PASSWORD, "student");
        String me = one("select id::text from users where email = ?", email);
        assertThat(me).isNotNull();
        step("1", "Asha registers; Today opens.");

        // 2. Joins "DSA – Section A" by code; the teacher is notified and approves.
        s.get("/app/rooms");
        s.post("/app/rooms/join", Map.of("code", code.substring(0, 4).toLowerCase() + "-" + code.substring(4)));
        eventually(() -> one("select count(*) from notifications where user_id = ?::uuid", teacherId).compareTo("0") > 0, "teacher notified");
        teacher.get("/teach/rooms/" + room);
        teacher.post("/teach/rooms/" + room, Map.of("action", "approve", "userId", me));
        assertThat(s.get("/app/rooms/" + room).statusCode()).isEqualTo(200);
        step("2", "Joins DSA – Section A with code " + code + "; Prof. Meera gets a notification and approves.");

        // 3–4. The diagnostic: graded, official evidence. She doesn't know traversal or recursion yet.
        takeAssessment(s, "ds-diagnostic", Set.of("trav-pre-basic", "rec-factorial-value"));
        int official = Integer.parseInt(one("select count(*) from learning_evidence where user_id = ?::uuid and is_official", me));
        assertThat(official).isGreaterThan(0);
        step("3-4", "Takes the starting-point check; answers become " + official + " official evidence rows.");

        // 5. The model and map reflect it.
        String preorderBefore = state(me, "preorder");
        assertThat(s.get("/app/progress").statusCode()).isEqualTo(200);
        assertThat(s.get("/app/map/data-structures").statusCode()).isEqualTo(200);
        step("5", "Progress and the Learning Map update; preorder is now '" + preorderBefore + "'.");

        // 6. Recommendations, each with a reason.
        String next = s.get("/app/next").body();
        List<String> recs = rows("select slot || ': ' || title || ' (' || reason_text || ')' from recommendations "
                + "where user_id = ?::uuid and status = 'active' order by slot, rank", me);
        assertThat(recs).isNotEmpty();
        assertThat(next).contains("Why");
        step("6", "Next steps: " + String.join(" | ", recs.subList(0, Math.min(3, recs.size()))));

        // 7–8. Opens and completes the Tree traversals module (its check counts as evidence).
        String path = "/app/modules/tree-traversals/overview";
        HttpResponse<String> done = null;
        for (int guard = 0; guard < 20; guard++) {
            String page = s.get(path).body();
            String section = path.substring(path.lastIndexOf('/') + 1).replaceAll("\\?.*", "");
            if (page.contains("id=\"check-form\"")) {
                s.post("/app/modules/tree-traversals/" + section + "/check", Map.of());
                path = "/app/modules/tree-traversals/summary";
                continue;
            }
            done = s.post("/app/modules/tree-traversals/" + section + "/done", Map.of());
            if (location(done).contains("completed=1")) {
                break;
            }
            path = location(done);
        }
        assertThat(location(done)).contains("completed=1");
        assertThat(one("select count(*) from learning_evidence where user_id = ?::uuid and source_kind = 'module_check'", me)).isNotEqualTo("0");
        step("7-8", "Works through Tree traversals; the module check is recorded as evidence.");

        // 9–10. Plays Traversal Race; the server replays it before recording informal evidence.
        String game = playPerfect(s, "traversal-race");
        assertThat(one("select status from activity_attempts where id = ?::uuid", game)).isEqualTo("completed");
        assertThat(one("select count(*) from learning_evidence where user_id = ?::uuid and source_kind = 'game' and not is_official", me)).isNotEqualTo("0");
        step("9-10", "Plays Traversal Race; the server replays the moves, then records informal evidence.");

        // 11. Asks the tutor (Guided Help when no local model runs, and it says so).
        s.get("/app/tutor");
        String chat = location(s.post("/app/tutor/start", Map.of("mode", "resource", "q", "Why is preorder root-first?")));
        String chatPage = s.get(chat).body();
        assertThat(chatPage).containsAnyOf("Guided Help · from Pathwise content", "AI tutor · may be wrong");
        step("11", "Asks the tutor \"why is preorder root-first?\" and gets an answer labelled with its source.");

        // 12. Quick practice.
        s.get("/app/practice");
        String practice = location(s.post("/app/practice/start", Map.of("mode", "quick"))).replaceAll("^/app/practice/([^/]+).*$", "$1");
        int items = Integer.parseInt(one("select count(*) from activity_attempt_items where attempt_id = ?::uuid", practice));
        for (int p = 1; p <= items; p++) {
            s.get("/app/practice/" + practice);
            s.post("/app/practice/" + practice + "/" + p + "/answer", practiceAnswer(practice, p));
        }
        assertThat(one("select count(*) from learning_evidence where user_id = ?::uuid and source_kind = 'practice'", me)).isNotEqualTo("0");
        step("12", "Quick practice: " + items + " questions answered, each one evidence.");

        // 13–14. An anonymous doubt; a mentor answers; Asha accepts; a Verified Educator verifies.
        Browser mentor = new Browser(TestServer.baseUrl());
        String mentorEmail = "demo-mentor-" + tag + "@example.com";
        mentor.register("Mentor Ravi", mentorEmail, PASSWORD, "student");
        db.tx().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    insert into user_platform_roles (user_id, role) select id, 'mentor' from users where email = ?
                    """)) {
                ps.setString(1, mentorEmail);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("update users set is_verified_educator = true where email = ?")) {
                ps.setString(1, teacherEmail);
                ps.executeUpdate();
            }
        });
        s.get("/app/doubts/ask");
        String thread = location(s.post("/app/doubts/ask", Map.of("action", "post", "room", "", "category", "concept",
                "title", "Why does preorder visit the root first " + tag + "?", "body",
                "I can follow the steps but I don't see why the root comes before its subtrees.", "topic", "tree-traversals")))
                .substring("/app/doubts/".length());
        mentor.get("/app/doubts/" + thread);
        mentor.post("/app/doubts/" + thread, Map.of("action", "answer", "body",
                "Preorder copies a tree: you must create a node before you can attach its children."));
        String post = one("select id::text from posts where thread_id = ?::uuid", thread);
        s.get("/app/doubts/" + thread);
        s.post("/app/doubts/" + thread, Map.of("action", "accept", "postId", post));
        teacher.get("/app/doubts/" + thread);
        teacher.post("/app/doubts/" + thread, Map.of("action", "verify", "postId", post));
        assertThat(one("select status from threads where id = ?::uuid", thread)).isEqualTo("resolved");
        assertThat(one("select teacher_verified from posts where id = ?::uuid", post)).isEqualTo("t");
        assertThat(mentor.get("/app/doubts/" + thread).body()).doesNotContain("Asha").doesNotContain(email);
        step("13-14", "Posts an anonymous doubt; a mentor answers; Asha accepts; Prof. Meera verifies. Asha's name never appears.");

        // 15–16. The traversal topic test, all correct; the model moves up.
        takeAssessment(s, "trees-topic-test", Set.of());
        String preorderAfter = state(me, "preorder");
        assertThat(STATES.indexOf(preorderAfter)).as("preorder %s -> %s", preorderBefore, preorderAfter)
                .isGreaterThan(STATES.indexOf(preorderBefore));
        step("15-16", "Takes the trees topic test; preorder moves from '" + preorderBefore + "' to '" + preorderAfter + "'.");

        // 17–18. Badges are awarded from recorded evidence, with notifications.
        eventually(() -> !"0".equals(one("select count(*) from badge_awards where user_id = ?::uuid", me)), "a badge");
        List<String> badges = rows("select badge_key from badge_awards where user_id = ?::uuid order by awarded_at", me);
        eventually(() -> !"0".equals(one("select count(*) from notifications where user_id = ?::uuid and type = 'badge_earned'", me)), "badge notification");
        step("17-18", "Badges earned from evidence: " + String.join(", ", badges) + " (each with a notification).");

        // 19. Today reflects it (on load; Pathwise has no live push, see FEATURE_STATUS).
        String today = s.get("/app/today").body();
        assertThat(today).contains("XP").contains("bell-count");
        step("19", "Today shows the new XP, streak and unread notifications when (re)loaded.");

        // 20. The teacher's analytics show Asha's progress.
        String analytics = teacher.get("/teach/rooms/" + room + "/analytics").body();
        assertThat(analytics).contains("Asha");
        step("20", "Prof. Meera's room analytics list Asha with her progress.");

        // 21. The engine proposes what's next.
        s.get("/app/next");
        List<String> after = rows("select slot || ': ' || title from recommendations where user_id = ?::uuid and status = 'active' order by slot, rank", me);
        assertThat(after).isNotEmpty();
        step("21", "New next steps: " + String.join(" | ", after.subList(0, Math.min(3, after.size()))));

        Path out = Path.of("target", "demo-transcript.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, "Pathwise demonstration flow (blueprint §39a)\n\n" + String.join("\n", transcript) + "\n", StandardCharsets.UTF_8);
        transcript.forEach(System.out::println);
    }
}
