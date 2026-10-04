package app.pathwise.web.platform;

import app.pathwise.core.identity.PasswordHasher;
import app.pathwise.core.security.RateLimiter;
import app.pathwise.data.Database;
import app.pathwise.web.admin.AdminService;
import app.pathwise.web.assessment.AssessmentService;
import app.pathwise.web.community.DoubtService;
import app.pathwise.web.rewards.RewardsService;
import app.pathwise.web.ai.AiProvider;
import app.pathwise.web.ai.TutorService;
import app.pathwise.web.games.GameService;
import app.pathwise.web.rooms.AssignmentService;
import app.pathwise.web.rooms.RoomAssessmentService;
import app.pathwise.web.rooms.RoomInsightService;
import app.pathwise.web.rooms.RoomService;
import app.pathwise.web.catalog.CatalogService;
import app.pathwise.web.content.ContentPackLoader;
import app.pathwise.web.content.ContentPackService;
import app.pathwise.web.content.ContentService;
import app.pathwise.web.engine.RecommendationService;
import app.pathwise.web.learn.LearnService;
import app.pathwise.web.learner.LearnerService;
import app.pathwise.web.practice.PracticeService;
import app.pathwise.web.questions.QuestionService;
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
    private final CatalogService catalog;
    private final ContentService content;
    private final ContentPackService contentPacks;
    private final LearnService learn;
    private final QuestionService questions;
    private final LearnerService learner;
    private final PracticeService practice;
    private final RecommendationService recommendations;
    private final AssessmentService assessments;
    private final GameService games;
    private final RoomService rooms;
    private final RoomAssessmentService roomAssessments;
    private final RoomInsightService roomInsights;
    private final AssignmentService assignments;
    private final DoubtService doubts;
    private final RewardsService rewards;
    private final TutorService tutor;
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
        this.catalog = new CatalogService(database.tx());
        this.content = new ContentService(database.tx(), events, clock);
        this.contentPacks = new ContentPackService(database.tx(), new ContentPackLoader(AppContext.class.getClassLoader()), events, clock);
        this.questions = new QuestionService(database.tx());
        this.learner = new LearnerService(database.tx(), events, clock);
        this.learn = new LearnService(catalog, content, learner, questions);
        this.practice = new PracticeService(database.tx(), questions, learner, catalog, content, events, clock);
        this.recommendations = new RecommendationService(database.tx(), catalog, content, learner, questions, practice, clock);
        this.assessments = new AssessmentService(database.tx(), questions, learner, catalog, events, clock);
        this.games = new GameService(database.tx(), learner, catalog, events, clock);
        this.rooms = new RoomService(database.tx(), catalog, events, clock);
        this.roomAssessments = new RoomAssessmentService(database.tx(), rooms, catalog, assessments, clock);
        this.roomInsights = new RoomInsightService(database.tx(), rooms, clock);
        this.assignments = new AssignmentService(database.tx(), rooms, catalog, learner, events, clock);
        this.doubts = new DoubtService(database.tx(), catalog, events, clock);
        this.rewards = new RewardsService(database.tx(), clock);
        this.rewards.register(events);
        this.tutor = new TutorService(database.tx(), AiProvider.from(config.ai()), questions, clock);
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
    public CatalogService catalog() { return catalog; }
    public ContentService content() { return content; }
    public ContentPackService contentPacks() { return contentPacks; }
    public LearnService learn() { return learn; }
    public QuestionService questions() { return questions; }
    public LearnerService learner() { return learner; }
    public PracticeService practice() { return practice; }
    public RecommendationService recommendations() { return recommendations; }
    public AssessmentService assessments() { return assessments; }
    public GameService games() { return games; }
    public RoomService rooms() { return rooms; }
    public RoomAssessmentService roomAssessments() { return roomAssessments; }
    public RoomInsightService roomInsights() { return roomInsights; }
    public AssignmentService assignments() { return assignments; }
    public DoubtService doubts() { return doubts; }
    public RewardsService rewards() { return rewards; }
    public TutorService tutor() { return tutor; }
    public RateLimiter loginLimiter() { return loginLimiter; }
    public RateLimiter registerLimiter() { return registerLimiter; }
}
