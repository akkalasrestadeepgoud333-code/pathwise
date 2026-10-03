package app.pathwise.web.platform;

import app.pathwise.core.identity.PasswordHasher;
import app.pathwise.core.security.RateLimiter;
import app.pathwise.data.Database;
import app.pathwise.web.admin.AdminService;
import app.pathwise.web.identity.IdentityService;
import app.pathwise.web.platform.events.EventBus;
import app.pathwise.web.platform.events.OutboxWorker;
import jakarta.servlet.ServletContext;

import java.time.Clock;

/**
 * The composition root: every long-lived object is created here once and shared. Stored as a
 * ServletContext attribute so servlets and filters can reach it without static singletons.
 */
public final class AppContext {
    public static final String ATTRIBUTE = AppContext.class.getName();

    private final AppConfig config;
    private final Database database;
    private final Clock clock;
    private final EventBus events;
    private final OutboxWorker outboxWorker;
    private final IdentityService identity;
    private final AdminService admin;
    private final RateLimiter loginLimiter;
    private final RateLimiter registerLimiter;

    public AppContext(AppConfig config, Database database, Clock clock) {
        this.config = config;
        this.database = database;
        this.clock = clock;
        this.events = new EventBus();
        this.outboxWorker = new OutboxWorker(database.tx(), events);
        PasswordHasher hasher = config.hashProfile() == AppConfig.HashProfile.FAST
                ? new PasswordHasher(1024, 1, 1)
                : new PasswordHasher();
        this.identity = new IdentityService(database.tx(), hasher, events, clock, config.webSessionTtl(), config.bearerSessionTtl());
        this.admin = new AdminService(database.tx());
        this.loginLimiter = new RateLimiter(config.loginAttemptsPerMinute(), 60_000, clock);      // per IP + email
        this.registerLimiter = new RateLimiter(config.registrationsPerHour(), 3_600_000, clock);  // per IP
    }

    public static AppContext from(ServletContext servletContext) {
        AppContext ctx = (AppContext) servletContext.getAttribute(ATTRIBUTE);
        if (ctx == null) {
            throw new IllegalStateException("Pathwise is not initialised");
        }
        return ctx;
    }

    public AppConfig config() { return config; }
    public Database database() { return database; }
    public Clock clock() { return clock; }
    public EventBus events() { return events; }
    public OutboxWorker outboxWorker() { return outboxWorker; }
    public IdentityService identity() { return identity; }
    public AdminService admin() { return admin; }
    public RateLimiter loginLimiter() { return loginLimiter; }
    public RateLimiter registerLimiter() { return registerLimiter; }
}
