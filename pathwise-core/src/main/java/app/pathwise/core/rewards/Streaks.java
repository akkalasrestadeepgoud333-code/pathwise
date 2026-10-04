package app.pathwise.core.rewards;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Learning streaks (blueprint §23): a day counts if it had a meaningful learning action. Every 7 days in a
 * row earns a freeze (at most 2 held); a freeze is used automatically to cover a single missed day. Today
 * never breaks a streak: there's still time.
 */
public final class Streaks {
    public static final int FREEZE_EVERY = 7;
    public static final int MAX_FREEZES = 2;

    private Streaks() {}

    public record Streak(int current, int longest, int freezesHeld, List<LocalDate> frozenDays, boolean activeToday) {}

    public static Streak compute(Set<LocalDate> activeDays, LocalDate today) {
        if (activeDays.isEmpty()) {
            return new Streak(0, 0, 0, List.of(), false);
        }
        LocalDate first = activeDays.stream().min(LocalDate::compareTo).orElseThrow();
        int current = 0;
        int longest = 0;
        int freezes = 0;
        int sinceFreeze = 0;
        List<LocalDate> frozen = new ArrayList<>();
        List<LocalDate> pendingFrozen = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            if (activeDays.contains(d)) {
                current++;
                sinceFreeze++;
                frozen.addAll(pendingFrozen);
                pendingFrozen.clear();
                if (sinceFreeze == FREEZE_EVERY) {
                    freezes = Math.min(MAX_FREEZES, freezes + 1);
                    sinceFreeze = 0;
                }
                longest = Math.max(longest, current);
            } else if (d.equals(today)) {
                break; // today isn't over
            } else if (freezes > 0 && current > 0) {
                freezes--;
                pendingFrozen.add(d); // kept only if the streak continues afterwards
            } else {
                current = 0;
                sinceFreeze = 0;
                pendingFrozen.clear();
            }
        }
        return new Streak(current, longest, freezes, List.copyOf(frozen), activeDays.contains(today));
    }
}
