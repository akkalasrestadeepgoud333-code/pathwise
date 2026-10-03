package app.pathwise.core.learner;

import app.pathwise.core.learner.MasteryModel.Config;
import app.pathwise.core.learner.MasteryModel.Evidence;
import app.pathwise.core.learner.MasteryModel.Mastery;
import app.pathwise.core.questions.Grader;
import app.pathwise.core.questions.Grader.AnswerKey;
import app.pathwise.core.questions.Grader.Type;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LearnerCoreTest {
    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");
    private static final Config CFG = Config.defaults();

    private static List<Evidence> practice(int correct, int wrong, int difficulty, Duration age) {
        List<Evidence> out = new ArrayList<>();
        for (int i = 0; i < correct; i++) {
            out.add(new Evidence("practice", 1, 0.6, difficulty, 0, false, NOW.minus(age)));
        }
        for (int i = 0; i < wrong; i++) {
            out.add(new Evidence("practice", 0, 0.6, difficulty, 0, false, NOW.minus(age)));
        }
        return out;
    }

    // ------------------------------------------------------------------ mastery model (golden values)

    @Test
    void noEvidenceMeansNotStarted() {
        Mastery m = MasteryModel.estimate(List.of(), NOW, CFG);
        assertThat(m.state()).isEqualTo(MasteryState.NOT_STARTED);
        assertThat(m.confidence()).isZero();
    }

    @Test
    void oneLuckyAnswerIsOnlyExploring() {
        Mastery m = MasteryModel.estimate(practice(1, 0, 2, Duration.ZERO), NOW, CFG);
        assertThat(m.state()).isEqualTo(MasteryState.EXPLORING);
        assertThat(m.estimate()).isLessThan(0.6);
    }

    @Test
    void fiveCorrectIsProficientButNotMastered() {
        Mastery m = MasteryModel.estimate(practice(5, 0, 2, Duration.ZERO), NOW, CFG);
        assertThat(m.estimate()).isCloseTo(0.736, within(0.002));
        assertThat(m.confidence()).isCloseTo(0.459, within(0.002));
        assertThat(m.state()).isEqualTo(MasteryState.PROFICIENT);
        assertThat(m.reviewDueAt()).isEqualTo(NOW.plus(Duration.ofDays(10)));
    }

    @Test
    void mostlyWrongNeedsSupport() {
        Mastery m = MasteryModel.estimate(practice(2, 3, 2, Duration.ZERO), NOW, CFG);
        assertThat(m.state()).isEqualTo(MasteryState.NEEDS_SUPPORT);
    }

    @Test
    void masteryRequiresRecentRigorousEvidence() {
        List<Evidence> lots = practice(14, 0, 3, Duration.ZERO);
        assertThat(MasteryModel.estimate(lots, NOW, CFG).state()).as("practice alone").isEqualTo(MasteryState.PROFICIENT);
        List<Evidence> withChallenge = new ArrayList<>(lots);
        withChallenge.add(new Evidence("challenge", 1, 0.8, 4, 0, false, NOW));
        assertThat(MasteryModel.estimate(withChallenge, NOW, CFG).state()).isEqualTo(MasteryState.MASTERED);
        List<Evidence> oldChallenge = new ArrayList<>(lots);
        oldChallenge.add(new Evidence("challenge", 1, 0.8, 4, 0, false, NOW.minus(Duration.ofDays(90))));
        assertThat(MasteryModel.estimate(oldChallenge, NOW, CFG).state()).isEqualTo(MasteryState.PROFICIENT);
    }

    @Test
    void oldEvidenceCountsLessThanRecentEvidence() {
        List<Evidence> mixed = new ArrayList<>(practice(5, 0, 2, Duration.ofDays(60))); // old successes
        mixed.addAll(practice(0, 4, 2, Duration.ZERO));                                // recent failures
        Mastery m = MasteryModel.estimate(mixed, NOW, CFG);
        assertThat(m.estimate()).isLessThan(0.4);
    }

    @Test
    void hintsReduceCredit() {
        assertThat(MasteryModel.hintAdjusted(1, 0)).isEqualTo(1);
        assertThat(MasteryModel.hintAdjusted(1, 1)).isEqualTo(0.75);
        assertThat(MasteryModel.hintAdjusted(1, 5)).isEqualTo(0.25);
        double noHints = MasteryModel.estimate(practice(5, 0, 2, Duration.ZERO), NOW, CFG).estimate();
        List<Evidence> hinted = practice(5, 0, 2, Duration.ZERO).stream()
                .map(e -> new Evidence(e.source(), e.outcome(), e.baseWeight(), e.difficulty(), 2, false, e.occurredAt())).toList();
        assertThat(MasteryModel.estimate(hinted, NOW, CFG).estimate()).isLessThan(noHints);
    }

    @Test
    void onlyTheMostRecentSixtyRowsCount() {
        List<Evidence> many = new ArrayList<>(practice(0, 100, 2, Duration.ofDays(1)));
        many.addAll(practice(60, 0, 2, Duration.ZERO));
        assertThat(MasteryModel.estimate(many, NOW, CFG).state()).isEqualTo(MasteryState.PROFICIENT);
    }

    // ------------------------------------------------------------------ grader

    private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID(), C = UUID.randomUUID(), D = UUID.randomUUID();

    @Test
    void singleChoice() {
        AnswerKey key = new AnswerKey(Type.SINGLE_CHOICE, Set.of(B), Set.of(A, B, C), null, null, null);
        assertThat(Grader.grade(key, Set.of(B), null).correct()).isTrue();
        assertThat(Grader.grade(key, Set.of(A), null).partial()).isZero();
        assertThat(Grader.grade(key, Set.of(A, B), null).partial()).as("two picks").isZero();
        assertThat(Grader.grade(key, Set.of(), null).partial()).isZero();
    }

    @Test
    void multiChoiceGivesPartialCreditAndPenalisesWrongPicks() {
        AnswerKey key = new AnswerKey(Type.MULTI_CHOICE, Set.of(A, B), Set.of(A, B, C, D), null, null, null);
        assertThat(Grader.grade(key, Set.of(A, B), null).correct()).isTrue();
        assertThat(Grader.grade(key, Set.of(A), null).partial()).isEqualTo(0.5);
        assertThat(Grader.grade(key, Set.of(A, B, C, D), null).partial()).as("select-all earns nothing").isZero();
        assertThat(Grader.grade(key, Set.of(A, C), null).partial()).isZero();
        assertThat(Grader.grade(key, Set.of(UUID.randomUUID()), null).partial()).as("foreign option").isZero();
    }

    @Test
    void numericWithTolerance() {
        AnswerKey key = new AnswerKey(Type.NUMERIC, Set.of(), Set.of(), 3.0, 0.01, null);
        assertThat(Grader.grade(key, null, " 3 ").correct()).isTrue();
        assertThat(Grader.grade(key, null, "3.005").correct()).isTrue();
        assertThat(Grader.grade(key, null, "3.1").correct()).isFalse();
        assertThat(Grader.grade(key, null, "three").correct()).isFalse();
        assertThat(Grader.grade(key, null, "NaN").correct()).isFalse();
    }

    @Test
    void shortTextIsNormalised() {
        AnswerKey key = new AnswerKey(Type.SHORT_TEXT, Set.of(), Set.of(), null, null, List.of("8, 4, 2, 6", "Queue"));
        assertThat(Grader.grade(key, null, "8,4,2,6").correct()).isTrue();
        assertThat(Grader.grade(key, null, "  8 ,  4, 2 ,6 ").correct()).isTrue();
        assertThat(Grader.grade(key, null, "QUEUE").correct()).isTrue();
        assertThat(Grader.grade(key, null, "8, 2, 4, 6").correct()).isFalse();
        assertThat(Grader.grade(key, null, "").correct()).isFalse();
    }
}
