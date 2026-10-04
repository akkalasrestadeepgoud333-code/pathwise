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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static app.pathwise.app.testing.Browser.location;
import static org.assertj.core.api.Assertions.assertThat;

/** M5 part 2: assignments set, handed in, sent back, graded into official evidence; room context in Next steps. */
class AssignmentFlowTest {
    private static final String PASSWORD = "trees grow upside down";
    private static Database db;

    @BeforeAll
    static void setUp() {
        db = TestServer.database();
        var r = new ContentPackService(db.tx(), new ContentPackLoader(AssignmentFlowTest.class.getClassLoader()),
                new EventBus(), Clock.systemUTC()).importPack("data-structures", null, false);
        assertThat(r.outcome()).isNotEqualTo(ContentPackService.Outcome.INVALID);
    }

    @AfterAll
    static void tearDown() {
        db.close();
    }

    private static String email(String p) {
        return p + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static Browser person(String name, String email, String role) {
        Browser b = new Browser(TestServer.baseUrl());
        b.register(name, email, PASSWORD, role);
        return b;
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

    private static void exec(String sql, Object arg) {
        db.tx().run(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, arg);
                ps.executeUpdate();
            }
        });
    }

    private record Setup(Browser teacher, UUID room, Browser student, String studentEmail) {}

    private static Setup room() {
        Browser teacher = person("Ms Bose", email("bose"), "teacher");
        teacher.get("/teach");
        UUID room = UUID.fromString(location(teacher.post("/teach", Map.of("name", "Writing Lab", "subject", "data-structures",
                "joinPolicy", "open"))).substring("/teach/rooms/".length()));
        String se = email("writer");
        Browser student = person("Kiran Writer", se, "student");
        student.get("/app/rooms");
        student.post("/app/rooms/join", Map.of("code", one("select code from rooms where id = ?", room)));
        return new Setup(teacher, room, student, se);
    }

    private static UUID createAssignment(Setup s, String title, boolean allowLate, String... skills) {
        s.teacher().get("/teach/rooms/" + s.room() + "/assignments");
        List<Map.Entry<String, String>> form = new java.util.ArrayList<>(List.of(Map.entry("action", "create-assignment"),
                Map.entry("title", title), Map.entry("maxPoints", "10"), Map.entry("dueOn", LocalDate.now().plusDays(3).toString()),
                Map.entry("instructions", "Explain **when** a linked list beats an array.")));
        if (!allowLate) {
            form.add(Map.entry("allowLate", "0"));
        }
        for (String k : skills) {
            form.add(Map.entry("skill", one("select id from skills where key = ?", k)));
        }
        HttpResponse<String> r = s.teacher().postPairs("/teach/rooms/" + s.room() + "/assignments", form);
        assertThat(location(r)).isEqualTo("/teach/rooms/" + s.room() + "/assignments");
        return UUID.fromString(one("select id from assignments where title = ? order by created_at desc limit 1", title));
    }

    private static void teach(Setup s, String path, Map<String, String> form) {
        s.teacher().get("/teach/rooms/" + s.room() + path);
        s.teacher().post("/teach/rooms/" + s.room() + path, form);
    }

    @Test
    void setHandInSendBackGradeAndCountAsOfficialEvidence() {
        Setup s = room();
        UUID a = createAssignment(s, "Lists versus arrays", true, "ll-vs-array", "array-complexity");
        String unit;
        teach(s, "/curriculum", Map.of("action", "add-unit", "title", "Writing"));
        unit = one("select id from room_units where room_id = ?", s.room());
        teach(s, "/curriculum", Map.of("action", "add-item", "unitId", unit, "choice", "assignment:" + a));
        String url = "/app/rooms/" + s.room() + "/assignments/" + a;

        // Drafts are invisible to students.
        assertThat(s.student().get("/app/rooms/" + s.room()).body()).doesNotContain("Lists versus arrays");
        assertThat(s.student().get(url).statusCode()).isEqualTo(404);
        teach(s, "/assignments", Map.of("action", "publish-assignment", "assignmentId", a.toString()));
        assertThat(s.student().get("/app/rooms/" + s.room()).body()).contains("Lists versus arrays").contains("Assignment");

        // Room context: Next steps and Today show the room's next item and what's due soon.
        String next = s.student().get("/app/next").body();
        assertThat(next).contains("From your rooms").contains("Writing Lab").contains("Lists versus arrays").containsPattern("In \\d days|Due tomorrow");
        assertThat(s.student().get("/app/today").body()).contains("From your rooms");

        // Draft, then hand in; handed-in work is locked.
        String page = s.student().get(url).body();
        assertThat(page).contains("<strong>when</strong>").contains("Not started");
        s.student().post(url, Map.of("action", "save", "body", "Draft thoughts"));
        assertThat(s.student().get(url).body()).contains("Draft saved").contains("Draft thoughts");
        s.student().post(url, Map.of("action", "hand-in", "body", "Lists win when inserting after a node you already hold: O(1)."));
        assertThat(s.student().get(url).body()).contains("Handed in").doesNotContain("name=\"body\"");
        s.student().post(url, Map.of("action", "hand-in", "body", "sneaky edit"));
        assertThat(s.student().get(url).body()).contains("You can edit it again only if your teacher sends it back")
                .doesNotContain("sneaky edit");

        // The teacher sees it to grade; bad grades are refused; sending back needs a comment.
        assertThat(s.teacher().get("/teach/rooms/" + s.room()).body()).contains("1 submission to grade");
        String gradingPath = "/assignments/" + a;
        String grading = s.teacher().get("/teach/rooms/" + s.room() + gradingPath).body();
        assertThat(grading).contains("Kiran Writer").contains("O(1).").contains("Save grade");
        Map<String, String> base = Map.of("action", "grade", "assignmentId", a.toString(), "userId", one("select id from users where email = ?", s.studentEmail()));
        teach(s, gradingPath, with(base, "grade", "11"));
        assertThat(s.teacher().get("/teach/rooms/" + s.room() + gradingPath).body()).contains("The grade must be between 0 and 10.");
        teach(s, gradingPath, with(base, "sendBack", "1"));
        assertThat(s.teacher().get("/teach/rooms/" + s.room() + gradingPath).body()).contains("Say what to change");
        teach(s, gradingPath, with(with(base, "sendBack", "1"), "feedback", "Add an example with numbers."));
        assertThat(s.student().get(url).body()).contains("Add an example with numbers.").contains("name=\"body\"");
        s.student().post(url, Map.of("action", "hand-in", "body", "Inserting after p in a 1000-node list: 2 pointer changes vs up to 999 shifts."));

        // Grading writes official evidence (0.9 weight, outcome = fraction) and updates the model.
        teach(s, gradingPath, with(with(base, "grade", "8"), "feedback", "Good example."));
        String graded = s.student().get(url).body();
        assertThat(graded).contains("8 / 10").contains("Good example.").contains("This grade now counts toward");
        String sid = one("select id from users where email = ?", s.studentEmail());
        assertThat(one("""
                select count(*) from learning_evidence where user_id = ?::uuid and source_kind = 'assignment' and is_official
                  and base_weight = 0.9 and outcome = 0.8 and submission_id is not null
                """, sid)).isEqualTo("2");
        assertThat(one("select count(*) from skill_mastery where user_id = ?::uuid and official_count > 0", sid)).isEqualTo("2");
        assertThat(one("select version from submissions where assignment_id = ?", a)).isEqualTo("2");

        // Grading again does nothing; analytics shows it.
        teach(s, gradingPath, with(base, "grade", "10"));
        assertThat(one("select grade from submissions where assignment_id = ?", a)).isEqualTo("8.00");
        assertThat(s.teacher().get("/teach/rooms/" + s.room() + "/analytics").body()).contains("Lists versus arrays").contains("80%");
        assertThat(s.student().get("/app/rooms/" + s.room()).body()).contains("1 of 1 required items done");
    }

    @Test
    void lateRulesAndOutsiders() {
        Setup s = room();
        UUID strict = createAssignment(s, "No late work", false);
        UUID lenient = createAssignment(s, "Late is fine", true);
        teach(s, "/assignments", Map.of("action", "publish-assignment", "assignmentId", strict.toString()));
        teach(s, "/assignments", Map.of("action", "publish-assignment", "assignmentId", lenient.toString()));
        exec("update assignments set due_at = now() - interval '1 day' where room_id = ?", s.room());

        String strictUrl = "/app/rooms/" + s.room() + "/assignments/" + strict;
        s.student().get(strictUrl);
        s.student().post(strictUrl, Map.of("action", "hand-in", "body", "too late"));
        assertThat(s.student().get(strictUrl).body()).contains("Nothing was handed in");
        assertThat(one("select count(*) from submissions where assignment_id = ?", strict)).isEqualTo("0");

        String lenientUrl = "/app/rooms/" + s.room() + "/assignments/" + lenient;
        s.student().get(lenientUrl);
        s.student().post(lenientUrl, Map.of("action", "hand-in", "body", "late but here"));
        assertThat(one("select is_late from submissions where assignment_id = ?", lenient)).isEqualTo("t");
        assertThat(s.teacher().get("/teach/rooms/" + s.room() + "/assignments/" + lenient).body()).contains("late");
        assertThat(s.teacher().get("/teach/rooms/" + s.room()).body()).contains("Missed the deadline for No late work");

        Browser outsider = person("Outsider", email("out"), "student");
        assertThat(outsider.get(lenientUrl).statusCode()).isEqualTo(404);
        outsider.get("/app/rooms");
        outsider.post(lenientUrl, Map.of("action", "hand-in", "body", "intrusion"));
        assertThat(one("select count(*) from submissions where assignment_id = ?", lenient)).isEqualTo("1");
        assertThat(s.student().get("/teach/rooms/" + s.room() + "/assignments/" + lenient).statusCode()).isEqualTo(403);
    }

    private static Map<String, String> with(Map<String, String> m, String k, String v) {
        Map<String, String> out = new java.util.LinkedHashMap<>(m);
        out.put(k, v);
        return out;
    }
}
