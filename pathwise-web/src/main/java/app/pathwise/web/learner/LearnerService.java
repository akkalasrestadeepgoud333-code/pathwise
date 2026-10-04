package app.pathwise.web.learner;

import app.pathwise.core.learner.MasteryModel;
import app.pathwise.core.learner.MasteryState;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.NotAuthenticatedException;
import app.pathwise.data.Tx;
import app.pathwise.data.learner.EvidenceDao;
import app.pathwise.data.learner.EvidenceDao.EvidenceRow;
import app.pathwise.data.learner.EvidenceDao.MasteryRow;
import app.pathwise.web.platform.events.EventBus;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Learning evidence and the learner model. Evidence rows are appended in the caller's transaction and
 * the affected skills' mastery is recomputed in that same transaction, so the model never lags behind
 * the evidence.
 */
public final class LearnerService {
    public static final String EVENT_MASTERY_CHANGED = "learner.mastery_changed";
    private static final int MODEL_WINDOW = 60;

    /** Base weights by source (blueprint §12). Practice repeats within 24 h count much less. */
    public static final double W_PRACTICE = 0.6;
    public static final double W_REPEAT = 0.2;
    public static final double W_CHALLENGE = 0.8;
    public static final double W_MODULE_CHECK = 0.5;

    private final Tx tx;
    private final EventBus events;
    private final Clock clock;
    private final EvidenceDao dao = new EvidenceDao();
    private final MasteryModel.Config config = MasteryModel.Config.defaults();

    public LearnerService(Tx tx, EventBus events, Clock clock) {
        this.tx = tx;
        this.events = events;
        this.clock = clock;
    }

    public record Change(UUID skillId, MasteryState before, MasteryState after, double estimate, double confidence,
                         int evidenceCount) {
        public boolean improved() {
            return after.ordinal() > before.ordinal();
        }
    }

    /** Appends one evidence row. Call {@link #recompute} once for all affected skills afterwards. */
    public void addEvidence(Connection c, UUID userId, UUID skillId, String source, Long itemId, double outcome,
                            double baseWeight, int difficulty, int hints, Instant at) throws SQLException {
        dao.insert(c, userId, skillId, source, itemId, outcome, baseWeight, difficulty, hints, false, at);
    }

    /** Recomputes mastery for the given skills from their evidence and stores it. Returns what changed. */
    public List<Change> recompute(Connection c, UUID userId, Collection<UUID> skillIds, Instant now) throws SQLException {
        Map<UUID, MasteryRow> before = dao.mastery(c, userId);
        List<Change> changes = new ArrayList<>();
        for (UUID skill : skillIds) {
            List<MasteryModel.Evidence> evidence = toModel(dao.recentForSkill(c, userId, skill, MODEL_WINDOW));
            int total = dao.countForSkill(c, userId, skill);
            MasteryModel.Mastery m = MasteryModel.estimate(evidence, now, config);
            int official = (int) evidence.stream().filter(MasteryModel.Evidence::official).count();
            dao.upsertMastery(c, userId, skill, m.estimate(), m.confidence(), m.state().dbValue(), total, official,
                    m.lastEvidenceAt(), m.reviewDueAt(), MasteryModel.VERSION, now);
            MasteryState was = before.containsKey(skill) ? MasteryState.fromDb(before.get(skill).state()) : MasteryState.NOT_STARTED;
            changes.add(new Change(skill, was, m.state(), m.estimate(), m.confidence(), total));
            if (was != m.state()) {
                events.publish(c, EVENT_MASTERY_CHANGED, Map.of("userId", userId.toString(), "skillId", skill.toString(),
                        "from", was.dbValue(), "to", m.state().dbValue()));
            }
        }
        return changes;
    }

    /** The model as it stood just before {@code at} (used for "before → after" in practice summaries). */
    public MasteryState stateBefore(Actor actor, UUID skillId, Instant at) {
        requireUser(actor);
        List<EvidenceRow> rows = tx.read(c -> dao.recentForSkill(c, actor.userId(), skillId, 500));
        List<MasteryModel.Evidence> earlier = toModel(rows.stream().filter(r -> r.occurredAt().isBefore(at)).toList());
        return MasteryModel.estimate(earlier, at, config).state();
    }

    public Map<UUID, MasteryRow> mastery(Actor actor) {
        requireUser(actor);
        return tx.read(c -> dao.mastery(c, actor.userId()));
    }

    public Map<UUID, EvidenceDao.RecentStats> recentStats(Actor actor, Instant since) {
        requireUser(actor);
        return tx.read(c -> dao.recentStats(c, actor.userId(), since));
    }

    public Map<String, Integer> evidenceBySource(Actor actor, UUID skillId) {
        requireUser(actor);
        return tx.read(c -> dao.countsBySource(c, actor.userId(), skillId));
    }

    public Instant latestEvidenceAt(Actor actor) {
        requireUser(actor);
        return tx.read(c -> dao.latestEvidenceAt(c, actor.userId()));
    }

    /** Rebuilds a user's whole model from evidence (CLI {@code engine-rebuild}). */
    public int rebuild(UUID userId) {
        Instant now = clock.instant();
        return tx.call(c -> {
            List<UUID> skills = new ArrayList<>(dao.mastery(c, userId).keySet());
            return recompute(c, userId, skills, now).size();
        });
    }

    private static List<MasteryModel.Evidence> toModel(List<EvidenceRow> rows) {
        return rows.stream().map(r -> new MasteryModel.Evidence(r.sourceKind(), r.outcome(), r.baseWeight(), r.difficulty(),
                r.hintsUsed(), r.official(), r.occurredAt())).toList();
    }

    private static void requireUser(Actor actor) {
        if (actor == null || !actor.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }
    }
}
