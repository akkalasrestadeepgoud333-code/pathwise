package app.pathwise.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CliTest {

    @Test
    void parsesOptions() {
        Map<String, String> o = Main.options(new String[]{"--port", "9090", "--force", "--name", "Asha Rao"});
        assertThat(o).containsEntry("port", "9090").containsEntry("force", "true").containsEntry("name", "Asha Rao");
    }

    @Test
    void localConfigIsGeneratedOnceWithARandomSecret(@TempDir Path root) throws Exception {
        assertThat(LocalConfig.ensure(root)).isTrue();
        String first = Files.readString(root.resolve(LocalConfig.FILE));
        assertThat(first).contains("pathwise.secret=").contains("pathwise.db.mode=embedded");
        assertThat(LocalConfig.ensure(root)).as("never overwritten").isFalse();
        assertThat(Files.readString(root.resolve(LocalConfig.FILE))).isEqualTo(first);

        Path other = root.resolve("other");
        LocalConfig.ensure(other);
        String secretA = first.lines().filter(l -> l.startsWith("pathwise.secret=")).findFirst().orElseThrow();
        String secretB = Files.readString(other.resolve(LocalConfig.FILE)).lines()
                .filter(l -> l.startsWith("pathwise.secret=")).findFirst().orElseThrow();
        assertThat(secretA).isNotEqualTo(secretB);
    }

    @Test
    void demoPasswordsAreRandomAndPassThePolicy() {
        String a = DemoSeed.randomPassword();
        String b = DemoSeed.randomPassword();
        assertThat(a).matches("[a-z2-9]{4}(-[a-z2-9]{4}){3}").isNotEqualTo(b);
        assertThat(app.pathwise.core.identity.PasswordPolicy.check(a, "student@demo.pathwise.local")).isEmpty();
    }
}
