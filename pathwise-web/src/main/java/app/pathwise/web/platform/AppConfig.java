package app.pathwise.web.platform;

import app.pathwise.data.DbConfig;

import java.time.Duration;
import java.util.Locale;
import java.util.function.Function;

/**
 * Typed, validated configuration. Precedence: JVM system property {@code pathwise.x.y} &gt;
 * environment variable {@code PATHWISE_X_Y} &gt; default. The launcher loads
 * {@code config/pathwise.local.properties} into system properties before the web app starts.
 */
public record AppConfig(
        Environment environment,
        DbConfig db,
        boolean secureCookies,
        boolean trustProxyHeaders,
        String secret,
        Duration webSessionTtl,
        Duration bearerSessionTtl,
        HashProfile hashProfile,
        int loginAttemptsPerMinute,
        int registrationsPerHour) {

    public enum Environment { DEV, TEST, PRODUCTION }

    /** Password hashing cost. {@code FAST} is only accepted in the TEST environment. */
    public enum HashProfile { STANDARD, FAST }

    public boolean isProduction() {
        return environment == Environment.PRODUCTION;
    }

    public static AppConfig load() {
        return load(AppConfig::lookup);
    }

    /** Loads from an arbitrary key lookup (used by tests). Keys use the dotted form, e.g. {@code pathwise.db.url}. */
    public static AppConfig load(Function<String, String> source) {
        Environment env = Environment.valueOf(get(source, "pathwise.env", "dev").toUpperCase(Locale.ROOT));
        DbConfig db = new DbConfig(
                required(source, "pathwise.db.url"),
                get(source, "pathwise.db.user", "postgres"),
                get(source, "pathwise.db.password", "postgres"),
                Integer.parseInt(get(source, "pathwise.db.pool", "10")));
        String secret = get(source, "pathwise.secret", "");
        if (secret.length() < 32) {
            if (env == Environment.PRODUCTION) {
                throw new IllegalStateException("pathwise.secret must be at least 32 characters in production. Run scripts\\setup.ps1.");
            }
            secret = "dev-only-insecure-secret-" + "0".repeat(16);
        }
        HashProfile hash = HashProfile.valueOf(get(source, "pathwise.hash.profile", "standard").toUpperCase(Locale.ROOT));
        if (hash == HashProfile.FAST && env != Environment.TEST) {
            throw new IllegalStateException("pathwise.hash.profile=fast is only allowed when pathwise.env=test");
        }
        boolean secure = Boolean.parseBoolean(get(source, "pathwise.cookies.secure", env == Environment.PRODUCTION ? "true" : "false"));
        boolean trustProxy = Boolean.parseBoolean(get(source, "pathwise.trust.proxy", "false"));
        return new AppConfig(env, db, secure, trustProxy, secret,
                Duration.ofHours(Long.parseLong(get(source, "pathwise.session.web.hours", "12"))),
                Duration.ofDays(Long.parseLong(get(source, "pathwise.session.bearer.days", "30"))),
                hash,
                Integer.parseInt(get(source, "pathwise.ratelimit.login.per.minute", "5")),
                Integer.parseInt(get(source, "pathwise.ratelimit.register.per.hour", "10")));
    }

    private static String lookup(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key.toUpperCase(Locale.ROOT).replace('.', '_'));
        }
        return value;
    }

    private static String get(Function<String, String> source, String key, String fallback) {
        String value = source.apply(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String required(Function<String, String> source, String key) {
        String value = source.apply(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing configuration: " + key
                    + " (set it in config/pathwise.local.properties or as " + key.toUpperCase(Locale.ROOT).replace('.', '_') + ")");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "AppConfig[environment=" + environment + ", db=" + db + ", secureCookies=" + secureCookies
                + ", trustProxyHeaders=" + trustProxyHeaders + ", secret=***, hashProfile=" + hashProfile + "]";
    }
}
