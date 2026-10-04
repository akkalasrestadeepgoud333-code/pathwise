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

import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static app.pathwise.app.testing.Browser.location;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5 end to end: rooms, joining, approvals, co-teacher permissions, the curriculum each student sees,
 * teacher-made assessments with released results, analytics, archiving, and cross-room access attempts.
 */
class RoomFlowTest {
    private static final String PASSWORD = "trees grow upside down";
    private static Database db;

    @BeforeAll
    static void setUp() {
        db = TestServer.database();
        var r = new ContentPackService(db.tx(), new ContentPackLoader(RoomFlowTest.class.getClassLoader()),
                new EventBus(), Clock.systemUTC()).importPack("data-structures", null, false);
        assertThat(r.outcome()).isNotEqualTo(ContentPackService.Outcome.INVALID);
    }

    @AfterAll
    static void tearDown() {
        db.close();
    }

    private static String email(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static Browser person(String name, String email, String role) {
        Browser b = new Browser(TestServer.baseUrl());
        b.register(name, email, PASSWORD, role);
        return b;
    }

    private static UUID createRoom(Browser teacher, String name, String policy) {
        teacher.get("/teach");
        HttpResponse<String> r = teacher.post("/teach", Map.of("name", name, "subject", "data-structures", "joinPolicy", policy));
        assertThat(location(r)).startsWith("/teach/rooms/");
        return UUID.fromString(location(r).substring("/teach/rooms/".length()));
    }

    private static String code(UUID room) {
        return db.tx().read(c -> {
            try (PreparedStatement ps = c.prepareStatement("select code from rooms where id = ?")) {
                ps.setObject(1, room);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getString(1);
                }
            }
        });
    }

    private static String idOf(String sql, Object arg) {
        return db.tx().read(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, arg);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getString(1);
                }
            }
        });
    }

    private static HttpResponse<String> join(Browser b, String code) {
        b.get("/app/rooms");
        return b.post("/app/rooms/join", Map.of("code", code.substring(0, 4).toLowerCase() + "-" + code.substring(4)));
    }

    private static HttpResponse<String> teach(Browser b, UUID room, String section, Map<String, String> form) {
        b.get("/teach/rooms/" + room + (section.isEmpty() ? "" : "/" + section));
        return b.post("/teach/rooms/" + room + (section.isEmpty() ? "" : "/" + section), form);
    }

    private static UUID userId(String email) {
        return UUID.fromString(idOf("select id from users where email = ?", email));
    }

    @Test
    void approvalCurriculumAndTheStudentsOwnPath() {
        Browser teacher = person("Ms Rao", email("teacher"), "teacher");
        UUID room = createRoom(teacher, "DSA – Section A", "approval");
        assertThat(teacher.get("/teach/rooms/" + room).body()).contains("DSA – Section A").contains("Build the curriculum")
                .contains(code(room).substring(0, 4) + "-" + code(room).substring(4));

        // A student asks to join with the code (any case, with a dash) and waits for approval.
        String studentEmail = email("student");
        Browser student = person("Asha Student", studentEmail, "student");
        HttpResponse<String> asked = join(student, code(room));
        assertThat(location(asked)).isEqualTo("/app/rooms");
        assertThat(student.get("/app/rooms").body()).contains("Waiting for the teacher to approve you");
        assertThat(student.get("/app/rooms/" + room).statusCode()).isEqualTo(404);
        assertThat(teacher.get("/teach/rooms/" + room).body()).contains("Waiting to join").contains("Asha Student");
        teach(teacher, room, "", Map.of("action", "approve", "userId", userId(studentEmail).toString()));
        assertThat(student.get("/app/rooms/" + room).statusCode()).isEqualTo(200);

        // The teacher builds a unit: the diagnostic, then a module, then a game; and a unit that opens later.
        teach(teacher, room, "curriculum", Map.of("action", "add-unit", "title", "Week 1: Foundations"));
        String unit = idOf("select id from room_units where room_id = ? and title = 'Week 1: Foundations'", room);
        String diagnostic = idOf("select id from assessments where key = ?", "ds-diagnostic");
        String module = idOf("select id from modules where key = ?", "arrays-essentials");
        String game = idOf("select id from activities where key = ?", "traversal-race");
        for (String choice : List.of("assessment:" + diagnostic, "module:" + module, "game:" + game)) {
            HttpResponse<String> added = teach(teacher, room, "curriculum", Map.of("action", "add-item", "unitId", unit, "choice", choice));
            assertThat(location(added)).isEqualTo("/teach/rooms/" + room + "/curriculum");
        }
        teach(teacher, room, "curriculum", Map.of("action", "add-unit", "title", "Week 9: Graphs",
                "publishOn", LocalDate.now().plusDays(30).toString()));
        // Items that aren't offered (e.g. another room's or made-up ids) are refused.
        teach(teacher, room, "curriculum", Map.of("action", "add-item", "unitId", unit, "choice", "module:" + UUID.randomUUID()));
        assertThat(teacher.get("/teach/rooms/" + room + "/curriculum").body()).contains("That can&#039;t be added to this room.");
        assertThat(count("select count(*) from room_unit_items where unit_id = ?::uuid", unit)).isEqualTo(3);

        String view = student.get("/app/rooms/" + room).body();
        assertThat(view).contains("Week 1: Foundations").contains("Arrays").contains("Traversal Race")
                .contains("Your next step in this room").contains("Data Structures starting-point check")
                .contains("0 of 3 required items done").doesNotContain("Week 9: Graphs");

        // Submitting the diagnostic moves the student's next step on; the teacher sees it.
        student.get("/app/assessments/ds-diagnostic");
        HttpResponse<String> started = student.post("/app/assessments/ds-diagnostic/start", Map.of());
        String attempt = location(started).substring("/app/assessments/attempt/".length());
        student.get("/app/assessments/attempt/" + attempt);
        student.post("/app/assessments/attempt/" + attempt + "/submit", Map.of("confirm", "yes"));
        String after = student.get("/app/rooms/" + room).body();
        assertThat(after).contains("1 of 3 required items done").contains("Submitted");
        Matcher next = Pattern.compile("Your next step in this room</p>\\s*<h2 id=\"next-title\">([^<]+)</h2>").matcher(after);
        assertThat(next.find()).isTrue();
        assertThat(next.group(1)).isNotEqualTo("Data Structures starting-point check");

        String analytics = teacher.get("/teach/rooms/" + room + "/analytics").body();
        assertThat(analytics).contains("Asha Student").contains("33%").contains("Room skills").contains("1/1");
        HttpResponse<String> csv = teacher.get("/teach/rooms/" + room + "/analytics.csv");
        assertThat(csv.headers().firstValue("Content-Type").orElse("")).startsWith("text/csv");
        assertThat(csv.body()).contains(studentEmail).contains("curriculum_done_percent");

        // Announcements reach students.
        teach(teacher, room, "", Map.of("action", "announce", "body", "Quiz on Friday covers linked lists.", "pinned", "1"));
        assertThat(student.get("/app/rooms/" + room).body()).contains("Quiz on Friday covers linked lists.").contains("Pinned");
    }

    @Test
    void outsidersStudentsAndCoTeachersOnlyGetWhatTheyMay() {
        Browser owner = person("Mr Iyer", email("owner"), "teacher");
        UUID room = createRoom(owner, "Trees Club", "open");
        String studentEmail = email("member");
        Browser student = person("Ravi Member", studentEmail, "student");
        assertThat(location(join(student, code(room)))).isEqualTo("/app/rooms/" + room); // open: straight in

        // Another teacher with no membership: the room doesn't exist for them.
        Browser stranger = person("Other Teacher", email("stranger"), "teacher");
        assertThat(stranger.get("/teach/rooms/" + room).statusCode()).isEqualTo(404);
        assertThat(stranger.get("/teach/rooms/" + room + "/analytics").statusCode()).isEqualTo(404);
        assertThat(stranger.get("/app/rooms/" + room).statusCode()).isEqualTo(404);
        stranger.get("/teach");
        assertThat(stranger.post("/teach/rooms/" + room, Map.of("action", "remove", "userId", userId(studentEmail).toString())).statusCode())
                .isEqualTo(404);
        assertThat(count("select count(*) from room_memberships where room_id = ?::uuid and status = 'active' and role = 'student'", room.toString()))
                .isEqualTo(1);

        // A student member can't use teacher pages.
        assertThat(student.get("/teach/rooms/" + room).statusCode()).isEqualTo(403);
        assertThat(student.get("/teach/rooms/" + room + "/analytics.csv").statusCode()).isEqualTo(403);

        // A co-teacher joins by invite link: can approve and see analytics by default, but not publish or change settings.
        owner.get("/teach/rooms/" + room + "/people");
        owner.post("/teach/rooms/" + room + "/people", Map.of("action", "invite", "role", "co_teacher", "maxUses", "1", "days", "7"));
        String people = owner.get("/teach/rooms/" + room + "/people").body();
        Matcher link = Pattern.compile("/join/([A-Za-z0-9_-]{20,})").matcher(people);
        assertThat(link.find()).isTrue();
        String token = link.group(1);
        assertThat(owner.get("/teach/rooms/" + room + "/people").body()).as("shown once").doesNotContain(token);

        Browser notTeacher = person("Student Trying", email("try"), "student");
        notTeacher.get("/join/" + token);
        notTeacher.post("/join/" + token, Map.of());
        assertThat(notTeacher.get("/join/" + token).body()).contains("Co-teacher invites need a teacher account");

        String coEmail = email("co");
        Browser co = person("Co Teacher", coEmail, "teacher");
        assertThat(co.get("/join/" + token).body()).contains("Join Trees Club").contains("Co-teacher");
        assertThat(location(co.post("/join/" + token, Map.of()))).isEqualTo("/app/rooms/" + room);
        assertThat(co.get("/teach/rooms/" + room + "/analytics").body()).contains("Ravi Member");
        assertThat(co.get("/teach/rooms/" + room + "/settings").statusCode()).isEqualTo(403);
        Browser late = person("Late Teacher", email("late"), "teacher");
        late.get("/join/" + token);
        assertThat(late.get("/join/" + token).body()).contains("used up");

        // The owner turns off the co-teacher's analytics: only class totals remain.
        teach(owner, room, "people", Map.of("action", "perms", "userId", userId(coEmail).toString(), "author", "1"));
        assertThat(co.get("/teach/rooms/" + room + "/analytics").body()).contains("only class totals are shown").doesNotContain("Ravi Member");
        assertThat(co.get("/teach/rooms/" + room + "/analytics.csv").statusCode()).isEqualTo(403);

        // Removing a student ends their access, and the code won't let them back in.
        teach(owner, room, "people", Map.of("action", "remove", "userId", userId(studentEmail).toString()));
        assertThat(student.get("/app/rooms/" + room).statusCode()).isEqualTo(404);
        join(student, code(room));
        assertThat(student.get("/app/rooms").body()).contains("You were removed from this room");
    }

    @Test
    void inviteOnlyRoomsAndStudentLinks() {
        Browser owner = person("Dr Sen", email("sen"), "teacher");
        UUID room = createRoom(owner, "Private Seminar", "invite_only");
        Browser s1 = person("First Student", email("s1"), "student");
        join(s1, code(room));
        assertThat(s1.get("/app/rooms").body()).contains("only takes members through an invite link");
        owner.get("/teach/rooms/" + room + "/people");
        owner.post("/teach/rooms/" + room + "/people", Map.of("action", "invite", "role", "student", "maxUses", "5", "days", "3"));
        Matcher link = Pattern.compile("/join/([A-Za-z0-9_-]{20,})").matcher(owner.get("/teach/rooms/" + room + "/people").body());
        assertThat(link.find()).isTrue();
        s1.get("/join/" + link.group(1));
        assertThat(location(s1.post("/join/" + link.group(1), Map.of()))).isEqualTo("/app/rooms/" + room);
        assertThat(count("select uses from room_invites where room_id = ?::uuid", room.toString())).isEqualTo(1);
        // Using it again while already in doesn't use it up.
        s1.get("/join/" + link.group(1));
        s1.post("/join/" + link.group(1), Map.of());
        assertThat(count("select uses from room_invites where room_id = ?::uuid", room.toString())).isEqualTo(1);
        assertThat(new Browser(TestServer.baseUrl()).get("/join/" + link.group(1)).statusCode()).as("sign in first").isEqualTo(303);
        Browser guess = person("Guesser", email("guess"), "student");
        assertThat(guess.get("/join/notarealtokenatallxxxx").body()).contains("isn&#039;t valid");
    }

    @Test
    void teacherMadeAssessmentsWithReleasedResultsAndArchiving() {
        Browser owner = person("Ms Kaur", email("kaur"), "teacher");
        UUID room = createRoom(owner, "Lists Lab", "open");
        Browser student = person("Neel Student", email("neel"), "student");
        join(student, code(room));
        Browser outsider = person("Outside Student", email("outside"), "student");

        // Build a draft from the Linked Lists bank (three questions, results held until released).
        String builder = owner.get("/teach/rooms/" + room + "/assess/new?topic=linked-lists").body();
        assertThat(builder).contains("New assessment: Linked Lists").contains("test-only");
        Matcher q = Pattern.compile("name=\"q\" value=\"([0-9a-f-]{36})\"").matcher(builder);
        List<Map.Entry<String, String>> form = new ArrayList<>(List.of(Map.entry("action", "create-assessment"),
                Map.entry("topic", "linked-lists"), Map.entry("title", "Lists check-in"), Map.entry("kind", "quiz"),
                Map.entry("maxAttempts", "1"), Map.entry("feedback", "manual_release"),
                Map.entry("dueOn", LocalDate.now().plusDays(7).toString())));
        for (int i = 0; i < 3 && q.find(); i++) {
            form.add(Map.entry("q", q.group(1)));
        }
        HttpResponse<String> created = owner.postPairs("/teach/rooms/" + room + "/assess", form);
        assertThat(location(created)).isEqualTo("/teach/rooms/" + room + "/assess");
        String key = idOf("select key from assessments where owner_room_id = ?", room);
        String aid = idOf("select id from assessments where owner_room_id = ?", room);
        assertThat(key).startsWith("rm-");
        assertThat(count("select count(*) from assessment_items where assessment_id = ?::uuid", aid)).isEqualTo(3);

        // A draft is invisible to students; publishing shows it to members only.
        assertThat(student.get("/app/assessments").body()).doesNotContain("Lists check-in");
        assertThat(student.get("/app/assessments/" + key).statusCode()).isEqualTo(404);
        teach(owner, room, "assess", Map.of("action", "publish-assessment", "assessmentId", aid));
        assertThat(student.get("/app/assessments").body()).contains("Lists check-in").contains("Lists Lab");
        assertThat(outsider.get("/app/assessments").body()).doesNotContain("Lists check-in");
        assertThat(outsider.get("/app/assessments/" + key).statusCode()).isEqualTo(404);
        outsider.get("/app/assessments");
        assertThat(location(outsider.post("/app/assessments/" + key + "/start", Map.of()))).isEqualTo("/app/assessments/" + key);
        assertThat(count("select count(*) from assessment_attempts where assessment_id = ?::uuid", aid)).isZero();

        // The student takes it: score now, answers only after release.
        student.get("/app/assessments/" + key);
        String attempt = location(student.post("/app/assessments/" + key + "/start", Map.of())).substring("/app/assessments/attempt/".length());
        student.get("/app/assessments/attempt/" + attempt);
        student.post("/app/assessments/attempt/" + attempt + "/submit", Map.of("confirm", "yes"));
        String held = student.get("/app/assessments/result/" + attempt).body();
        assertThat(held).contains("0%").contains("aren&#039;t released yet").doesNotContain("Correct answer");
        assertThat(owner.get("/teach/rooms/" + room + "/assess").body()).contains("1/1").contains("Release results");
        teach(owner, room, "assess", Map.of("action", "release-results", "assessmentId", aid));
        assertThat(student.get("/app/assessments/result/" + attempt).body()).contains("Correct answer");
        assertThat(owner.get("/teach/rooms/" + room + "/assess/" + aid).body()).contains("Item analysis").contains("Lists check-in");
        assertThat(outsider.get("/teach/rooms/" + room + "/assess/" + aid).statusCode()).isEqualTo(404);

        // Archiving makes the room read-only: no new attempts, no edits, until restored.
        teach(owner, room, "settings", Map.of("action", "archive"));
        assertThat(student.get("/app/rooms/" + room).body()).contains("Archived");
        assertThat(student.get("/app/assessments").body()).doesNotContain("Lists check-in");
        owner.get("/teach/rooms/" + room + "/curriculum");
        assertThat(owner.post("/teach/rooms/" + room + "/curriculum", Map.of("action", "add-unit", "title", "Late unit")).statusCode())
                .isEqualTo(403);
        teach(owner, room, "settings", Map.of("action", "restore"));
        assertThat(location(teach(owner, room, "curriculum", Map.of("action", "add-unit", "title", "Back again"))))
                .isEqualTo("/teach/rooms/" + room + "/curriculum");
    }

    @Test
    void classStatisticsAppearOnlyForGroupsOfFiveOrMore() {
        Browser owner = person("Prof Das", email("das"), "teacher");
        UUID room = createRoom(owner, "Stats Room", "open");
        owner.get("/teach/rooms/" + room + "/settings");
        owner.post("/teach/rooms/" + room + "/settings", Map.of("action", "save-settings", "name", "Stats Room",
                "subject", "data-structures", "joinPolicy", "open", "classStats", "1"));
        teach(owner, room, "curriculum", Map.of("action", "add-unit", "title", "Unit"));
        String unit = idOf("select id from room_units where room_id = ?", room);
        teach(owner, room, "curriculum", Map.of("action", "add-item", "unitId", unit,
                "choice", "game:" + idOf("select id from activities where key = ?", "match-structures")));
        List<Browser> students = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Browser s = person("Stat Student " + i, email("stat" + i), "student");
            join(s, code(room));
            students.add(s);
            String page = s.get("/app/rooms/" + room).body();
            if (i < 4) {
                assertThat(page).as("hidden with %d students", i + 1).doesNotContain("The class so far");
            } else {
                assertThat(page).contains("The class so far").contains("0%");
            }
        }
    }

    @Test
    void apiJoinsAndShowsTheRoom() {
        Browser owner = person("Api Owner", email("apiowner"), "teacher");
        UUID room = createRoom(owner, "Api Room", "open");
        Browser http = new Browser(TestServer.baseUrl());
        HttpResponse<String> reg = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(TestServer.baseUrl() + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"displayName\":\"Api Student\",\"email\":\"" + email("apist")
                        + "\",\"password\":\"" + PASSWORD + "\"}")));
        Matcher tm = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(reg.body());
        assertThat(tm.find()).isTrue();
        String token = tm.group(1);
        java.util.function.BiFunction<String, String, HttpResponse<String>> api = (path, json) -> http.send(
                java.net.http.HttpRequest.newBuilder(java.net.URI.create(TestServer.baseUrl() + path))
                        .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                        .method(json == null ? "GET" : "POST", json == null ? java.net.http.HttpRequest.BodyPublishers.noBody()
                                : java.net.http.HttpRequest.BodyPublishers.ofString(json)));
        assertThat(api.apply("/api/v1/rooms/" + room, null).statusCode()).isEqualTo(404);
        assertThat(api.apply("/api/v1/rooms/join", "{\"code\":\"nope\"}").statusCode()).isEqualTo(409);
        HttpResponse<String> joined = api.apply("/api/v1/rooms/join", "{\"code\":\"" + code(room) + "\"}");
        assertThat(joined.statusCode()).isEqualTo(200);
        assertThat(joined.body()).contains("\"status\":\"active\"");
        assertThat(api.apply("/api/v1/rooms", null).body()).contains("Api Room");
        assertThat(api.apply("/api/v1/rooms/" + room, null).body()).contains("\"name\":\"Api Room\"").contains("\"units\"");
    }

    private static int count(String sql, String arg) {
        return db.tx().read(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, arg);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }
}
