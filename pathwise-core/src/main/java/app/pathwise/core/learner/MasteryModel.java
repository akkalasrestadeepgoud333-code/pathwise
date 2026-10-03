package app.pathwise.core.learner;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The learner model's per-skill estimate (blueprint §12). A transparent heuristic:
 * evidence-weighted, recency-decayed and smoothed toward a prior, so one lucky answer cannot make a skill
 * "mastered". It is deliberately <em>not</em> knowledge tracing and makes no claims beyond the
 * evidence it is given.
 */
public final class MasteryModel {
    public static final String VERSION = "heuristic-v1";

    private MasteryModel() {}

    /** One observation about one skill. {@code outcome} is credit in [0, 1]. */
    public record Evidence(String source, double outcome, double baseWeight, int difficulty, int hintsUsed,
                           boolean official, Instant occurredAt) {}

    public record Config(double halfLifeDays, double priorWeight, double prior, double confidenceScale,
                         int maxEvidence, double exploringConfidence, double needsSupportBelow,
                         double proficientAt, double proficientConfidence, double masteredAt,
                         double masteredConfidence, int masteredVerificationDays, int masteredMinDifficulty) {

        public static Config defaults() {
            return new Config(21, 2.0, 0.40, 3.0, 60, 0.35, 0.45, 0.70, 0.40, 0.85, 0.60, 60, 3);
        }
    }

    public record Mastery(double estimate, double confidence, MasteryState state, int evidenceCount,
                          int officialCount, Instant lastEvidenceAt, Instant reviewDueAt) {}

    /**
     * @param evidence newest first or in any order; only the most recent {@code maxEvidence} rows count
     */
    public static Mastery estimate(List<Evidence> evidence, Instant now, Config cfg) {
        if (evidence.isEmpty()) {
            return new Mastery(cfg.prior(), 0, MasteryState.NOT_STARTED, 0, 0, null, null);
        }
        List<Evidence> recent = evidence.stream()
                .sorted((a, b) -> b.occurredAt().compareTo(a.occurredAt()))
                .limit(cfg.maxEvidence())
                .toList();
        double weightSum = 0;
        double creditSum = 0;
        int official = 0;
        boolean verified = false;
        Instant last = recent.get(0).occurredAt();
        for (Evidence e : recent) {
            double ageDays = Math.max(0, Duration.between(e.occurredAt(), now).toMinutes() / 1440.0);
            double recency = Math.pow(0.5, ageDays / cfg.halfLifeDays());
            double difficultyFactor = 0.7 + 0.15 * (clamp(e.difficulty(), 1, 5) - 1);
            double w = e.baseWeight() * difficultyFactor * recency;
            weightSum += w;
            creditSum += w * hintAdjusted(e.outcome(), e.hintsUsed());
            if (e.official()) {
                official++;
            }
            boolean rigorous = e.official() || "challenge".equals(e.source());
            if (rigorous && e.outcome() >= 0.999 && e.difficulty() >= cfg.masteredMinDifficulty()
                    && ageDays <= cfg.masteredVerificationDays()) {
                verified = true;
            }
        }
        double estimate = (creditSum + cfg.priorWeight() * cfg.prior()) / (weightSum + cfg.priorWeight());
        double confidence = weightSum / (weightSum + cfg.confidenceScale());
        MasteryState state = state(estimate, confidence, verified, cfg);
        return new Mastery(round(estimate), round(confidence), state, evidence.size(), official, last,
                last.plus(reviewInterval(state)));
    }

    /** Correct-with-hints counts for less: 1 hint → 0.75, 2 → 0.5, 3+ → 0.25 of the credit. */
    static double hintAdjusted(double outcome, int hints) {
        if (hints <= 0) {
            return outcome;
        }
        return outcome * Math.max(0.25, 1 - 0.25 * hints);
    }

    static MasteryState state(double estimate, double confidence, boolean verified, Config cfg) {
        if (confidence < cfg.exploringConfidence()) {
            return MasteryState.EXPLORING;
        }
        if (estimate < cfg.needsSupportBelow()) {
            return MasteryState.NEEDS_SUPPORT;
        }
        if (estimate >= cfg.masteredAt() && confidence >= cfg.masteredConfidence() && verified) {
            return MasteryState.MASTERED;
        }
        if (estimate >= cfg.proficientAt() && confidence >= cfg.proficientConfidence()) {
            return MasteryState.PROFICIENT;
        }
        return MasteryState.DEVELOPING;
    }

    /** Spaced review: stronger skills are revisited less often. Overdue review never lowers the state. */
    static Duration reviewInterval(MasteryState s) {
        return switch (s) {
            case MASTERED -> Duration.ofDays(30);
            case PROFICIENT -> Duration.ofDays(10);
            case DEVELOPING -> Duration.ofDays(3);
            default -> Duration.ofDays(2);
        };
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
