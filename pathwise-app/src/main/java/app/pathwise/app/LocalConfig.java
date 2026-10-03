package app.pathwise.app;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

/**
 * {@code config/pathwise.local.properties}: created automatically on first run with a random secret,
 * then loaded into system properties (values already set by -D or environment variables win).
 * The file is git-ignored; it never needs editing for local development.
 */
public final class LocalConfig {
    public static final String FILE = "config/pathwise.local.properties";

    private LocalConfig() {}

    /** Creates the file if missing. Returns true when it was created. */
    public static boolean ensure(Path root) {
        Path file = root.resolve(FILE);
        if (Files.exists(file)) {
            return false;
        }
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        Properties p = new Properties();
        p.setProperty("pathwise.env", "dev");
        p.setProperty("pathwise.secret", Base64.getUrlEncoder().withoutPadding().encodeToString(secret));
        p.setProperty("pathwise.db.mode", "embedded");
        p.setProperty("pathwise.db.embedded.port", "54329");
        p.setProperty("pathwise.http.port", "8080");
        p.setProperty("pathwise.http.host", "127.0.0.1");
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                p.store(w, " Pathwise local configuration (generated). Keep this file private: it contains a secret.\n"
                        + " To use an installed PostgreSQL instead of the embedded one, set pathwise.db.mode=external and\n"
                        + " pathwise.db.url / pathwise.db.user / pathwise.db.password.");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + file, e);
        }
        return true;
    }

    /** Loads the file into system properties without overriding values that are already set. */
    public static void load(Path root) {
        Path file = root.resolve(FILE);
        if (!Files.exists(file)) {
            return;
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
        for (String key : p.stringPropertyNames()) {
            String envName = key.toUpperCase().replace('.', '_');
            if (System.getProperty(key) == null && System.getenv(envName) == null) {
                System.setProperty(key, p.getProperty(key));
            }
        }
    }

    public static String get(String key, String fallback) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            v = System.getenv(key.toUpperCase().replace('.', '_'));
        }
        return v == null || v.isBlank() ? fallback : v.trim();
    }
}
