package app.pathwise.web.community;

import app.pathwise.core.community.LiveSessionRules;
import app.pathwise.core.security.AccessDeniedException;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.core.security.PlatformPolicy;
import app.pathwise.data.Tx;
import app.pathwise.data.community.LiveSessionDao;
import app.pathwise.data.community.LiveSessionDao.ParticipantRow;
import app.pathwise.data.community.LiveSessionDao.QuestionRow;
import app.pathwise.data.community.LiveSessionDao.SessionRow;
import app.pathwise.data.platform.AuditDao;
import app.pathwise.web.platform.Json;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Live doubt sessions. Participants appear only as "Participant N", to everyone including the host; views
 * carry no user ids or names except the host's. Text only.
 */
public final class LiveSessionService {
    private final Tx tx;
    private final Clock clock;
    private final LiveSessionDao dao = new LiveSessionDao();
    private final AuditDao audit = new AuditDao();

    public LiveSessionService(Tx tx, Clock clock) {
        this.tx = tx;
        this.clock = clock;
    }

    public static final class SessionNotFoundException extends RuntimeException {}

    public static final class LiveRuleException extends RuntimeException {
        public LiveRuleException(String message) {
            super(message);
        }
    }

    public record Card(UUID id, String title, String hostName, String status, String statusLabel, int participants, boolean locked) {}

    public record Home(boolean canHost, List<Card> sessions) {}

    public record Question(UUID id, String who, String body, String status, int votes, boolean myVote, boolean mine) {}

    public record View(UUID id, String title, String hostName, String status, String statusLabel, boolean locked, boolean host,
                       boolean joined, boolean removed, String myAlias, boolean handRaised, boolean canTakePart, boolean canJoin,
                       int participants, List<String> hands, List<Integer> activeAliases, List<Question> questions) {}

    private static String label(String status) {
        return switch (status) {
            case "live" -> "Live now";
            case "scheduled" -> "Not started";
            default -> "Ended";
        };
    }

    private static Card card(SessionRow s) {
        return new Card(s.id(), s.title(), s.hostName(), s.status(), label(s.status()), s.participants(), s.locked());
    }

    private static void requireUser(Actor actor) {
        PlatformPolicy.require(actor, PlatformAction.USE_STUDENT_AREA);
    }

    public Home home(Actor actor) {
        requireUser(actor);
        return new Home(PlatformPolicy.can(actor, PlatformAction.HOST_LIVE_SESSION), tx.read(dao::recent).stream().map(LiveSessionService::card).toList());
    }

    public UUID create(Actor actor, String title) {
        PlatformPolicy.require(actor, PlatformAction.HOST_LIVE_SESSION);
        String t = title == null ? "" : title.strip().replaceAll("\\s+", " ");
        if (t.length() < 3 || t.length() > 120) {
            throw new LiveRuleException("Give the session a title (3 to 120 characters).");
        }
        return tx.call(c -> {
            UUID id = dao.create(c, actor.userId(), t);
            audit.record(c, actor.userId(), "live.session_created", "live_session", id.toString(), null, null);
            return id;
        });
    }

    public View view(Actor actor, UUID id) {
        requireUser(actor);
        return tx.read(c -> {
            SessionRow s = dao.find(c, id).orElseThrow(SessionNotFoundException::new);
            boolean host = s.hostId().equals(actor.userId());
            Optional<ParticipantRow> me = dao.participant(c, id, actor.userId());
            String myStatus = me.map(ParticipantRow::status).orElse(null);
            boolean removed = "removed".equals(myStatus);
            List<ParticipantRow> people = dao.participants(c, id);
            Map<UUID, Integer> aliasOf = people.stream().collect(Collectors.toMap(ParticipantRow::userId, ParticipantRow::aliasNumber));
            List<Question> questions = removed ? List.of() : LiveSessionRules.ordered(dao.questions(c, id, actor.userId()),
                    q -> new LiveSessionRules.Entry(q.status(), q.votes(), q.createdAt())).stream()
                    .filter(q -> !"dismissed".equals(q.status()) || host)
                    .map(q -> new Question(q.id(), aliasOf.containsKey(q.userId()) ? LiveSessionRules.aliasLabel(aliasOf.get(q.userId())) : "Participant",
                            q.body(), q.status(), q.votes(), q.myVote(), q.userId().equals(actor.userId())))
                    .toList();
            List<String> hands = people.stream().filter(p -> "active".equals(p.status()) && p.handRaisedAt() != null)
                    .sorted(Comparator.comparing(ParticipantRow::handRaisedAt))
                    .map(p -> LiveSessionRules.aliasLabel(p.aliasNumber())).toList();
            List<Integer> active = host ? people.stream().filter(p -> "active".equals(p.status())).map(ParticipantRow::aliasNumber).toList() : List.of();
            return new View(s.id(), s.title(), s.hostName(), s.status(), label(s.status()), s.locked(), host,
                    "active".equals(myStatus), removed, me.map(p -> LiveSessionRules.aliasLabel(p.aliasNumber())).orElse(null),
                    me.map(p -> p.handRaisedAt() != null).orElse(false), !host && LiveSessionRules.canTakePart(s.status(), myStatus),
                    !host && !"active".equals(myStatus) && LiveSessionRules.canJoin(s.status(), s.locked(), myStatus),
                    s.participants(), hands, active, questions);
        });
    }

    // ---- participants --------------------------------------------------------------------------

    public void join(Actor actor, UUID id) {
        requireUser(actor);
        tx.run(c -> {
            SessionRow s = dao.lock(c, id).orElseThrow(SessionNotFoundException::new);
            if (s.hostId().equals(actor.userId())) {
                return; // the host is always in the room
            }
            Optional<ParticipantRow> me = dao.participant(c, id, actor.userId());
            if (!LiveSessionRules.canJoin(s.status(), s.locked(), me.map(ParticipantRow::status).orElse(null))) {
                throw new LiveRuleException(me.filter(p -> "removed".equals(p.status())).isPresent() ? "The host removed you from this session."
                        : "ended".equals(s.status()) ? "This session has ended." : "The host has locked this session to new people.");
            }
            if (me.isEmpty()) {
                dao.join(c, id, actor.userId());
            } else {
                dao.setParticipantStatus(c, id, actor.userId(), "active");
            }
        });
    }

    public void leave(Actor actor, UUID id) {
        requireUser(actor);
        tx.run(c -> {
            dao.lock(c, id).orElseThrow(SessionNotFoundException::new);
            if (dao.participant(c, id, actor.userId()).filter(p -> "active".equals(p.status())).isPresent()) {
                dao.setParticipantStatus(c, id, actor.userId(), "left");
            }
        });
    }

    private SessionRow participating(Connection c, Actor actor, UUID id) throws SQLException {
        SessionRow s = dao.find(c, id).orElseThrow(SessionNotFoundException::new);
        String myStatus = dao.participant(c, id, actor.userId()).map(ParticipantRow::status).orElse(null);
        if (!LiveSessionRules.canTakePart(s.status(), myStatus)) {
            throw new LiveRuleException("live".equals(s.status()) ? "Join the session first." : "The session isn't live.");
        }
        return s;
    }

    public void hand(Actor actor, UUID id, boolean raise) {
        requireUser(actor);
        tx.run(c -> {
            participating(c, actor, id);
            dao.setHand(c, id, actor.userId(), raise ? clock.instant() : null);
        });
    }

    public void ask(Actor actor, UUID id, String body) {
        requireUser(actor);
        String b = body == null ? "" : body.strip();
        if (b.length() < 3 || b.length() > 500) {
            throw new LiveRuleException("Questions are 3 to 500 characters.");
        }
        tx.run(c -> {
            SessionRow s = participating(c, actor, id);
            int queued = dao.queuedBy(c, id, actor.userId());
            if (!LiveSessionRules.canAsk(s.status(), "active", queued)) {
                throw new LiveRuleException("You already have " + LiveSessionRules.MAX_QUEUED_PER_PERSON
                        + " questions waiting. Let the host get to them first.");
            }
            dao.ask(c, id, actor.userId(), b);
        });
    }

    public void vote(Actor actor, UUID id, UUID questionId) {
        requireUser(actor);
        tx.run(c -> {
            participating(c, actor, id);
            QuestionRow q = dao.question(c, id, questionId).orElseThrow(SessionNotFoundException::new);
            if (q.userId().equals(actor.userId())) {
                throw new LiveRuleException("You can't upvote your own question.");
            }
            if (!"queued".equals(q.status())) {
                throw new LiveRuleException("Only waiting questions can be upvoted.");
            }
            dao.toggleVote(c, questionId, actor.userId());
        });
    }

    // ---- host ----------------------------------------------------------------------------------

    private SessionRow hosted(Connection c, Actor actor, UUID id) throws SQLException {
        SessionRow s = dao.lock(c, id).orElseThrow(SessionNotFoundException::new);
        if (!s.hostId().equals(actor.userId())) {
            throw new AccessDeniedException("live session host");
        }
        return s;
    }

    public void move(Actor actor, UUID id, String to) {
        requireUser(actor);
        tx.run(c -> {
            SessionRow s = hosted(c, actor, id);
            if (!LiveSessionRules.canMove(s.status(), to)) {
                throw new LiveRuleException("That session can't move from " + label(s.status()).toLowerCase() + " to " + label(to).toLowerCase() + ".");
            }
            dao.setStatus(c, id, to, clock.instant());
            audit.record(c, actor.userId(), "live.session_" + to, "live_session", id.toString(), null, null);
        });
    }

    public void setLocked(Actor actor, UUID id, boolean locked) {
        requireUser(actor);
        tx.run(c -> {
            hosted(c, actor, id);
            dao.setLocked(c, id, locked);
        });
    }

    public void setQuestion(Actor actor, UUID id, UUID questionId, String to) {
        requireUser(actor);
        tx.run(c -> {
            SessionRow s = hosted(c, actor, id);
            if (!"live".equals(s.status())) {
                throw new LiveRuleException("Start the session first.");
            }
            QuestionRow q = dao.question(c, id, questionId).orElseThrow(SessionNotFoundException::new);
            if (!LiveSessionRules.canMoveQuestion(q.status(), to)) {
                throw new LiveRuleException("That question can't move to " + to + ".");
            }
            if ("answering".equals(to)) {
                dao.requeueAnswering(c, id);
            }
            dao.setQuestionStatus(c, questionId, to, clock.instant());
        });
    }

    /** Removes a participant by alias. They can't rejoin this session. */
    public void remove(Actor actor, UUID id, int alias) {
        requireUser(actor);
        tx.run(c -> {
            hosted(c, actor, id);
            ParticipantRow p = dao.participantByAlias(c, id, alias).orElseThrow(() -> new LiveRuleException("No one has that alias here."));
            dao.setParticipantStatus(c, id, p.userId(), "removed");
            audit.record(c, actor.userId(), "live.participant_removed", "live_session", id.toString(),
                    Json.write(Map.of("alias", alias)), null);
        });
    }
}
