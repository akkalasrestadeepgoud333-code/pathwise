package app.pathwise.app;

import app.pathwise.app.testing.Browser;
import app.pathwise.app.testing.TestServer;
import app.pathwise.core.identity.PlatformRole;
import app.pathwise.core.security.Actor;
import app.pathwise.data.Database;
import app.pathwise.data.questions.QuestionDao;
import app.pathwise.web.ai.AiProvider;
import app.pathwise.web.ai.TutorService;
import app.pathwise.web.content.ContentPackLoader;
import app.pathwise.web.content.ContentPackService;
import app.pathwise.web.platform.AppConfig;
import app.pathwise.web.platform.events.EventBus;
import app.pathwise.web.questions.QuestionService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static app.pathwise.app.testing.Browser.location;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M8: the tutor with AI off (Guided Help from Pathwise content), its modes, assessment AI policies, privacy,
 * and the hint answer-leak guard with a stub local model.
 */
class TutorFlowTest {
    private static final String PASSWORD = "trees grow upside down";
    private static Database db;

    @BeforeAll
    static void setUp() {
        db = TestServer.database();
        var r = new ContentPackService(db.tx(), new ContentPackLoader(TutorFlowTest.class.getClassLoader()),
                new EventBus(), Clock.systemUTC()).importPack("data-structures", null, false);
        assertThat(r.outcome()).isNotEqualTo(ContentPackService.Outcome.INVALID);
    }

    @AfterAll
    static void tearDown() {
        db.close();
    }

    private record Person(Browser b, String id) {}

    private static Person student(String name) {
        String email = "tu-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        Browser b = new Browser(TestServer.baseUrl());
        b.register(name, email, PASSWORD, "student");
        return new Person(b, one("select id from users where email = ?", email));
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

    private static String practice(Person p, String skillKey) {
        p.b().get("/app/practice");
        HttpResponse<String> r = p.b().post("/app/practice/start", Map.of("mode", "practice", "skill", one("select id from skills where key = ?", skillKey)));
        assertThat(location(r)).startsWith("/app/practice/");
        return location(r).substring("/app/practice/".length());
    }

    private static String tutor(Person p, Map<String, String> form) {
        p.b().get("/app/tutor");
        HttpResponse<String> r = p.b().post("/app/tutor/start", form);
        return location(r);
    }

    private static QuestionDao.QuestionRow question(String attempt, int position) {
        String qid = one("select question_id from activity_attempt_items where attempt_id = ?::uuid and position = " + (position - 1), attempt);
        return db.tx().read(c -> new QuestionDao().byIds(c, List.of(UUID.fromString(qid)))).get(0);
    }

    @Test
    void guidedHelpExplainsFromPathwiseContentAndSaysSo() {
        Person s = student("Tutee Tara");
        String home = s.b().get("/app/tutor").body();
        assertThat(home).contains("Guided Help (AI off)").contains("never pretends to be an AI");

        // About a module: links to Pathwise material, labelled as Guided Help.
        String loc = tutor(s, Map.of("mode", "tutor", "module", "tree-traversals"));
        assertThat(loc).startsWith("/app/tutor/");
        String chat = s.b().get(loc).body();
        assertThat(chat).contains("About ").contains("Guided Help · from Pathwise content").contains("/app/modules/");

        // A free question in resource mode, then a follow-up.
        String res = tutor(s, Map.of("mode", "resource", "q", "How does a queue differ from a stack?"));
        String resChat = s.b().get(res).body();
        assertThat(resChat).contains("Pathwise has these on that").contains("/app/modules/");
        s.b().post(res, Map.of("action", "ask", "q", "And what about a heap?"));
        assertThat(s.b().get(res).body()).contains("And what about a heap?");
        assertThat(Integer.parseInt(one("select count(*) from ai_messages where conversation_id = ?::uuid", res.substring("/app/tutor/".length()))))
                .isEqualTo(4);
    }

    @Test
    void hintsStepThroughTheLadderAndExplainUsesTheRationales() {
        Person s = student("Hinted Hari");
        String attempt = practice(s, "preorder");
        String page = s.b().get("/app/practice/" + attempt).body();
        assertThat(page).contains("Ask the tutor for a hint");
        int hints = QuestionService.hints(question(attempt, 1)).size();

        String loc = tutor(s, Map.of("mode", "hint", "attempt", attempt, "position", "1"));
        String chat = s.b().get(loc).body();
        if (hints > 0) {
            assertThat(chat).contains("Hint 1 of " + hints);
            s.b().post(loc, Map.of("action", "ask", "q", "Another one please"));
            assertThat(s.b().get(loc).body()).contains(hints > 1 ? "Hint 2 of " + hints : "every hint for this question");
        } else {
            assertThat(chat).contains("no written hints");
        }
        // Explaining before answering is refused; afterwards it uses the authored rationales.
        assertThat(location(s.b().post("/app/tutor/start", Map.of("mode", "explain", "attempt", attempt, "position", "1")))).isEqualTo("/app/tutor");
        assertThat(s.b().get("/app/tutor").body()).contains("Answer the question first");
        QuestionDao.QuestionRow q = question(attempt, 1);
        Map<String, String> wrong = q.options().isEmpty() ? Map.of("text", "zzz")
                : Map.of("option", q.options().stream().filter(o -> !o.correct()).findFirst().orElseThrow().id().toString());
        s.b().get("/app/practice/" + attempt);
        s.b().post("/app/practice/" + attempt + "/1/answer", wrong);
        assertThat(s.b().get("/app/practice/" + attempt + "/1/feedback").body()).contains("Explain my mistake");
        String ex = tutor(s, Map.of("mode", "explain", "attempt", attempt, "position", "1"));
        assertThat(ex).as(() -> s.b().get("/app/tutor").body().replaceAll("(?s).*<main", "").substring(0, 600)).startsWith("/app/tutor/");
        assertThat(s.b().get(ex).body()).containsAnyOf("Correct answer", "Expected:");
        // Hints aren't offered for answered questions; and someone else's attempt can't be used.
        assertThat(location(s.b().post("/app/tutor/start", Map.of("mode", "hint", "attempt", attempt, "position", "1")))).isEqualTo("/app/tutor");
        Person other = student("Other Ola");
        other.b().get("/app/tutor");
        assertThat(other.b().post("/app/tutor/start", Map.of("mode", "explain", "attempt", attempt, "position", "1")).statusCode()).isEqualTo(404);
    }

    @Test
    void conversationsArePrivateAndDeletable() {
        Person s = student("Private Pia");
        String loc = tutor(s, Map.of("mode", "tutor", "q", "What is a balance factor?"));
        Person other = student("Snoop Sam");
        assertThat(other.b().get(loc).statusCode()).isEqualTo(404);
        other.b().get("/app/tutor");
        assertThat(other.b().post(loc, Map.of("action", "delete")).statusCode()).isEqualTo(404);
        s.b().get(loc);
        s.b().post(loc, Map.of("action", "delete"));
        assertThat(s.b().get(loc).statusCode()).isEqualTo(404);
        assertThat(one("select count(*) from ai_messages where conversation_id = ?::uuid", loc.substring("/app/tutor/".length()))).isEqualTo("0");
    }

    @Test
    void blockedAssessmentsTurnTheTutorOff() {
        Person s = student("Exam Eli");
        s.b().get("/app/assessments/ds-diagnostic");
        String attempt = location(s.b().post("/app/assessments/ds-diagnostic/start", Map.of())).substring("/app/assessments/attempt/".length());
        assertThat(s.b().get("/app/tutor").body()).contains("The tutor is off while you have an assessment in progress");
        assertThat(location(s.b().post("/app/tutor/start", Map.of("mode", "tutor", "q", "What is preorder?")))).isEqualTo("/app/tutor");
        assertThat(one("select count(*) from ai_conversations where user_id = ?::uuid", s.id())).isEqualTo("0");
        s.b().get("/app/assessments/attempt/" + attempt);
        s.b().post("/app/assessments/attempt/" + attempt + "/submit", Map.of("confirm", "yes"));
        assertThat(tutor(s, Map.of("mode", "tutor", "q", "What is preorder?"))).startsWith("/app/tutor/");
    }

    @Test
    void aLocalModelAnswersButHintsThatLeakTheAnswerAreReplaced() throws Exception {
        Person s = student("Model Mia");
        String attempt = practice(s, "preorder");
        List<String> answers = QuestionService.answerTexts(question(attempt, 1));
        AtomicReference<String> reply = new AtomicReference<>("The answer is " + answers.get(0) + ".");
        AtomicInteger calls = new AtomicInteger();
        HttpServer stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/api/chat", ex -> {
            ex.getRequestBody().readAllBytes();
            calls.incrementAndGet();
            byte[] out = ("{\"message\":{\"content\":" + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(reply.get()) + "}}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        stub.start();
        try {
            TutorService tutor = new TutorService(db.tx(), AiProvider.from(new AppConfig.Ai("ollama", "stub-model",
                    "http://127.0.0.1:" + stub.getAddress().getPort(), Duration.ofSeconds(5))), new QuestionService(db.tx()), Clock.systemUTC());
            Actor actor = new Actor(UUID.fromString(s.id()), "Model Mia", Set.of(PlatformRole.STUDENT), UUID.randomUUID());
            UUID conv = tutor.start(actor, "hint", new TutorService.Ref(null, UUID.fromString(attempt), 1), null);
            // It leaked twice: the authored hint (or Guided Help) is stored instead, never the model's answer.
            assertThat(calls.get()).as("answers=" + answers + " stored=" + one("select content from ai_messages where conversation_id = ?::uuid and role = 'assistant'", conv)).isEqualTo(2);
            assertThat(one("select source from ai_messages where conversation_id = ?::uuid and role = 'assistant'", conv)).isEqualTo("guided");
            assertThat(one("select content from ai_messages where conversation_id = ?::uuid and role = 'assistant'", conv))
                    .doesNotContain("The answer is");
            // A safe hint is kept and labelled as AI.
            reply.set("Start at the root, then think about which subtree comes first.");
            tutor.ask(actor, conv, "Another hint?");
            assertThat(one("select source from ai_messages where conversation_id = ?::uuid and role = 'assistant' order by id desc limit 1", conv))
                    .isEqualTo("ai");
            assertThat(tutor.chat(actor, conv).messages().get(3).sourceLabel()).contains("may be wrong");
        } finally {
            stub.stop(0);
        }
    }
}
