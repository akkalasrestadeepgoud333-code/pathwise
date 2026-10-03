package app.pathwise.app.testing;

import app.pathwise.app.PathwiseServer;
import app.pathwise.data.Database;
import app.pathwise.data.DbConfig;
import app.pathwise.data.testing.EmbeddedPg;

import java.nio.file.Path;

/**
 * One real Pathwise server per test JVM: embedded Tomcat serving the JSPs from source, backed by a fresh
 * embedded PostgreSQL database. Tests use unique emails so they never interfere.
 */
public final class TestServer {
    private static PathwiseServer server;
    private static DbConfig dbConfig;

    private TestServer() {}

    public static synchronized String baseUrl() {
        if (server == null) {
            dbConfig = EmbeddedPg.newEmptyDatabase();
            System.setProperty("pathwise.env", "test");
            System.setProperty("pathwise.hash.profile", "fast");
            System.setProperty("pathwise.ratelimit.register.per.hour", "10000"); // every test client shares 127.0.0.1
            System.setProperty("pathwise.secret", "test-secret-test-secret-test-secret-123");
            System.setProperty("pathwise.db.url", dbConfig.url());
            System.setProperty("pathwise.db.user", dbConfig.user());
            System.setProperty("pathwise.db.password", dbConfig.password());
            Path webModule = Path.of(System.getProperty("pathwise.web.dir", "../pathwise-web"));
            server = PathwiseServer.startFromSource("127.0.0.1", 0, webModule);
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        }
        return "http://127.0.0.1:" + server.port();
    }

    /** A direct connection to the server's database, for assertions about stored state. */
    public static Database database() {
        baseUrl();
        return Database.connect(new DbConfig(dbConfig.url(), dbConfig.user(), dbConfig.password(), 2));
    }
}
