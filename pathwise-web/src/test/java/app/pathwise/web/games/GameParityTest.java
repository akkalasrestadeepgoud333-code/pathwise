package app.pathwise.web.games;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the JavaScript game rules against the shared vectors with {@code node --test}, so the build fails if
 * the browser's rules drift from the server's. (The Java side of the same vectors runs in pathwise-core.)
 * Skipped, and reported as skipped, if Node.js is not installed.
 */
class GameParityTest {

    @Test
    void browserRulesMatchTheServerVectors() throws IOException, InterruptedException {
        Path test = Path.of("src", "test", "js", "games.test.mjs").toAbsolutePath();
        assertThat(test).exists();
        assumeTrue(nodeAvailable(), "Node.js not found on PATH: JS parity not checked");
        Process p = new ProcessBuilder("node", "--test", "--test-reporter=tap", test.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(p.exitValue()).as(out).isZero();
        assertThat(out).contains("# fail 0").containsPattern("# pass [1-9]");
    }

    private static boolean nodeAvailable() {
        try {
            Process p = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Test
    void vectorsExistForEveryJsGame() {
        Path vectors = Path.of("..", "pathwise-core", "src", "test", "resources", "game-vectors");
        for (String game : new String[] {"traversal_race", "match_pairs", "bug_hunt"}) {
            assertThat(vectors.resolve(game + ".json")).exists();
            assertThat(Files.exists(Path.of("src", "main", "webapp", "static", "js", "games", game, "rules.js"))).isTrue();
        }
    }
}
