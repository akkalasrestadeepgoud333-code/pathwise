package app.pathwise.app;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A real PostgreSQL 16 server started from binaries downloaded by Maven, with its data kept in
 * {@code data/pg} between runs. Lets Pathwise run with nothing but a JDK installed.
 */
public final class EmbeddedDatabase implements AutoCloseable {
    public static final String DATABASE = "pathwise";
    private final EmbeddedPostgres postgres;

    private EmbeddedDatabase(EmbeddedPostgres postgres) {
        this.postgres = postgres;
    }

    public static EmbeddedDatabase start(Path dataDir, int port) {
        try {
            Files.createDirectories(dataDir);
            EmbeddedPostgres pg = EmbeddedPostgres.builder()
                    .setDataDirectory(dataDir.toFile())
                    .setCleanDataDirectory(false)
                    .setPort(port)
                    .start();
            EmbeddedDatabase db = new EmbeddedDatabase(pg);
            db.ensureDatabase();
            return db;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the embedded PostgreSQL on port " + port
                    + ". Is another Pathwise running? (" + e.getMessage() + ")", e);
        }
    }

    private void ensureDatabase() {
        try (Connection c = postgres.getPostgresDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement("select 1 from pg_database where datname = ?")) {
            ps.setString(1, DATABASE);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    try (Statement s = c.createStatement()) {
                        s.execute("create database " + DATABASE); // constant name, not user input
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the '" + DATABASE + "' database", e);
        }
    }

    public String jdbcUrl() {
        return postgres.getJdbcUrl("postgres", DATABASE);
    }

    /** Points the web application (same JVM) at this database. */
    public void exportToSystemProperties() {
        System.setProperty("pathwise.db.url", jdbcUrl());
        System.setProperty("pathwise.db.user", "postgres");
        System.setProperty("pathwise.db.password", "postgres");
    }

    @Override
    public void close() {
        try {
            postgres.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
