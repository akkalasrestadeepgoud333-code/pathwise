package app.pathwise.core.engine;

import app.pathwise.core.learner.MasteryState;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Everything the recommender may look at, gathered by the server in one pass. Identifiers are opaque
 * strings (database ids), so the engine works across subjects and stays independent of storage.
 */
public record Snapshot(
        Instant now,
        List<Topic> topics,
        List<Skill> skills,
        List<Prerequisite> prerequisites,
        List<ModuleInfo> modules,
        List<Goal> goals,
        List<Suppression> suppressions,
        List<String> recentActivityKinds) {

    public record Topic(String id, String title, String subjectId, int position, boolean optional, boolean advanced) {}

    /**
     * @param recentAttempts answers on this skill in the last 7 days
     * @param recentErrors   incorrect answers among them
     * @param practiceQuestions published questions available for practice
     * @param challengeQuestions those at difficulty 4 or above
     */
    public record Skill(String id, String title, String topicId, boolean core, MasteryState state, double estimate,
                        double confidence, int evidenceCount, Instant reviewDueAt, int recentAttempts, int recentErrors,
                        int practiceQuestions, int challengeQuestions) {}

    public record Prerequisite(String topicId, String prerequisiteTopicId, boolean required) {}

    public record ModuleInfo(String id, String title, String topicId, int position, boolean started, boolean completed,
                         int percent, Instant lastSeen) {}

    public record Goal(String topicId, String title, LocalDate targetDate) {}

    /** Targets the student asked not to see for now ("not now", "not relevant"). */
    public record Suppression(Set<String> targetIds, Instant until) {}
}
