package app.pathwise.core.rewards;

/** XP amounts and anti-farming limits (blueprint §23). No XP for logging in, opening content or asking doubts. */
public final class XpRules {
    private XpRules() {}

    public static final int MODULE_COMPLETE = 50;
    public static final int PRACTICE_CORRECT = 5;
    public static final int PRACTICE_DAILY_CAP = 100;
    public static final int ASSESSMENT_SUBMIT = 30;
    public static final int ASSESSMENT_IMPROVE = 20;
    public static final int SKILL_PROFICIENT = 40;
    public static final int SKILL_MASTERED = 100;
    public static final int ANSWER_ACCEPTED = 25;
    public static final int ANSWER_VERIFIED = 40;
    public static final int GAMES_PER_DAY_PER_GAME = 3;
    public static final double GAME_MIN_ACCURACY = 0.6;

    /** 15 at 60% accuracy rising to 30 at 100%; nothing below 60%. */
    public static int game(double accuracy) {
        if (accuracy < GAME_MIN_ACCURACY) {
            return 0;
        }
        return 15 + (int) Math.round(15 * (accuracy - GAME_MIN_ACCURACY) / (1 - GAME_MIN_ACCURACY));
    }

    /** An improvement of at least 10 percentage points over the previous best attempt. */
    public static boolean improved(double previousBestFraction, double newFraction) {
        return newFraction - previousBestFraction >= 0.10 - 1e-9;
    }
}
