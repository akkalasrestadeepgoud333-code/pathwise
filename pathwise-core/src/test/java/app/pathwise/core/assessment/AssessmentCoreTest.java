package app.pathwise.core.assessment;

import app.pathwise.core.assessment.AssessmentRules.FeedbackPolicy;
import app.pathwise.core.assessment.AssessmentRules.Settings;
import app.pathwise.core.assessment.AssessmentRules.StartDecision;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentCoreTest {
    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");

    @Test
    void startRules() {
        Settings open = new Settings(600, 2, null, null, FeedbackPolicy.IMMEDIATE);
        assertThat(AssessmentRules.canStart(open, 0, false, NOW)).isEqualTo(StartDecision.OK);
        assertThat(AssessmentRules.canStart(open, 2, false, NOW)).isEqualTo(StartDecision.NO_ATTEMPTS_LEFT);
        assertThat(AssessmentRules.canStart(open, 0, true, NOW)).isEqualTo(StartDecision.ALREADY_IN_PROGRESS);
        Settings future = new Settings(null, 1, NOW.plusSeconds(60), null, FeedbackPolicy.IMMEDIATE);
        assertThat(AssessmentRules.canStart(future, 0, false, NOW)).isEqualTo(StartDecision.NOT_YET_AVAILABLE);
        Settings closed = new Settings(null, 1, null, NOW.minusSeconds(1), FeedbackPolicy.IMMEDIATE);
        assertThat(AssessmentRules.canStart(closed, 0, false, NOW)).isEqualTo(StartDecision.CLOSED);
    }

    @Test
    void deadlineIsTheEarlierOfTimeLimitAndDueDate() {
        assertThat(AssessmentRules.deadline(new Settings(600, 1, null, null, FeedbackPolicy.IMMEDIATE), NOW))
                .isEqualTo(NOW.plusSeconds(600));
        assertThat(AssessmentRules.deadline(new Settings(600, 1, null, NOW.plusSeconds(120), FeedbackPolicy.IMMEDIATE), NOW))
                .isEqualTo(NOW.plusSeconds(120));
        assertThat(AssessmentRules.deadline(new Settings(null, 1, null, null, FeedbackPolicy.IMMEDIATE), NOW)).isNull();
        Instant deadline = NOW.plusSeconds(600);
        assertThat(AssessmentRules.acceptsAnswers(deadline, deadline.plusSeconds(29))).isTrue();
        assertThat(AssessmentRules.acceptsAnswers(deadline, deadline.plusSeconds(31))).isFalse();
    }

    @Test
    void feedbackRelease() {
        assertThat(AssessmentRules.feedbackVisible(new Settings(null, 1, null, null, FeedbackPolicy.IMMEDIATE), NOW)).isTrue();
        Settings afterDue = new Settings(null, 1, null, NOW.plus(Duration.ofDays(1)), FeedbackPolicy.AFTER_DUE);
        assertThat(AssessmentRules.feedbackVisible(afterDue, NOW)).isFalse();
        assertThat(AssessmentRules.feedbackVisible(afterDue, NOW.plus(Duration.ofDays(2)))).isTrue();
        assertThat(AssessmentRules.feedbackVisible(new Settings(null, 1, null, null, FeedbackPolicy.MANUAL_RELEASE), NOW)).isFalse();
        assertThat(AssessmentRules.isLate(afterDue, NOW.plus(Duration.ofDays(1)).plusSeconds(60))).isTrue();
    }

    @Test
    void itemAnalysisComputesDifficultyDiscriminationAndDistractors() {
        // 10 attempts. Item "good": the top scorers get it right and the bottom ones don't.
        // Item "bad": the reverse (a suspicious key). Item "easy": everyone right.
        List<ItemAnalysis.Attempt> attempts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            boolean strong = i < 5;
            attempts.add(new ItemAnalysis.Attempt(strong ? 9 - i : 2 - i * 0.1,
                    Map.of("good", strong ? 1.0 : 0.0, "bad", strong ? 0.0 : 1.0, "easy", 1.0),
                    Map.of("good", List.of(strong ? "A" : "B"))));
        }
        var stats = ItemAnalysis.analyse(List.of("good", "bad", "easy", "unused"), attempts);
        var good = stats.get(0);
        assertThat(good.difficulty()).isEqualTo(0.5);
        assertThat(good.discrimination()).isEqualTo(1.0);
        assertThat(good.optionCounts()).containsEntry("A", 5).containsEntry("B", 5);
        assertThat(stats.get(1).discrimination()).isEqualTo(-1.0);
        assertThat(stats.get(1).flag()).contains("check the answer key");
        assertThat(stats.get(2).flag()).isEqualTo("Very easy");
        assertThat(stats.get(3).flag()).isEqualTo("No responses yet");
        assertThat(ItemAnalysis.analyse(List.of("good"), attempts.subList(0, 3)).get(0).discrimination())
                .as("too few attempts").isNull();
    }
}
