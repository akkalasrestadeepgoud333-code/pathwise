package app.pathwise.core.rewards;

import java.util.List;
import java.util.Optional;

/**
 * The badge catalogue and its criteria (blueprint §24). Criteria are typed records, not code or free-form
 * JSON; each is evaluated against {@link Facts} (counts the data layer computes from recorded evidence) and
 * reports progress, so locked badges can show how close you are.
 */
public final class Badges {
    private Badges() {}

    public enum Rarity {
        COMMON(25), UNCOMMON(50), RARE(100), EPIC(200), LEGENDARY(400);

        public final int xp;

        Rarity(int xp) {
            this.xp = xp;
        }

        public String label() {
            return name().charAt(0) + name().substring(1).toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** What the evaluators may ask. Implementations read recorded evidence only. */
    public interface Facts {
        int modulesCompleted();

        /** The largest number of core skills at or above {@code minState} within the given topics (any one learner). */
        int coreSkillsAtLeast(String minState, List<String> topicKeys);

        /** Topics whose core skills are all mastered. */
        int topicsFullyMastered();

        /** Verified games of a template at or above an accuracy; and how many distinct skills they covered. */
        int gamesAtAccuracy(String template, double minAccuracy);

        int gameSkillsAtAccuracy(String template, double minAccuracy);

        /** First practice answers (not repeats), rolling accuracy over them, distinct skills practised. */
        int practiceFirstAnswers();

        double practiceAccuracy();

        int practiceSkills();

        /** Distinct official assessments of a kind with a best score at or above a fraction. */
        int assessmentsAtLeast(String kind, double minFraction);

        /** Skills that went from needs practice to proficient or better. */
        int comebacks();

        int acceptedAnswers();

        int distinctAskersAccepted();

        int verifiedAnswers();

        int longestStreak();
    }

    public sealed interface Criterion permits ModulesCompleted, CoreSkillsAtLeast, TopicMastered, GameRuns, PracticeVolume,
            AssessmentScores, Comeback, AcceptedAnswers, VerifiedAnswers, StreakDays {}

    public record ModulesCompleted(int count) implements Criterion {}

    public record CoreSkillsAtLeast(String state, List<String> topicKeys, int count) implements Criterion {}

    public record TopicMastered(int count) implements Criterion {}

    public record GameRuns(String template, double minAccuracy, int count, int distinctSkills) implements Criterion {}

    public record PracticeVolume(int count, double minAccuracy, int distinctSkills) implements Criterion {}

    public record AssessmentScores(String kind, double minFraction, int count) implements Criterion {}

    public record Comeback(int count) implements Criterion {}

    public record AcceptedAnswers(int count, int distinctAskers) implements Criterion {}

    public record VerifiedAnswers(int count) implements Criterion {}

    public record StreakDays(int days) implements Criterion {}

    public record Badge(String key, String name, String description, String category, Rarity rarity, String icon, Criterion criterion) {}

    public record Progress(int current, int target, boolean met, String detail) {
        public int percent() {
            return target == 0 ? 100 : Math.min(100, 100 * current / target);
        }
    }

    public static final List<Badge> CATALOGUE = List.of(
            new Badge("first-steps", "First Steps", "Finish your first module, every required section.", "milestone", Rarity.COMMON,
                    "sparkles", new ModulesCompleted(1)),
            new Badge("tree-explorer", "Tree Explorer", "Reach Proficient or better in 4 core tree skills (trees, binary trees, traversals, BSTs).",
                    "mastery", Rarity.UNCOMMON, "route",
                    new CoreSkillsAtLeast("proficient", List.of("trees", "binary-trees", "tree-traversals", "bst"), 4)),
            new Badge("concept-master", "Concept Master", "Master every core skill of one topic. Mastered needs recent assessment or challenge evidence.",
                    "mastery", Rarity.RARE, "award", new TopicMastered(1)),
            new Badge("debug-detective", "Debug Detective", "Finish 5 Bug Hunt games at 80% or better, covering 3 different skills.",
                    "game", Rarity.UNCOMMON, "bug", new GameRuns("bug_hunt", 0.8, 5, 3)),
            new Badge("practice-pro", "Practice Pro", "Answer 100 practice questions for the first time, at 70% accuracy, across 5 skills.",
                    "practice", Rarity.UNCOMMON, "target", new PracticeVolume(100, 0.7, 5)),
            new Badge("assessment-ace", "Assessment Ace", "Score 90% or more on 3 different topic tests.", "assessment", Rarity.RARE,
                    "clipboard-check", new AssessmentScores("topic_test", 0.9, 3)),
            new Badge("comeback", "Comeback", "Take a skill from Needs practice to Proficient.", "growth", Rarity.UNCOMMON,
                    "activity", new Comeback(1)),
            new Badge("helpful-peer", "Helpful Peer", "Have 5 answers accepted, by 5 different askers.", "community", Rarity.UNCOMMON,
                    "users", new AcceptedAnswers(5, 5)),
            new Badge("verified-helper", "Verified Helper", "Have 3 answers marked Teacher verified.", "community", Rarity.RARE,
                    "shield", new VerifiedAnswers(3)),
            new Badge("streak-7", "Learning Streak I", "Learn on 7 days in a row (freezes count).", "streak", Rarity.COMMON,
                    "flame", new StreakDays(7)),
            new Badge("streak-30", "Learning Streak II", "Learn on 30 days in a row (freezes count).", "streak", Rarity.UNCOMMON,
                    "flame", new StreakDays(30)));

    public static Optional<Badge> find(String key) {
        return CATALOGUE.stream().filter(b -> b.key().equals(key)).findFirst();
    }

    public static Progress progress(Criterion criterion, Facts f) {
        return switch (criterion) {
            case ModulesCompleted c -> simple(f.modulesCompleted(), c.count(), "modules finished");
            case CoreSkillsAtLeast c -> simple(f.coreSkillsAtLeast(c.state(), c.topicKeys()), c.count(), "core skills at " + c.state() + " or better");
            case TopicMastered c -> simple(f.topicsFullyMastered(), c.count(), "topics fully mastered");
            case GameRuns c -> {
                int runs = f.gamesAtAccuracy(c.template(), c.minAccuracy());
                int skills = f.gameSkillsAtAccuracy(c.template(), c.minAccuracy());
                boolean met = runs >= c.count() && skills >= c.distinctSkills();
                yield new Progress(Math.min(runs, c.count()), c.count(), met,
                        runs + " of " + c.count() + " games at " + pct(c.minAccuracy()) + "+, " + skills + " of " + c.distinctSkills() + " skills");
            }
            case PracticeVolume c -> {
                int n = f.practiceFirstAnswers();
                double acc = f.practiceAccuracy();
                int skills = f.practiceSkills();
                boolean met = n >= c.count() && acc >= c.minAccuracy() && skills >= c.distinctSkills();
                yield new Progress(Math.min(n, c.count()), c.count(), met,
                        n + " of " + c.count() + " answers, accuracy " + pct(acc) + " (needs " + pct(c.minAccuracy()) + "), "
                                + skills + " of " + c.distinctSkills() + " skills");
            }
            case AssessmentScores c -> simple(f.assessmentsAtLeast(c.kind(), c.minFraction()), c.count(), "tests at " + pct(c.minFraction()) + "+");
            case Comeback c -> simple(f.comebacks(), c.count(), "skills turned around");
            case AcceptedAnswers c -> {
                int n = f.acceptedAnswers();
                int askers = f.distinctAskersAccepted();
                boolean met = n >= c.count() && askers >= c.distinctAskers();
                yield new Progress(Math.min(Math.min(n, askers), c.count()), c.count(), met,
                        n + " accepted answers from " + askers + " different askers");
            }
            case VerifiedAnswers c -> simple(f.verifiedAnswers(), c.count(), "teacher-verified answers");
            case StreakDays c -> simple(f.longestStreak(), c.days(), "days in a row (best)");
        };
    }

    private static Progress simple(int current, int target, String what) {
        return new Progress(Math.min(current, target), target, current >= target, Math.min(current, target) + " of " + target + " " + what);
    }

    private static String pct(double f) {
        return Math.round(f * 100) + "%";
    }
}
