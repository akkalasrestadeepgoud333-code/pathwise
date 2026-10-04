package app.pathwise.core.assessment;

import java.time.Duration;
import java.time.Instant;

/** Pure rules for official assessments: availability, attempts, deadlines, lateness and feedback release. */
public final class AssessmentRules {
    private AssessmentRules() {}

    public enum FeedbackPolicy { IMMEDIATE, AFTER_DUE, MANUAL_RELEASE }

    /** {@code releasedAt}: when a teacher released results (only used by {@link FeedbackPolicy#MANUAL_RELEASE}). */
    public record Settings(Integer timeLimitSec, int maxAttempts, Instant availableFrom, Instant dueAt,
                           FeedbackPolicy feedback, Instant releasedAt) {
        public Settings(Integer timeLimitSec, int maxAttempts, Instant availableFrom, Instant dueAt, FeedbackPolicy feedback) {
            this(timeLimitSec, maxAttempts, availableFrom, dueAt, feedback, null);
        }
    }

    public enum StartDecision { OK, NOT_YET_AVAILABLE, CLOSED, NO_ATTEMPTS_LEFT, ALREADY_IN_PROGRESS }

    /**
     * Whether a new attempt may start now. After the due date nothing new can start; work already in
     * progress is handled by its own deadline.
     */
    public static StartDecision canStart(Settings s, int attemptsUsed, boolean inProgress, Instant now) {
        if (inProgress) {
            return StartDecision.ALREADY_IN_PROGRESS;
        }
        if (s.availableFrom() != null && now.isBefore(s.availableFrom())) {
            return StartDecision.NOT_YET_AVAILABLE;
        }
        if (s.dueAt() != null && !now.isBefore(s.dueAt())) {
            return StartDecision.CLOSED;
        }
        if (attemptsUsed >= s.maxAttempts()) {
            return StartDecision.NO_ATTEMPTS_LEFT;
        }
        return StartDecision.OK;
    }

    /**
     * The attempt's hard deadline: start + time limit, but never after the due date. Null means no
     * time limit and no due date.
     */
    public static Instant deadline(Settings s, Instant startedAt) {
        Instant byLimit = s.timeLimitSec() == null ? null : startedAt.plus(Duration.ofSeconds(s.timeLimitSec()));
        if (byLimit == null) {
            return s.dueAt();
        }
        return s.dueAt() == null || byLimit.isBefore(s.dueAt()) ? byLimit : s.dueAt();
    }

    /** A small grace period absorbs network delay on the final submit; answers saved later are refused. */
    public static final Duration GRACE = Duration.ofSeconds(30);

    public static boolean acceptsAnswers(Instant deadline, Instant now) {
        return deadline == null || !now.isAfter(deadline.plus(GRACE));
    }

    public static boolean isLate(Settings s, Instant submittedAt) {
        return s.dueAt() != null && submittedAt.isAfter(s.dueAt().plus(GRACE));
    }

    /** Whether the student may see per-question feedback (scores are always shown after grading). */
    public static boolean feedbackVisible(Settings s, Instant now) {
        return switch (s.feedback()) {
            case IMMEDIATE -> true;
            case AFTER_DUE -> s.dueAt() != null && !now.isBefore(s.dueAt());
            case MANUAL_RELEASE -> s.releasedAt() != null && !now.isBefore(s.releasedAt());
        };
    }
}
