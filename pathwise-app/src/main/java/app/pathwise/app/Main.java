package app.pathwise.app;

import app.pathwise.core.identity.PasswordHasher;
import app.pathwise.core.identity.PasswordPolicy;
import app.pathwise.data.Database;
import app.pathwise.web.identity.IdentityService;
import app.pathwise.web.platform.AppConfig;
import app.pathwise.web.platform.events.EventBus;

import java.io.Console;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pathwise command line: {@code serve | migrate | create-admin | seed-demo | doctor | help}.
 * Run from the project root (scripts\dev.ps1 does this for you).
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        if (System.getProperty("logback.configurationFile") == null) {
            System.setProperty("logback.configurationFile", "pathwise-logback.xml");
        }
        String command = args.length == 0 ? "help" : args[0];
        Map<String, String> opts = options(Arrays.copyOfRange(args, Math.min(1, args.length), args.length));
        Path root = Path.of(opts.getOrDefault("root", ".")).toAbsolutePath().normalize();

        switch (command) {
            case "serve" -> serve(root, opts);
            case "migrate" -> withDatabase(root, db -> System.out.println("Database is up to date (schema version "
                    + Database.flyway(db.dataSource()).info().current().getVersion() + ")."));
            case "create-admin" -> withDatabase(root, db -> createAdmin(db, opts));
            case "seed-demo" -> withDatabase(root, db -> DemoSeed.run(db, root, opts.containsKey("force")));
            case "import-content" -> withDatabase(root, db -> importContent(db, opts));
            case "doctor" -> System.exit(Doctor.run(root) ? 0 : 1);
            default -> help();
        }
    }

    private static void serve(Path root, Map<String, String> opts) {
        if (LocalConfig.ensure(root)) {
            System.out.println("Created " + LocalConfig.FILE + " with a new random secret.");
        }
        LocalConfig.load(root);
        EmbeddedDatabase embedded = startEmbeddedIfConfigured(root, true);
        String host = opts.getOrDefault("host", LocalConfig.get("pathwise.http.host", "127.0.0.1"));
        int port = Integer.parseInt(opts.getOrDefault("port", LocalConfig.get("pathwise.http.port", "8080")));
        PathwiseServer server = opts.containsKey("war")
                ? PathwiseServer.startFromWar(host, port, Path.of(opts.get("war")))
                : PathwiseServer.startFromSource(host, port, root.resolve(opts.getOrDefault("web-dir", "pathwise-web")));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            if (embedded != null) {
                embedded.close();
            }
        }));
        System.out.println();
        System.out.println("  Pathwise is running at http://" + ("0.0.0.0".equals(host) ? "localhost" : host) + ":" + server.port());
        System.out.println("  Press Ctrl+C to stop.");
        System.out.println();
        server.await();
    }

    @FunctionalInterface
    interface DbWork {
        void run(Database db) throws Exception;
    }

    private static void withDatabase(Path root, DbWork work) throws Exception {
        LocalConfig.ensure(root);
        LocalConfig.load(root);
        // A null resource (database already running, or external mode) is simply not closed.
        try (EmbeddedDatabase embedded = startEmbeddedIfConfigured(root, false)) {
            AppConfig config = AppConfig.load();
            try (Database db = Database.connect(config.db())) {
                db.migrate();
                work.run(db);
            }
        }
    }

    /**
     * Starts the embedded PostgreSQL when {@code pathwise.db.mode=embedded}. If it is already running
     * (for example, the server is up and an admin runs create-admin), connects to it instead.
     */
    static EmbeddedDatabase startEmbeddedIfConfigured(Path root, boolean announce) {
        if (!"embedded".equals(LocalConfig.get("pathwise.db.mode", "embedded"))) {
            return null;
        }
        int port = Integer.parseInt(LocalConfig.get("pathwise.db.embedded.port", "54329"));
        String url = "jdbc:postgresql://localhost:" + port + "/" + EmbeddedDatabase.DATABASE;
        if (reachable(url)) {
            System.setProperty("pathwise.db.url", url);
            System.setProperty("pathwise.db.user", "postgres");
            System.setProperty("pathwise.db.password", "postgres");
            return null;
        }
        if (announce) {
            System.out.println("Starting embedded PostgreSQL (data in data/pg)...");
        }
        EmbeddedDatabase db = EmbeddedDatabase.start(root.resolve("data/pg"), port);
        db.exportToSystemProperties();
        return db;
    }

    private static boolean reachable(String url) {
        try (var c = DriverManager.getConnection(url, "postgres", "postgres")) {
            return c.isValid(1);
        } catch (Exception e) {
            return false;
        }
    }

    private static void createAdmin(Database db, Map<String, String> opts) {
        Console console = System.console();
        String email = opts.get("email");
        String name = opts.get("name");
        if (email == null && console != null) {
            email = console.readLine("Admin email: ");
        }
        if (name == null && console != null) {
            name = console.readLine("Display name: ");
        }
        if (email == null || email.isBlank() || name == null || name.isBlank()) {
            fail("Usage: create-admin --email you@example.com --name \"Your Name\"");
        }
        String password = System.getenv("PATHWISE_ADMIN_PASSWORD");
        if (password == null) {
            if (console == null) {
                fail("No console available. Set PATHWISE_ADMIN_PASSWORD for non-interactive use.");
            }
            char[] first = console.readPassword("Password (min %d chars): ", PasswordPolicy.MIN_LENGTH);
            char[] second = console.readPassword("Repeat password: ");
            if (!Arrays.equals(first, second)) {
                fail("Passwords did not match.");
            }
            password = new String(first);
        }
        var problem = PasswordPolicy.check(password, email);
        if (problem.isPresent()) {
            fail(problem.get());
        }
        IdentityService identity = new IdentityService(db.tx(), new PasswordHasher(), new EventBus(), Clock.systemUTC(),
                java.time.Duration.ofHours(12), java.time.Duration.ofDays(30));
        identity.createOrPromoteAdmin(email, name, password);
        System.out.println("Administrator ready: " + email.trim().toLowerCase());
    }

    private static void importContent(Database db, Map<String, String> opts) {
        var service = new app.pathwise.web.content.ContentPackService(db.tx(),
                new app.pathwise.web.content.ContentPackLoader(Main.class.getClassLoader()), new EventBus(), Clock.systemUTC());
        boolean force = opts.containsKey("force");
        var results = opts.containsKey("pack")
                ? java.util.List.of(service.importPack(opts.get("pack"), null, force))
                : service.importAll(force);
        boolean failed = false;
        for (var r : results) {
            switch (r.outcome()) {
                case IMPORTED -> System.out.println("  imported  " + r.key() + " (" + r.topics() + " topics, " + r.modules()
                        + " modules, " + r.sections() + " sections, " + r.questions() + " questions)");
                case UNCHANGED -> System.out.println("  unchanged " + r.key());
                case INVALID -> {
                    failed = true;
                    System.out.println("  INVALID   " + r.key());
                    r.errors().forEach(e -> System.out.println("            - " + e));
                }
            }
        }
        if (failed) {
            System.exit(1);
        }
    }

    static Map<String, String> options(String[] args) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                String value = i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "true";
                out.put(key, value);
            }
        }
        return out;
    }

    private static void help() {
        System.out.println("""
                Pathwise command line

                  serve          Start Pathwise (embedded Tomcat + embedded PostgreSQL)
                                   --port 8080  --host 127.0.0.1  --war path\\to\\pathwise-web.war
                  migrate        Apply database migrations
                  create-admin   Create or promote an administrator  --email x --name "Name"
                  seed-demo      Create demo accounts (refused in production unless --force)
                  import-content Import bundled content packs   --pack data-structures  --force
                  doctor         Check this machine's setup
                """);
        if (!Files.exists(Path.of(LocalConfig.FILE))) {
            System.out.println("Tip: run scripts\\setup.ps1 first.");
        }
    }

    static void fail(String message) {
        System.err.println(message);
        System.exit(2);
    }
}
