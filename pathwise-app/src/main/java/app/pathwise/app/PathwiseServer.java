package app.pathwise.app;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Valve;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.tomcat.util.http.Rfc6265CookieProcessor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Embedded Tomcat 10.1 running the Pathwise web application, either from source (development: JSPs
 * and static files reload without a rebuild) or from the packaged WAR.
 */
public final class PathwiseServer implements AutoCloseable {
    private final Tomcat tomcat;

    private PathwiseServer(Tomcat tomcat) {
        this.tomcat = tomcat;
    }

    /** Serves {@code <webModule>/src/main/webapp} with classes from {@code <webModule>/target/classes}. */
    public static PathwiseServer startFromSource(String host, int port, Path webModule) {
        Path docBase = webModule.resolve("src/main/webapp").toAbsolutePath().normalize();
        Path classes = webModule.resolve("target/classes").toAbsolutePath().normalize();
        if (!Files.isDirectory(docBase) || !Files.isDirectory(classes)) {
            throw new IllegalStateException("Web module not built at " + webModule.toAbsolutePath()
                    + " (expected src/main/webapp and target/classes). Run: mvnw -q install -DskipTests");
        }
        Tomcat tomcat = baseTomcat(host, port);
        Context ctx = tomcat.addWebapp("", docBase.toString());
        WebResourceRoot resources = new StandardRoot(ctx);
        resources.addPreResources(new DirResourceSet(resources, "/WEB-INF/classes", classes.toString(), "/"));
        ctx.setResources(resources);
        configure(ctx);
        return start(tomcat);
    }

    /** Serves a packaged {@code pathwise-web.war}. */
    public static PathwiseServer startFromWar(String host, int port, Path war) {
        if (!Files.isRegularFile(war)) {
            throw new IllegalStateException("WAR not found: " + war.toAbsolutePath());
        }
        Tomcat tomcat = baseTomcat(host, port);
        Context ctx = tomcat.addWebapp("", war.toAbsolutePath().toString());
        configure(ctx);
        return start(tomcat);
    }

    private static Tomcat baseTomcat(String host, int port) {
        Path base;
        try {
            base = Files.createTempDirectory("pathwise-tomcat");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(base.toString());
        Connector connector = new Connector();
        connector.setPort(port);
        connector.setProperty("address", host);
        connector.setProperty("compression", "on");
        connector.setProperty("compressibleMimeType", "text/html,text/css,application/javascript,application/json,image/svg+xml");
        connector.setProperty("maxPostSize", Integer.toString(2 * 1024 * 1024));
        connector.setProperty("relaxedQueryChars", "");
        connector.setXpoweredBy(false);
        tomcat.setConnector(connector);
        tomcat.getHost().setAutoDeploy(false);
        return tomcat;
    }

    private static void configure(Context ctx) {
        Rfc6265CookieProcessor cookies = new Rfc6265CookieProcessor();
        cookies.setSameSiteCookies("lax");
        ctx.setCookieProcessor(cookies);
        StandardManager manager = new StandardManager();
        manager.setPathname(null); // do not persist HttpSessions to disk; Pathwise sessions live in the database
        ctx.setManager(manager);
        ctx.setUseHttpOnly(true);
    }

    private static PathwiseServer start(Tomcat tomcat) {
        try {
            tomcat.start();
        } catch (LifecycleException e) {
            throw new IllegalStateException("Tomcat failed to start: " + e.getMessage(), e);
        }
        // Never show Tomcat's default error pages or version in responses.
        for (Valve valve : ((StandardHost) tomcat.getHost()).getPipeline().getValves()) {
            if (valve instanceof ErrorReportValve erv) {
                erv.setShowReport(false);
                erv.setShowServerInfo(false);
            }
        }
        if (tomcat.getConnector().getLocalPort() < 0) {
            throw new IllegalStateException("Could not bind the HTTP port. Is another Pathwise already running?");
        }
        if (!tomcat.getHost().findChildren()[0].getState().isAvailable()) {
            throw new IllegalStateException("The Pathwise web application failed to start. See the log above.");
        }
        return new PathwiseServer(tomcat);
    }

    public int port() {
        return tomcat.getConnector().getLocalPort();
    }

    public void await() {
        tomcat.getServer().await();
    }

    @Override
    public void close() {
        try {
            tomcat.stop();
            tomcat.destroy();
        } catch (LifecycleException e) {
            throw new IllegalStateException(e);
        }
    }
}
