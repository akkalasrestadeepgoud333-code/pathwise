package app.pathwise.core.games;

import java.util.List;

/** Small deterministic PRNG (SplitMix64). The same seed always generates the same puzzle. */
public final class SplitMix64 {
    private long state;

    public SplitMix64(long seed) {
        this.state = seed;
    }

    public long nextLong() {
        long z = (state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Uniform-enough integer in [0, bound). */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        return (int) Long.remainderUnsigned(nextLong(), bound);
    }

    /** Fisher–Yates shuffle in place. */
    public <T> void shuffle(List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }
}
