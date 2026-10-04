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

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static app.pathwise.app.testing.Browser.location;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teachers' own notes and PDFs in a room (members only, PDFs checked by content, notes sanitised), and the
 * administrator's powers to message people and remove accounts.
 */
class MaterialsAndAdminPowersFlowTest {
    private static final String PASSWORD = "trees grow upside down";
    private static Database db;

    @BeforeAll
    static void setUp() {
        db = TestServer.database();
        new ContentPackService(db.tx(), new ContentPackLoader(MaterialsAndAdminPowersFlowTest.class.getClassLoader()),
                new EventBus(), Clock.systemUTC()).importPack("data-structures", null, false);
    }

    @AfterAll
    static void tearDown() {
        db.close();
    }

    private record Person(Browser b, String email, String id) {}

    private static Person person(String name, String role) {
        String email = "mat-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        Browser b = new Browser(TestServer.baseUrl());
        b.register(name, email, PASSWORD, role);
        return new Person(b, email, one("select id::text from users where email = ?", email));
    }

    private static Person admin() {
        String email = "mat-admin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        new IdentityService(db.tx(), new PasswordHasher(1024, 1, 1), new EventBus(), Clock.systemUTC(), Duration.ofHours(12),
                Duration.ofDays(30)).createOrPromoteAdmin(email, "Ada Admin", PASSWORD);
        Browser b = new Browser(TestServer.baseUrl());
        b.login(email, PASSWORD);
        return new Person(b, email, one("select id::text from users where email = ?", email));
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

    /** A multipart upload as a browser form would send it (with the page's CSRF token). */
    private static HttpResponse<String> upload(Browser b, String room, String title, String fileName, byte[] content) {
        String boundary = "----pathwise" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        java.util.function.BiConsumer<String, String> field = (name, value) -> body.writeBytes(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
        field.accept("_csrf", b.csrf());
        field.accept("action", "add-pdf");
        field.accept("title", title);
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
                + "\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(content);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        // Post where the page's own upload form points, as a browser would (falls back for people who can't see the form).
        java.util.regex.Matcher form = java.util.regex.Pattern.compile("enctype=\"multipart/form-data\" action=\"([^\"]+)\"")
                .matcher(b.get("/teach/rooms/" + room + "/materials").body());
        String action = form.find() ? form.group(1) : "/materials/" + room;
        return b.send(HttpRequest.newBuilder(URI.create(TestServer.baseUrl() + action))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())));
    }

    @Test
    void teachersShareNotesAndPdfsWithTheirRoomOnly() throws Exception {
        Person teacher = person("Prof. Notes", "teacher");
        Person student = person("Sam Reader", "student");
        Person outsider = person("Out Sider", "student");
        teacher.b().get("/teach");
        String room = location(teacher.b().post("/teach", Map.of("name", "Materials Room", "subject", "data-structures", "joinPolicy", "open")))
                .substring("/teach/rooms/".length());
        student.b().get("/app/rooms");
        student.b().post("/app/rooms/join", Map.of("code", one("select code from rooms where id = ?::uuid", room)));

        // A note: Markdown rendered, scripts stripped. The page's forms point at this room.
        String materialsPage = teacher.b().get("/teach/rooms/" + room + "/materials").body();
        assertThat(materialsPage).contains("Write a note").contains("Upload a PDF").contains("Materials Room")
                .contains("action=\"/materials/" + room + "\"").doesNotContain("action=\"/materials/\"");
        // The room header (name and tabs) also renders on the other pages that share it.
        for (String section : new String[] {"doubts", "settings"}) {
            assertThat(teacher.b().get("/teach/rooms/" + room + "/" + section).body()).as(section)
                    .contains("Materials Room").contains("/teach/rooms/" + room + "/curriculum");
        }
        teacher.b().post("/materials/" + room, Map.of("action", "add-note", "title", "Traversal cheat sheet",
                "body", "**Preorder**: root first.\n\n<script>alert('x')</script>\n\n- Inorder: left, root, right"));
        String studentView = student.b().get("/app/rooms/" + room).body();
        assertThat(studentView).contains("Materials from your teacher").contains("Traversal cheat sheet")
                .contains("<strong>Preorder</strong>").doesNotContain("<script>alert");

        // A PDF: stored, then served only to members.
        byte[] pdf = "%PDF-1.4\n1 0 obj << >> endobj\ntrailer << >>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        teacher.b().get("/teach/rooms/" + room + "/materials");
        HttpResponse<String> up = upload(teacher.b(), room, "Week 1 slides", "week1 \"slides\".pdf", pdf);
        assertThat(location(up)).isEqualTo("/teach/rooms/" + room + "/materials");
        String material = one("select id::text from room_materials where room_id = ?::uuid and kind = 'pdf'", room);
        assertThat(material).isNotNull();
        assertThat(one("select file_name from room_materials where id = ?::uuid", material)).isEqualTo("week1 slides.pdf");

        HttpResponse<String> got = student.b().get("/materials/" + room + "/" + material);
        assertThat(got.statusCode()).isEqualTo(200);
        assertThat(got.headers().firstValue("Content-Type").orElse("")).startsWith("application/pdf");
        assertThat(got.headers().firstValue("Content-Security-Policy").orElse("")).isEqualTo("sandbox");
        assertThat(got.body()).startsWith("%PDF-1.4");
        assertThat(outsider.b().get("/materials/" + room + "/" + material).statusCode()).isEqualTo(404);
        assertThat(student.b().get("/app/rooms/" + room).body()).contains("Week 1 slides");

        // A realistic size (over the 1 MB in-memory threshold, so Tomcat spools it to disk).
        byte[] big = new byte[2 * 1024 * 1024 + 123];
        System.arraycopy(pdf, 0, big, 0, pdf.length);
        teacher.b().get("/teach/rooms/" + room + "/materials");
        HttpResponse<String> bigUp = upload(teacher.b(), room, "Big handout", "handout.pdf", big);
        assertThat(bigUp.statusCode()).as(bigUp.body().substring(0, Math.min(600, bigUp.body().length()))).isEqualTo(303);
        String bigId = one("select id::text from room_materials where room_id = ?::uuid and title = 'Big handout'", room);
        assertThat(bigId).isNotNull();
        HttpResponse<String> bigGot = student.b().get("/materials/" + room + "/" + bigId);
        assertThat(bigGot.statusCode()).isEqualTo(200);
        assertThat(bigGot.headers().firstValueAsLong("Content-Length").orElse(0)).isEqualTo(big.length);
        teacher.b().post("/materials/" + room, Map.of("action", "delete", "id", bigId));

        // Students can't add or remove; a file that isn't a PDF is refused whatever its name.
        student.b().get("/app/rooms/" + room);
        assertThat(upload(student.b(), room, "Not mine", "x.pdf", pdf).statusCode()).isEqualTo(403);
        assertThat(student.b().post("/materials/" + room, Map.of("action", "delete", "id", material)).statusCode()).isEqualTo(403);
        teacher.b().get("/teach/rooms/" + room + "/materials");
        upload(teacher.b(), room, "Sneaky", "evil.pdf", "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));
        assertThat(teacher.b().get("/teach/rooms/" + room + "/materials").body()).contains("isn&#039;t a PDF");
        assertThat(one("select count(*) from room_materials where room_id = ?::uuid and kind = 'pdf' and deleted_at is null", room)).isEqualTo("1");

        // Removing deletes the file and the link stops working.
        Path stored = Path.of("storage", "materials", material + ".pdf").toAbsolutePath();
        assertThat(Files.exists(stored)).isTrue();
        teacher.b().post("/materials/" + room, Map.of("action", "delete", "id", material));
        assertThat(Files.exists(stored)).isFalse();
        assertThat(student.b().get("/materials/" + room + "/" + material).statusCode()).isEqualTo(404);
        assertThat(one("select count(*) from audit_log where entity_id = ? and action like 'room.material_%'", room)).isEqualTo("5"); // note, 2 PDFs added, 2 removed
    }

    @Test
    void adminsMessagePeopleAndRemoveAccounts() {
        Person admin = admin();
        Person student = person("Mia Message", "student");
        Person teacher = person("Tom Teacher", "teacher");

        // A message to one person, signed by the admin, under their bell.
        admin.b().get("/admin/users/" + student.id());
        admin.b().post("/admin/users/" + student.id(), Map.of("action", "message", "title", "Welcome to Pathwise",
                "body", "Start with the diagnostic.\nAsk in Doubts if you're stuck."));
        String inbox = student.b().get("/app/notifications").body();
        assertThat(inbox).contains("Welcome to Pathwise").contains("Start with the diagnostic.").contains("Ada Admin, administrator")
                .contains("Message from the administrators");

        // A group message: "students" excludes teachers.
        admin.b().get("/admin/messages");
        admin.b().post("/admin/messages", Map.of("audience", "students", "title", "Exam week", "body", "Assessments open Monday."));
        assertThat(one("select count(*) from notifications where user_id = ?::uuid and title = 'Exam week'", student.id())).isEqualTo("1");
        assertThat(one("select count(*) from notifications where user_id = ?::uuid and title = 'Exam week'", teacher.id())).isEqualTo("0");
        assertThat(admin.b().get("/admin/messages").body()).contains("students").contains("Exam week");
        assertThat(student.b().post("/admin/messages", Map.of("audience", "everyone", "title", "Hacked", "body", "x")).statusCode()).isEqualTo(403);

        // Removal needs the password again and a reason.
        String page = "/admin/users/" + student.id();
        assertThat(location(admin.b().post(page, Map.of("action", "remove", "reason", "Duplicate account")))).startsWith("/admin/reauth");
        admin.b().post("/admin/reauth", Map.of("password", PASSWORD, "next", page));
        admin.b().post(page, Map.of("action", "remove", "reason", "x"));
        assertThat(one("select status from users where id = ?::uuid", student.id())).isEqualTo("active");

        // A teacher with an active room can't be removed; an admin can't remove themselves.
        teacher.b().get("/teach");
        teacher.b().post("/teach", Map.of("name", "Keep Me", "subject", "data-structures", "joinPolicy", "open"));
        admin.b().post("/admin/users/" + teacher.id(), Map.of("action", "remove", "reason", "Left the school"));
        assertThat(one("select status from users where id = ?::uuid", teacher.id())).isEqualTo("active");
        assertThat(admin.b().get("/admin/users/" + teacher.id()).body()).contains("Archive them first");
        admin.b().post("/admin/users/" + admin.id(), Map.of("action", "remove", "reason", "Testing self removal"));
        assertThat(one("select status from users where id = ?::uuid", admin.id())).isEqualTo("active");

        // Removing: signed out, can't sign in, name and email wiped, email free again, audited.
        assertThat(location(admin.b().post(page, Map.of("action", "remove", "reason", "Duplicate account")))).isEqualTo("/admin/users");
        assertThat(one("select status from users where id = ?::uuid", student.id())).isEqualTo("deleted");
        assertThat(one("select display_name from users where id = ?::uuid", student.id())).isEqualTo("Removed user");
        assertThat(one("select count(*) from users where email = ?", student.email())).isEqualTo("0");
        assertThat(location(student.b().get("/app/today"))).startsWith("/login");
        assertThat(new Browser(TestServer.baseUrl()).login(student.email(), PASSWORD).body()).doesNotContain("Mia Message");
        assertThat(admin.b().get("/admin/users?q=" + student.email()).body()).doesNotContain("Mia Message");
        assertThat(one("select count(*) from audit_log where entity_id = ? and action = 'admin.user_removed'", student.id())).isEqualTo("1");
        Person again = person("Mia Again", "student");
        assertThat(again.id()).isNotEqualTo(student.id());
    }
}
