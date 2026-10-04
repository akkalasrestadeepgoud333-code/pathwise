package app.pathwise.web.community;

import app.pathwise.core.community.DoubtRules;
import app.pathwise.core.community.DoubtRules.Scope;
import app.pathwise.core.community.DoubtRules.Viewer;
import app.pathwise.core.rooms.RoomPolicy;
import app.pathwise.core.security.AccessDeniedException;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.NotAuthenticatedException;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.core.security.PlatformPolicy;
import app.pathwise.core.security.RateLimiter;
import app.pathwise.data.Tx;
import app.pathwise.data.catalog.CatalogDao;
import app.pathwise.data.community.DoubtDao;
import app.pathwise.data.community.DoubtDao.Person;
import app.pathwise.data.community.DoubtDao.PostRow;
import app.pathwise.data.community.DoubtDao.ThreadRow;
import app.pathwise.data.community.DoubtDao.ThreadSummary;
import app.pathwise.data.platform.AuditDao;
import app.pathwise.data.rooms.RoomDao;
import app.pathwise.web.catalog.CatalogService;
import app.pathwise.web.content.Markdown;
import app.pathwise.web.content.SafeHtml;
import app.pathwise.web.platform.Json;
import app.pathwise.web.platform.events.EventBus;
import app.pathwise.web.platform.view.TimeLabels;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Doubts: "ask your teacher" (private to a room's teachers) and the public doubt network (anonymous by
 * default, with a per-thread alias). Every view is built from types that carry no user ids, so an anonymous
 * author can't leak through a page or an API response. Moderation and identity reveal are logged.
 */
public final class DoubtService {
    public static final String EVENT_ASKED = "doubt.asked";
    public static final String EVENT_ANSWERED = "doubt.answered";
    public static final String EVENT_RESOLVED = "doubt.resolved";
    public static final String EVENT_VERIFIED = "doubt.answer_verified";
    public static final String EVENT_POST_REMOVED = "doubt.answer_removed";

    private final Tx tx;
    private final CatalogService catalog;
    private final EventBus events;
    private final Clock clock;
    private final DoubtDao dao = new DoubtDao();
    private final RoomDao rooms = new RoomDao();
    private final AuditDao audit = new AuditDao();
    private final CatalogDao catalogDao = new CatalogDao();
    private final RateLimiter askLimiter;
    private final RateLimiter postLimiter;

    public DoubtService(Tx tx, CatalogService catalog, EventBus events, Clock clock) {
        this.tx = tx;
        this.catalog = catalog;
        this.events = events;
        this.clock = clock;
        this.askLimiter = new RateLimiter(10, 3_600_000, clock);   // 10 new doubts per hour
        this.postLimiter = new RateLimiter(30, 600_000, clock);    // 30 answers per 10 minutes
    }

    public static final class DoubtNotFoundException extends RuntimeException {
        public DoubtNotFoundException() {
            super("Not found");
        }
    }

    public static final class DoubtRuleException extends RuntimeException {
        public DoubtRuleException(String message) {
            super(message);
        }
    }

    // ------------------------------------------------------------------ view types (no user ids by design)

    /** How a person appears: {@code kind} is you, teacher, named, verified or anon; {@code alias} is set only for anonymous people. */
    public record Who(String label, String kind, Integer alias) {}

    public record PostView(UUID id, Who who, SafeHtml body, String when, int votes, boolean votedByMe, boolean accepted,
                           boolean verified, boolean mine, String status, boolean canVote, boolean canAccept, boolean canVerify) {}

    public record ThreadView(UUID id, String scope, String scopeLabel, String category, String categoryLabel, String title,
                             SafeHtml body, Who asker, String when, String status, String statusLabel, String roomName,
                             String topicTitle, String practiceHref, List<PostView> posts, boolean canAnswer, boolean mine,
                             boolean canModerate, boolean answerAnonymousByDefault) {}

    public record Summary(UUID id, String scopeLabel, String categoryLabel, String title, String status, String statusLabel,
                          int answers, boolean verified, String when, String context) {}

    public static final Map<String, String> CATEGORY_LABELS;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("concept", "Understanding a concept");
        m.put("problem_solving", "Solving a problem");
        m.put("coding", "Writing code");
        m.put("debugging", "Fixing a bug");
        m.put("where_wrong", "Where did I go wrong?");
        m.put("resources", "Finding resources");
        m.put("teacher_help", "Help from my teacher");
        CATEGORY_LABELS = java.util.Collections.unmodifiableMap(m);
    }

    // ------------------------------------------------------------------ asking

    public record AskOptions(List<Map<String, String>> rooms, List<Map<String, String>> topics, Map<String, String> categories) {}

    public AskOptions askOptions(Actor actor) {
        requireUser(actor);
        return tx.read(c -> {
            List<Map<String, String>> myRooms = new ArrayList<>();
            for (RoomDao.MyRoom r : rooms.roomsOf(c, actor.userId())) {
                if ("student".equals(r.role()) && "active".equals(r.status()) && r.archivedAt() == null) {
                    myRooms.add(Map.of("id", r.id().toString(), "name", r.name()));
                }
            }
            List<Map<String, String>> topics = new ArrayList<>();
            for (var s : catalog.subjects()) {
                catalog.graph(s.key()).ifPresent(g -> g.topics().forEach(t -> topics.add(Map.of("key", t.key(), "title", s.title() + ": " + t.title()))));
            }
            return new AskOptions(myRooms, topics, CATEGORY_LABELS);
        });
    }

    public List<DoubtDao.Match> similar(String text) {
        if (text == null || text.strip().length() < 8) {
            return List.of();
        }
        return tx.read(c -> dao.similar(c, text, 5));
    }

    /**
     * {@code roomId} null = the public network; otherwise a private doubt for that room's teachers (the asker
     * must be an active student there).
     */
    public UUID ask(Actor actor, UUID roomId, String category, String title, String body, String topicKey, boolean showName) {
        requireUser(actor);
        String t = clean(title, 8, 160, "Give your doubt a title of 8 to 160 characters, like a question.");
        String b = clean(body, 10, 8000, "Describe your doubt in at least 10 characters (up to 8000).");
        if (!DoubtRules.CATEGORIES.contains(category)) {
            throw new DoubtRuleException("Choose what kind of help you need.");
        }
        if (!askLimiter.tryAcquire(actor.userId().toString())) {
            throw new DoubtRuleException("You've asked a lot in the last hour. Please wait a little before asking again.");
        }
        UUID topic = topicId(topicKey);
        Instant now = clock.instant();
        return tx.call(c -> {
            if (roomId != null) {
                Optional<RoomDao.MemberRow> m = rooms.membership(c, roomId, actor.userId());
                Optional<RoomDao.RoomRow> room = rooms.find(c, roomId);
                if (m.isEmpty() || !"student".equals(m.get().role()) || !"active".equals(m.get().status())
                        || room.isEmpty() || room.get().archivedAt() != null) {
                    throw new DoubtRuleException("You can ask privately only in rooms you're a student in.");
                }
            }
            UUID id = dao.createThread(c, roomId == null ? "public" : "private_teacher", roomId, actor.userId(), category, t, b, topic,
                    roomId != null || showName, now);
            if (roomId == null) {
                dao.alias(c, id, actor.userId(), DoubtRules::newAlias);
            }
            events.publish(c, EVENT_ASKED, Map.of("threadId", id.toString(), "scope", roomId == null ? "public" : "private_teacher"));
            return id;
        });
    }

    // ------------------------------------------------------------------ reading

    public ThreadView view(Actor actor, UUID threadId) {
        requireUser(actor);
        Instant now = clock.instant();
        return tx.read(c -> {
            ThreadRow t = dao.thread(c, threadId).orElseThrow(DoubtNotFoundException::new);
            Viewer v = viewer(c, t, actor);
            Scope scope = scope(t);
            if (!DoubtRules.canSee(scope, t.status(), v)) {
                throw new DoubtNotFoundException();
            }
            List<PostRow> posts = dao.posts(c, threadId);
            Set<UUID> people = new HashSet<>();
            people.add(t.authorId());
            posts.forEach(p -> people.add(p.authorId()));
            Map<UUID, Person> persons = dao.people(c, people);
            Map<UUID, Integer> aliases = dao.aliases(c, threadId);
            Set<UUID> teachers = t.roomId() == null ? Set.of() : roomTeachers(c, t.roomId());
            Set<UUID> voted = dao.votedBy(c, actor.userId(), threadId);
            Set<UUID> blocked = dao.blockedBy(c, actor.userId());
            boolean author = v.author();
            boolean canVerify = DoubtRules.canVerify(scope, v);
            List<PostView> pv = new ArrayList<>();
            for (PostRow p : posts) {
                boolean visible = "visible".equals(p.status());
                if ((!visible && !v.moderator()) || blocked.contains(p.authorId())) {
                    continue;
                }
                boolean mine = p.authorId().equals(actor.userId());
                pv.add(new PostView(p.id(), who(scope, p.authorId(), p.showName(), actor.userId(), persons, aliases, teachers, t.authorId()),
                        Markdown.render(p.body()), TimeLabels.relative(p.createdAt(), now, TimeLabels.DEFAULT_ZONE), p.votes(),
                        voted.contains(p.id()), p.id().equals(t.acceptedPostId()), p.teacherVerified(), mine, p.status(),
                        !mine && visible, author && !mine && visible && !p.id().equals(t.acceptedPostId()),
                        canVerify && !p.teacherVerified() && visible && !mine));
            }
            String roomName = t.roomId() == null ? null : rooms.find(c, t.roomId()).map(RoomDao.RoomRow::name).orElse(null);
            String[] topic = topic(t.topicId());
            boolean open = "open".equals(t.status()) || "resolved".equals(t.status());
            return new ThreadView(t.id(), t.scope(), scopeLabel(t.scope()), t.category(), CATEGORY_LABELS.getOrDefault(t.category(), t.category()),
                    t.title(), Markdown.render(t.body()), who(scope, t.authorId(), t.showName(), actor.userId(), persons, aliases, teachers, t.authorId()),
                    TimeLabels.relative(t.createdAt(), now, TimeLabels.DEFAULT_ZONE), t.status(), statusLabel(t.status()), roomName,
                    topic == null ? null : topic[1], "resolved".equals(t.status()) && topic != null ? topic[0] : null,
                    pv, open && (scope == Scope.PUBLIC || author || v.roomTeacher()), author, v.moderator(), scope == Scope.PUBLIC);
        });
    }

    /** The label for a person, never their id. Private threads use real names; public ones aliases unless shown. */
    private static Who who(Scope scope, UUID person, boolean showName, UUID viewer, Map<UUID, Person> persons,
                           Map<UUID, Integer> aliases, Set<UUID> teachers, UUID asker) {
        if (person.equals(viewer)) {
            return new Who(DoubtRules.anonymous(scope, showName) ? "You (shown to others as " + DoubtRules.aliasLabel(aliases.getOrDefault(person, 0), person.equals(asker)) + ")" : "You", "you", null);
        }
        Person p = persons.get(person);
        if (teachers.contains(person)) {
            return new Who(p.displayName() + " · Teacher", "teacher", null);
        }
        if (DoubtRules.anonymous(scope, showName)) {
            int alias = aliases.getOrDefault(person, 0);
            return new Who(DoubtRules.aliasLabel(alias, person.equals(asker)), "anon", alias);
        }
        return new Who(p.displayName() + (p.verifiedEducator() ? " · Verified educator" : ""), p.verifiedEducator() ? "verified" : "named", null);
    }

    public record Lists(List<Summary> mine, List<Summary> network, String query) {}

    public Lists lists(Actor actor, String query) {
        requireUser(actor);
        Instant now = clock.instant();
        String q = query == null ? "" : query.strip();
        return tx.read(c -> {
            Map<UUID, String> roomNames = new HashMap<>();
            rooms.roomsOf(c, actor.userId()).forEach(r -> roomNames.put(r.id(), r.name()));
            List<Summary> mine = dao.threadsOf(c, actor.userId()).stream().map(s -> summary(s, now, roomNames)).toList();
            List<Summary> network = dao.publicThreads(c, q.length() > 200 ? q.substring(0, 200) : q, actor.userId(), 50)
                    .stream().map(s -> summary(s, now, roomNames)).toList();
            return new Lists(mine, network, q);
        });
    }

    private static Summary summary(ThreadSummary s, Instant now, Map<UUID, String> roomNames) {
        return new Summary(s.id(), scopeLabel(s.scope()), CATEGORY_LABELS.getOrDefault(s.category(), s.category()), s.title(), s.status(),
                statusLabel(s.status()), s.answers(), s.hasVerified(), TimeLabels.relative(s.createdAt(), now, TimeLabels.DEFAULT_ZONE),
                s.roomId() == null ? null : roomNames.get(s.roomId()));
    }

    /** A room's private doubts, for its teachers. */
    public record RoomDoubt(UUID id, String title, String askedBy, String status, String statusLabel, int answers, String when) {}

    public List<RoomDoubt> roomDoubts(Actor actor, UUID roomId) {
        requireUser(actor);
        Instant now = clock.instant();
        return tx.read(c -> {
            if (!roomTeachers(c, roomId).contains(actor.userId())) {
                throw new AccessDeniedException("room:doubts");
            }
            List<ThreadSummary> list = dao.roomThreads(c, roomId);
            Set<UUID> ids = new HashSet<>();
            list.forEach(s -> ids.add(s.authorId()));
            Map<UUID, Person> persons = ids.isEmpty() ? Map.of() : dao.people(c, ids);
            return list.stream().map(s -> new RoomDoubt(s.id(), s.title(), persons.get(s.authorId()).displayName(), s.status(),
                    statusLabel(s.status()), s.answers(), TimeLabels.relative(s.createdAt(), now, TimeLabels.DEFAULT_ZONE))).toList();
        });
    }

    public int unansweredInRoom(UUID roomId) {
        return tx.read(c -> dao.unansweredInRoom(c, roomId));
    }

    // ------------------------------------------------------------------ taking part

    public void answer(Actor actor, UUID threadId, String body, boolean showName) {
        requireUser(actor);
        String b = clean(body, 2, 8000, "Write an answer (up to 8000 characters).");
        if (!postLimiter.tryAcquire(actor.userId().toString())) {
            throw new DoubtRuleException("You're answering very fast. Please wait a few minutes.");
        }
        Instant now = clock.instant();
        tx.run(c -> {
            ThreadRow t = dao.thread(c, threadId).orElseThrow(DoubtNotFoundException::new);
            Viewer v = viewer(c, t, actor);
            Scope scope = scope(t);
            if (!DoubtRules.canSee(scope, t.status(), v)) {
                throw new DoubtNotFoundException();
            }
            if (!("open".equals(t.status()) || "resolved".equals(t.status()))) {
                throw new DoubtRuleException("This doubt is closed to new answers.");
            }
            if (scope == Scope.PRIVATE_TEACHER && !v.author() && !v.roomTeacher()) {
                throw new DoubtNotFoundException();
            }
            boolean named = scope == Scope.PRIVATE_TEACHER || showName;
            if (scope == Scope.PUBLIC) {
                dao.alias(c, threadId, actor.userId(), DoubtRules::newAlias);
            }
            UUID postId = dao.addPost(c, threadId, actor.userId(), b, named, now);
            events.publish(c, EVENT_ANSWERED, Map.of("threadId", threadId.toString(), "postId", postId.toString()));
        });
    }

    public void vote(Actor actor, UUID postId) {
        requireUser(actor);
        tx.run(c -> {
            PostRow p = dao.post(c, postId).filter(x -> "visible".equals(x.status())).orElseThrow(DoubtNotFoundException::new);
            ThreadRow t = dao.thread(c, p.threadId()).orElseThrow();
            if (!DoubtRules.canSee(scope(t), t.status(), viewer(c, t, actor))) {
                throw new DoubtNotFoundException();
            }
            if (p.authorId().equals(actor.userId())) {
                throw new DoubtRuleException("You can't vote for your own answer.");
            }
            dao.toggleVote(c, postId, actor.userId());
        });
    }

    public void accept(Actor actor, UUID threadId, UUID postId) {
        requireUser(actor);
        tx.run(c -> {
            ThreadRow t = dao.thread(c, threadId).orElseThrow(DoubtNotFoundException::new);
            if (!t.authorId().equals(actor.userId())) {
                throw new AccessDeniedException("doubt:accept");
            }
            PostRow p = dao.post(c, postId).filter(x -> x.threadId().equals(threadId) && "visible".equals(x.status()))
                    .orElseThrow(DoubtNotFoundException::new);
            if (p.authorId().equals(actor.userId())) {
                throw new DoubtRuleException("Accept someone else's answer.");
            }
            dao.accept(c, threadId, postId, clock.instant());
            events.publish(c, EVENT_RESOLVED, Map.of("threadId", threadId.toString(), "postId", postId.toString()));
        });
    }

    public void verify(Actor actor, UUID postId) {
        requireUser(actor);
        tx.run(c -> {
            PostRow p = dao.post(c, postId).filter(x -> "visible".equals(x.status())).orElseThrow(DoubtNotFoundException::new);
            ThreadRow t = dao.thread(c, p.threadId()).orElseThrow();
            Viewer v = viewer(c, t, actor);
            if (!DoubtRules.canSee(scope(t), t.status(), v)) {
                throw new DoubtNotFoundException();
            }
            if (!DoubtRules.canVerify(scope(t), v) || p.authorId().equals(actor.userId())) {
                throw new AccessDeniedException("doubt:verify");
            }
            dao.verify(c, postId, actor.userId());
            dao.moderationAction(c, actor.userId(), null, "post", postId, "verify", "Marked as teacher verified");
            events.publish(c, EVENT_VERIFIED, Map.of("postId", postId.toString()));
        });
    }

    public void report(Actor actor, String kind, UUID targetId, String reason, String details) {
        requireUser(actor);
        if (!Set.of("thread", "post").contains(kind)) {
            throw new DoubtNotFoundException();
        }
        if (!Set.of("spam", "harassment", "inappropriate", "cheating", "misinformation", "self_harm", "other").contains(reason)) {
            throw new DoubtRuleException("Choose a reason for the report.");
        }
        String d = details == null || details.isBlank() ? null : details.strip();
        if (d != null && d.length() > 1000) {
            d = d.substring(0, 1000);
        }
        String detail = d;
        tx.run(c -> {
            UUID threadId = "thread".equals(kind) ? targetId : dao.post(c, targetId).map(PostRow::threadId).orElseThrow(DoubtNotFoundException::new);
            ThreadRow t = dao.thread(c, threadId).orElseThrow(DoubtNotFoundException::new);
            if (!DoubtRules.canSee(scope(t), t.status(), viewer(c, t, actor))) {
                throw new DoubtNotFoundException();
            }
            dao.report(c, actor.userId(), kind, targetId, reason, detail, clock.instant());
        });
    }

    /** Blocks the person behind an alias in a thread: their doubts and answers are hidden from you everywhere. */
    public void block(Actor actor, UUID threadId, int alias) {
        requireUser(actor);
        tx.run(c -> {
            ThreadRow t = dao.thread(c, threadId).orElseThrow(DoubtNotFoundException::new);
            if (!DoubtRules.canSee(scope(t), t.status(), viewer(c, t, actor))) {
                throw new DoubtNotFoundException();
            }
            UUID target = dao.userOfAlias(c, threadId, alias).orElseThrow(DoubtNotFoundException::new);
            if (target.equals(actor.userId())) {
                throw new DoubtRuleException("You can't block yourself.");
            }
            dao.block(c, actor.userId(), target);
        });
    }

    // ------------------------------------------------------------------ moderation

    public record ReportView(UUID id, String kind, String reason, String details, String excerpt, String when, UUID threadId,
                             String targetStatus) {}

    public List<ReportView> queue(Actor actor) {
        PlatformPolicy.require(actor, PlatformAction.MODERATE_COMMUNITY);
        Instant now = clock.instant();
        return tx.read(c -> dao.openReports(c).stream().map(r -> new ReportView(r.id(), r.targetKind(), r.reason(), r.details(),
                r.excerpt(), TimeLabels.relative(r.createdAt(), now, TimeLabels.DEFAULT_ZONE), r.threadId(), r.targetStatus())).toList());
    }

    /** hide, restore, remove or dismiss the reported thread or post; every action needs a reason and is logged. */
    public void moderate(Actor actor, UUID reportId, String action, String reason) {
        PlatformPolicy.require(actor, PlatformAction.MODERATE_COMMUNITY);
        String why = clean(reason, 3, 500, "Give a short reason (it's kept in the moderation log).");
        if (!Set.of("hide", "restore", "remove", "dismiss").contains(action)) {
            throw new DoubtRuleException("Unknown moderation action.");
        }
        Instant now = clock.instant();
        tx.run(c -> {
            DoubtDao.ReportRow r = dao.openReport(c, reportId).orElseThrow(() -> new DoubtRuleException("That report has already been handled."));
            if (!"dismiss".equals(action)) {
                String status = switch (action) {
                    case "hide" -> "hidden";
                    case "remove" -> "removed";
                    default -> "thread".equals(r.targetKind()) ? "open" : "visible";
                };
                if ("thread".equals(r.targetKind())) {
                    dao.setThreadStatus(c, r.targetId(), status);
                } else {
                    dao.setPostStatus(c, r.targetId(), status);
                }
            }
            dao.moderationAction(c, actor.userId(), reportId, r.targetKind(), r.targetId(), action, why);
            if ("post".equals(r.targetKind()) && ("hide".equals(action) || "remove".equals(action))) {
                events.publish(c, EVENT_POST_REMOVED, Map.of("postId", r.targetId().toString()));
            }
            dao.closeReports(c, r.targetKind(), r.targetId(), "dismiss".equals(action) ? "dismissed" : "actioned", actor.userId(), now);
            audit.record(c, actor.userId(), "moderation." + action, r.targetKind(), r.targetId().toString(),
                    Json.write(Map.of("reason", why, "reportId", reportId.toString())), null);
        });
    }

    public record Revealed(String displayName, String email) {}

    /** Shows a moderator who is behind an alias. Requires a reason; written to the moderation log and the audit log. */
    public Revealed reveal(Actor actor, UUID threadId, int alias, String reason) {
        PlatformPolicy.require(actor, PlatformAction.MODERATE_COMMUNITY);
        String why = clean(reason, 10, 500, "Revealing someone's identity needs a reason of at least 10 characters.");
        return tx.call(c -> {
            UUID user = dao.userOfAlias(c, threadId, alias).orElseThrow(DoubtNotFoundException::new);
            Person p = dao.people(c, Set.of(user)).get(user);
            dao.moderationAction(c, actor.userId(), null, "user", user, "reveal_identity", why);
            audit.record(c, actor.userId(), "moderation.reveal_identity", "user", user.toString(),
                    Json.write(Map.of("threadId", threadId.toString(), "alias", alias, "reason", why)), null);
            return new Revealed(p.displayName(), p.email());
        });
    }

    // ------------------------------------------------------------------ knowledge base

    public record ArticleView(UUID id, String title, SafeHtml body, String topicTitle, String updated, UUID sourceThreadId) {}

    public record ArticleSummary(UUID id, String title, String topicTitle, String snippet) {}

    /** Promotes a resolved public doubt (or writes a new article) into the knowledge base. Moderators only. */
    public UUID publishArticle(Actor actor, UUID sourceThreadId, String topicKey, String title, String body) {
        PlatformPolicy.require(actor, PlatformAction.MODERATE_COMMUNITY);
        String t = clean(title, 8, 160, "Give the article a title of 8 to 160 characters.");
        String b = clean(body, 20, 20000, "Write the article (at least 20 characters).");
        UUID topic = topicId(topicKey);
        return tx.call(c -> {
            if (sourceThreadId != null) {
                ThreadRow src = dao.thread(c, sourceThreadId).orElseThrow(DoubtNotFoundException::new);
                if (!"public".equals(src.scope())) {
                    throw new DoubtRuleException("Only public doubts can be promoted; private ones stay between student and teacher.");
                }
            }
            return dao.createArticle(c, topic, t, b, sourceThreadId, actor.userId(), clock.instant());
        });
    }

    public List<ArticleSummary> articles(String query) {
        String q = query == null ? "" : query.strip();
        return tx.read(c -> dao.articles(c, q.length() > 200 ? q.substring(0, 200) : q, 50).stream()
                .map(a -> new ArticleSummary(a.id(), a.title(), topicTitle(a.topicId()), snippet(a.body()))).toList());
    }

    public ArticleView article(UUID id) {
        Instant now = clock.instant();
        return tx.read(c -> dao.article(c, id).map(a -> new ArticleView(a.id(), a.title(), Markdown.render(a.body()), topicTitle(a.topicId()),
                TimeLabels.relative(a.updatedAt(), now, TimeLabels.DEFAULT_ZONE), a.sourceThreadId())).orElseThrow(DoubtNotFoundException::new));
    }

    /** Draft text for promoting a thread: the question and its accepted (or best) answer. */
    public Map<String, String> promotionDraft(Actor actor, UUID threadId) {
        PlatformPolicy.require(actor, PlatformAction.MODERATE_COMMUNITY);
        return tx.read(c -> {
            ThreadRow t = dao.thread(c, threadId).filter(x -> "public".equals(x.scope())).orElseThrow(DoubtNotFoundException::new);
            List<PostRow> posts = dao.posts(c, threadId).stream().filter(p -> "visible".equals(p.status())).toList();
            PostRow best = posts.stream().filter(p -> p.id().equals(t.acceptedPostId())).findFirst()
                    .orElse(posts.stream().max((a, b) -> Integer.compare(a.votes(), b.votes())).orElse(null));
            String body = "**The question:** " + t.body() + "\n\n**The explanation:** " + (best == null ? "" : best.body());
            String topicKey = t.topicId() == null ? "" : topicKey(t.topicId());
            return Map.of("title", t.title(), "body", body, "topic", topicKey == null ? "" : topicKey);
        });
    }

    // ------------------------------------------------------------------ helpers

    private Viewer viewer(Connection c, ThreadRow t, Actor actor) throws SQLException {
        boolean roomTeacher = t.roomId() != null && roomTeachers(c, t.roomId()).contains(actor.userId());
        boolean verified = dao.people(c, Set.of(actor.userId())).getOrDefault(actor.userId(), new Person(actor.userId(), "", "", false)).verifiedEducator();
        return new Viewer(t.authorId().equals(actor.userId()), roomTeacher, PlatformPolicy.can(actor, PlatformAction.MODERATE_COMMUNITY), verified);
    }

    private Set<UUID> roomTeachers(Connection c, UUID roomId) throws SQLException {
        Set<UUID> out = new HashSet<>();
        for (RoomDao.MemberView m : rooms.members(c, roomId)) {
            if ("active".equals(m.status()) && !"student".equals(m.role())) {
                RoomPolicy.Role role = RoomPolicy.Role.fromDb(m.role());
                if (role != RoomPolicy.Role.STUDENT) {
                    out.add(m.userId());
                }
            }
        }
        return out;
    }

    private static Scope scope(ThreadRow t) {
        return "public".equals(t.scope()) ? Scope.PUBLIC : Scope.PRIVATE_TEACHER;
    }

    private UUID topicId(String topicKey) {
        if (topicKey == null || topicKey.isBlank()) {
            return null;
        }
        for (var s : catalog.subjects()) {
            var t = catalog.graph(s.key()).flatMap(g -> g.topic(topicKey));
            if (t.isPresent()) {
                return t.get().id();
            }
        }
        throw new DoubtRuleException("That topic isn't available.");
    }

    /** {href to the topic page, topic title}, or null. */
    private String[] topic(UUID topicId) {
        if (topicId == null) {
            return null;
        }
        for (var s : catalog.subjects()) {
            var t = catalog.graph(s.key()).flatMap(g -> g.topicById(topicId));
            if (t.isPresent()) {
                return new String[] {"/app/topics/" + s.key() + "/" + t.get().key(), t.get().title()};
            }
        }
        return null;
    }

    private String topicTitle(UUID topicId) {
        String[] t = topic(topicId);
        return t == null ? null : t[1];
    }

    private String topicKey(UUID topicId) {
        for (var s : catalog.subjects()) {
            var t = catalog.graph(s.key()).flatMap(g -> g.topicById(topicId));
            if (t.isPresent()) {
                return t.get().key();
            }
        }
        return null;
    }

    private static String snippet(String md) {
        String s = md.replaceAll("[`*_#>]", "").replaceAll("\\s+", " ").strip();
        return s.length() > 180 ? s.substring(0, 177) + "…" : s;
    }

    static String scopeLabel(String scope) {
        return "public".equals(scope) ? "Doubt network" : "Ask your teacher";
    }

    static String statusLabel(String status) {
        return switch (status) {
            case "resolved" -> "Resolved";
            case "hidden" -> "Hidden by a moderator";
            case "removed" -> "Removed";
            default -> "Open";
        };
    }

    private static String clean(String v, int min, int max, String message) {
        String s = v == null ? "" : v.strip();
        if (s.length() < min || s.length() > max) {
            throw new DoubtRuleException(message);
        }
        return s;
    }

    private static void requireUser(Actor actor) {
        if (actor == null || !actor.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }
    }
}
