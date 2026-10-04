package app.pathwise.web.platform;

import app.pathwise.data.Database;
import app.pathwise.data.identity.SessionDao;
import app.pathwise.data.platform.OutboxDao;
import app.pathwise.web.admin.AdminAssessmentController;
import app.pathwise.web.admin.AdminContentController;
import app.pathwise.web.assessment.AssessmentApiServlet;
import app.pathwise.web.assessment.AssessmentController;
import app.pathwise.web.games.GameApiServlet;
import app.pathwise.web.games.GameController;
import app.pathwise.web.rooms.RoomApiServlet;
import app.pathwise.web.rooms.RoomController;
import app.pathwise.web.rooms.TeachRoomController;
import app.pathwise.web.admin.AdminController;
import app.pathwise.web.identity.AuthApiServlet;
import app.pathwise.web.identity.AuthController;
import app.pathwise.web.identity.MeApiServlet;
import app.pathwise.web.identity.SettingsController;
import app.pathwise.web.engine.LearnerApiServlet;
import app.pathwise.web.engine.NextController;
import app.pathwise.web.learn.LearnApiServlet;
import app.pathwise.web.learner.ProgressController;
import app.pathwise.web.practice.PracticeController;
import app.pathwise.web.learn.LearnController;
import app.pathwise.web.learn.ModuleController;
import app.pathwise.web.pages.HomeController;
import app.pathwise.web.pages.StudentController;
import app.pathwise.web.pages.TeacherController;
import app.pathwise.web.platform.http.AuthFilter;
import app.pathwise.web.platform.http.CsrfFilter;
import app.pathwise.web.platform.http.ErrorServlet;
import app.pathwise.web.platform.http.HealthServlet;
import app.pathwise.web.platform.http.RequestIdFilter;
import app.pathwise.web.platform.http.SecurityHeadersFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.http.HttpServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Starts Pathwise inside the servlet container (declared in web.xml): loads configuration, migrates the
 * database, builds the {@link AppContext}, registers every filter and servlet explicitly (no classpath
 * scanning), and starts the background worker.
 */
public class AppBootstrap implements ServletContextListener {
    private static final Logger log = LoggerFactory.getLogger(AppBootstrap.class);
    private ScheduledExecutorService scheduler;
    private Database database;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext sc = event.getServletContext();
        AppConfig config = AppConfig.load();
        log.info("Starting Pathwise with {}", config);

        database = Database.connect(config.db());
        database.migrate();
        AppContext app = new AppContext(config, database, Clock.systemUTC());
        sc.setAttribute(AppContext.ATTRIBUTE, app);
        sc.getSessionCookieConfig().setSecure(config.secureCookies());
        sc.getSessionCookieConfig().setHttpOnly(true);
        sc.getSessionCookieConfig().setName("PW_SESSION");

        filter(sc, "requestId", new RequestIdFilter());
        filter(sc, "securityHeaders", new SecurityHeadersFilter(config.isProduction()));
        filter(sc, "auth", new AuthFilter());
        filter(sc, "csrf", new CsrfFilter());

        servlet(sc, "home", new HomeController(), "");
        servlet(sc, "auth", new AuthController(), "/login", "/register", "/logout");
        servlet(sc, "student", new StudentController(), "/app", "/app/today");
        servlet(sc, "settings", new SettingsController(), "/app/settings", "/app/settings/*");
        servlet(sc, "teacher", new TeacherController(), "/teach");
        servlet(sc, "teachRooms", new TeachRoomController(), "/teach/rooms/*");
        servlet(sc, "rooms", new RoomController(), "/app/rooms", "/app/rooms/*", "/join/*");
        servlet(sc, "apiRooms", new RoomApiServlet(), "/api/v1/rooms", "/api/v1/rooms/*");
        servlet(sc, "learn", new LearnController(), "/app/learn", "/app/map/*", "/app/topics/*");
        servlet(sc, "modules", new ModuleController(), "/app/modules/*");
        servlet(sc, "practice", new PracticeController(), "/app/practice", "/app/practice/*");
        servlet(sc, "next", new NextController(), "/app/next", "/app/next/*");
        servlet(sc, "progress", new ProgressController(), "/app/progress");
        servlet(sc, "apiLearner", new LearnerApiServlet(), "/api/v1/me/recommendations", "/api/v1/me/mastery",
                "/api/v1/recommendations/*", "/api/v1/practice", "/api/v1/practice/*");
        servlet(sc, "assessments", new AssessmentController(), "/app/assessments", "/app/assessments/*");
        servlet(sc, "apiAssessments", new AssessmentApiServlet(), "/api/v1/assessments", "/api/v1/assessments/*");
        servlet(sc, "games", new GameController(), "/app/games", "/app/games/*");
        servlet(sc, "apiGames", new GameApiServlet(), "/api/v1/games", "/api/v1/games/*");
        servlet(sc, "admin", new AdminController(), "/admin");
        servlet(sc, "adminAssessments", new AdminAssessmentController(), "/admin/assessments", "/admin/assessments/*");
        servlet(sc, "adminContent", new AdminContentController(), "/admin/content");
        servlet(sc, "apiLearn", new LearnApiServlet(), "/api/v1/subjects", "/api/v1/subjects/*", "/api/v1/modules/*");
        servlet(sc, "apiAuth", new AuthApiServlet(), "/api/v1/auth/*");
        servlet(sc, "apiMe", new MeApiServlet(), "/api/v1/me");
        servlet(sc, "health", new HealthServlet(), "/healthz");
        servlet(sc, "error", new ErrorServlet(), "/error");

        startBackgroundWork(app);
        log.info("Pathwise is ready");
    }

    private void startBackgroundWork(AppContext app) {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pathwise-worker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(guard("outbox", () -> app.outboxWorker().drain()), 1, 1, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(guard("assessment-deadlines", () -> {
            int n = app.assessments().sweepOverdue();
            if (n > 0) {
                log.info("Auto-submitted {} assessment attempts whose time ran out", n);
            }
        }), 30, 60, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(guard("housekeeping", () -> {
            Instant cutoff = app.clock().instant().minus(Duration.ofDays(30));
            int sessions = app.database().tx().call(c -> new SessionDao().purge(c, cutoff));
            int events = app.database().tx().call(c -> new OutboxDao().purgeProcessed(c, Duration.ofDays(7)));
            app.loginLimiter().evictIdle();
            app.registerLimiter().evictIdle();
            if (sessions + events > 0) {
                log.info("Housekeeping removed {} old sessions and {} processed events", sessions, events);
            }
        }), 1, 60, TimeUnit.MINUTES);
    }

    private static Runnable guard(String name, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                // A failing pass must not cancel the schedule; it is retried on the next tick.
                log.warn("Background task '{}' failed: {}", name, e.toString());
            }
        };
    }

    private static void filter(ServletContext sc, String name, Filter filter) {
        FilterRegistration.Dynamic reg = sc.addFilter(name, filter);
        reg.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
    }

    private static void servlet(ServletContext sc, String name, HttpServlet servlet, String... mappings) {
        sc.addServlet(name, servlet).addMapping(mappings);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (database != null) {
            database.close();
        }
        log.info("Pathwise stopped");
    }
}
