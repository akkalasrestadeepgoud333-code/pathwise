package app.pathwise.core.rewards;

import app.pathwise.core.rewards.Badges.Facts;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RewardsCoreTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    @Test
    void levelsFollowTheCurve() {
        assertThat(Levels.xpForLevel(2)).isEqualTo(283);
        assertThat(Levels.xpForLevel(3)).isEqualTo(520);
        assertThat(Levels.of(0).level()).isEqualTo(1);
        assertThat(Levels.of(282).level()).isEqualTo(1);
        assertThat(Levels.of(283).level()).isEqualTo(2);
        assertThat(Levels.of(400).percentToNext()).isEqualTo(49);
        assertThat(Levels.of(-50).xp()).isZero();
    }

    private static Set<LocalDate> days(int... offsets) {
        Set<LocalDate> out = new HashSet<>();
        for (int o : offsets) {
            out.add(TODAY.minusDays(o));
        }
        return out;
    }

    @Test
    void streaksCountDaysAndTodayNeverBreaksThem() {
        assertThat(Streaks.compute(Set.of(), TODAY).current()).isZero();
        assertThat(Streaks.compute(days(0, 1, 2), TODAY).current()).isEqualTo(3);
        Streaks.Streak notYet = Streaks.compute(days(1, 2), TODAY);
        assertThat(notYet.current()).isEqualTo(2);
        assertThat(notYet.activeToday()).isFalse();
        Streaks.Streak broken = Streaks.compute(days(0, 2, 3), TODAY);
        assertThat(broken.current()).isEqualTo(1);
        assertThat(broken.longest()).isEqualTo(2);
    }

    @Test
    void sevenDaysEarnAFreezeThatCoversOneMissedDay() {
        // Days 10..4 ago active (7 days: one freeze), 3 ago missed, 2..0 active.
        Streaks.Streak s = Streaks.compute(days(10, 9, 8, 7, 6, 5, 4, 2, 1, 0), TODAY);
        assertThat(s.current()).isEqualTo(10);
        assertThat(s.frozenDays()).containsExactly(TODAY.minusDays(3));
        assertThat(s.freezesHeld()).isZero();
        // Without the freeze (only 6 days before the gap) the streak resets.
        Streaks.Streak t = Streaks.compute(days(9, 8, 7, 6, 5, 4, 2, 1, 0), TODAY);
        assertThat(t.current()).isEqualTo(3);
        assertThat(t.longest()).isEqualTo(6);
        // Two missed days in a row need two freezes.
        Set<LocalDate> long21 = new HashSet<>();
        for (int i = 30; i >= 10; i--) {
            long21.add(TODAY.minusDays(i));
        }
        long21.addAll(days(7, 6, 5, 4, 3, 2, 1, 0));
        Streaks.Streak u = Streaks.compute(long21, TODAY);
        assertThat(u.frozenDays()).hasSize(2);
        assertThat(u.current()).isEqualTo(29);
    }

    @Test
    void gameXpAndImprovement() {
        assertThat(XpRules.game(0.59)).isZero();
        assertThat(XpRules.game(0.6)).isEqualTo(15);
        assertThat(XpRules.game(1.0)).isEqualTo(30);
        assertThat(XpRules.improved(0.5, 0.6)).isTrue();
        assertThat(XpRules.improved(0.5, 0.59)).isFalse();
    }

    private static Facts facts(int modules, int practice, double acc, int skills, int accepted, int askers) {
        return new Facts() {
            public int modulesCompleted() { return modules; }
            public int coreSkillsAtLeast(String s, List<String> t) { return 0; }
            public int topicsFullyMastered() { return 0; }
            public int gamesAtAccuracy(String t, double a) { return 6; }
            public int gameSkillsAtAccuracy(String t, double a) { return 2; }
            public int practiceFirstAnswers() { return practice; }
            public double practiceAccuracy() { return acc; }
            public int practiceSkills() { return skills; }
            public int assessmentsAtLeast(String k, double f) { return 0; }
            public int comebacks() { return 0; }
            public int acceptedAnswers() { return accepted; }
            public int distinctAskersAccepted() { return askers; }
            public int verifiedAnswers() { return 0; }
            public int longestStreak() { return 8; }
        };
    }

    @Test
    void badgeProgressReportsWhatIsMissing() {
        Facts f = facts(1, 120, 0.65, 6, 5, 4);
        assertThat(Badges.progress(Badges.find("first-steps").orElseThrow().criterion(), f).met()).isTrue();
        Badges.Progress practice = Badges.progress(Badges.find("practice-pro").orElseThrow().criterion(), f);
        assertThat(practice.met()).as("accuracy too low").isFalse();
        assertThat(practice.detail()).contains("accuracy 65% (needs 70%)");
        assertThat(Badges.progress(Badges.find("practice-pro").orElseThrow().criterion(), facts(1, 120, 0.7, 6, 0, 0)).met()).isTrue();
        Badges.Progress games = Badges.progress(Badges.find("debug-detective").orElseThrow().criterion(), f);
        assertThat(games.met()).as("needs 3 skills").isFalse();
        assertThat(games.current()).isEqualTo(5);
        assertThat(Badges.progress(Badges.find("helpful-peer").orElseThrow().criterion(), f).met()).as("askers").isFalse();
        assertThat(Badges.progress(Badges.find("streak-7").orElseThrow().criterion(), f).met()).isTrue();
        assertThat(Badges.progress(Badges.find("streak-30").orElseThrow().criterion(), f).percent()).isEqualTo(26);
        assertThat(Badges.CATALOGUE).extracting(Badges.Badge::key).doesNotHaveDuplicates().hasSizeGreaterThanOrEqualTo(8);
    }
}
