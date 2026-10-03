package app.pathwise.app;

import app.pathwise.core.identity.PasswordHasher;
import app.pathwise.data.Database;
import app.pathwise.data.identity.UserDao;
import app.pathwise.web.identity.IdentityService;
import app.pathwise.web.platform.AppConfig;
import app.pathwise.web.platform.RequestMeta;
import app.pathwise.web.platform.events.EventBus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Demo accounts for presentations. Idempotent: existing accounts are left untouched. Each run that
 * creates accounts generates fresh random passwords (never a well-known default) and writes them to
 * {@code config/demo-accounts.txt}, which is git-ignored.
 */
public final class DemoSeed {
    private DemoSeed() {}

    record DemoAccount(String email, String name, boolean teacher) {}

    static final List<DemoAccount> ACCOUNTS = List.of(
            new DemoAccount("student@demo.pathwise.local", "Asha Demo", false),
            new DemoAccount("student2@demo.pathwise.local", "Rohan Demo", false),
            new DemoAccount("teacher@demo.pathwise.local", "Prof. Meera Demo", true));

    public static void run(Database db, Path root, boolean force) {
        if (AppConfig.load().isProduction() && !force) {
            Main.fail("Refusing to seed demo accounts in production. Use --force if you really mean it.");
            return;
        }
        IdentityService identity = new IdentityService(db.tx(), new PasswordHasher(), new EventBus(), Clock.systemUTC(),
                Duration.ofHours(12), Duration.ofDays(30));
        UserDao users = new UserDao();
        List<String> lines = new ArrayList<>();
        for (DemoAccount a : ACCOUNTS) {
            boolean exists = db.tx().read(c -> users.emailInUse(c, a.email()));
            if (exists) {
                System.out.println("  exists   " + a.email() + " (password unchanged)");
                continue;
            }
            String password = randomPassword();
            identity.register(new IdentityService.Registration(a.email(), password, a.name(), a.teacher()), RequestMeta.system());
            lines.add(a.email() + "  " + password + "  (" + (a.teacher() ? "teacher" : "student") + ")");
            System.out.println("  created  " + a.email() + "  password: " + password);
        }
        if (!lines.isEmpty()) {
            Path file = root.resolve("config/demo-accounts.txt");
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, String.join(System.lineSeparator(), lines) + System.lineSeparator(),
                        StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            System.out.println("  Saved to config/demo-accounts.txt (git-ignored).");
        }
    }

    /** Four random lowercase words-ish groups: easy to type during a demo, hard to guess. */
    static String randomPassword() {
        String alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int group = 0; group < 4; group++) {
            if (group > 0) {
                sb.append('-');
            }
            for (int i = 0; i < 4; i++) {
                sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
        }
        return sb.toString();
    }
}
