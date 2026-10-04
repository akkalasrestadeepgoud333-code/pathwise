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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A single-user timing check of the main pages (not a load test): warm each page up, time 20 sequential requests,
 * and report the median and 95th percentile to {@code target/perf.txt}. Fails only on a gross regression (p95 over
 * 2 s), since timings depend on the machine.
 */
class PerformanceSmokeTest {
    private static final String PASSWORD = "trees grow upside down";
    private static Database db;

    @BeforeAll
    static void setUp() {
        db = TestServer.database();
        new ContentPackService(db.tx(), new ContentPackLoader(PerformanceSmokeTest.class.getClassLoader()),
                new EventBus(), Clock.systemUTC()).importPack("data-structures", null, false);
    }

    @AfterAll
    static void tearDown() {
        db.close();
    }

    @Test
    void mainPagesRespondQuicklyForOneUser() throws Exception {
        Browser b = new Browser(TestServer.baseUrl());
        b.register("Timed Student", "perf-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com", PASSWORD, "student");
        b.get("/app/practice");
        b.post("/app/practice/start", Map.of("mode", "quick")); // some evidence-free activity to make pages realistic

        List<String> pages = List.of("/app/today", "/app/next", "/app/learn", "/app/map/data-structures",
                "/app/topics/data-structures/tree-traversals", "/app/modules/tree-traversals/overview", "/app/progress",
                "/app/assessments", "/app/games", "/app/doubts", "/app/achievements", "/app/live");
        List<String> report = new ArrayList<>();
        report.add(String.format("%-48s %8s %8s %8s", "page (20 sequential requests, warm)", "median", "p95", "max"));
        long worstP95 = 0;
        for (String page : pages) {
            for (int i = 0; i < 3; i++) {
                assertThat(b.get(page).statusCode()).as(page).isEqualTo(200);
            }
            long[] ms = new long[20];
            for (int i = 0; i < ms.length; i++) {
                long t = System.nanoTime();
                b.get(page);
                ms[i] = (System.nanoTime() - t) / 1_000_000;
            }
            Arrays.sort(ms);
            long p95 = ms[18];
            worstP95 = Math.max(worstP95, p95);
            report.add(String.format("%-48s %6d ms %6d ms %6d ms", page, ms[10], p95, ms[19]));
        }
        report.add("");
        report.add("Machine: " + System.getProperty("os.name") + ", " + Runtime.getRuntime().availableProcessors() + " cores, JDK "
                + System.getProperty("java.version") + "; server and embedded PostgreSQL on the same machine as the client.");
        Path out = Path.of("target", "perf.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, String.join("\n", report) + "\n", StandardCharsets.UTF_8);
        report.forEach(System.out::println);
        assertThat(worstP95).as("slowest page p95 (ms)").isLessThan(2000);
    }
}
