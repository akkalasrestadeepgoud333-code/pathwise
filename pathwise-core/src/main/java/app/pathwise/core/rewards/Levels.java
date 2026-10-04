package app.pathwise.core.rewards;

/** Levels from cumulative XP: reaching level n needs round(100 · n^1.5) XP (blueprint §23). Computed, never stored. */
public final class Levels {
    private Levels() {}

    public static long xpForLevel(int n) {
        return n <= 1 ? 0 : Math.round(100 * Math.pow(n, 1.5));
    }

    public record Level(int level, long xp, long levelStart, long nextLevelAt, int percentToNext) {}

    public static Level of(long xp) {
        long safe = Math.max(0, xp);
        int n = 1;
        while (xpForLevel(n + 1) <= safe) {
            n++;
        }
        long start = xpForLevel(n);
        long next = xpForLevel(n + 1);
        int pct = (int) Math.min(100, Math.round(100.0 * (safe - start) / (next - start)));
        return new Level(n, safe, start, next, pct);
    }
}
