package app.pathwise.core.community;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure rules for live doubt sessions: lifecycle, who may join and ask, and the order of the question queue. */
public final class LiveSessionRules {
    private LiveSessionRules() {}

    /** At most this many of one participant's questions may wait in the queue at once. */
    public static final int MAX_QUEUED_PER_PERSON = 3;

    private static final Map<String, Set<String>> SESSION_MOVES = Map.of(
            "scheduled", Set.of("live", "ended"),
            "live", Set.of("ended"),
            "ended", Set.of());

    private static final Map<String, Set<String>> QUESTION_MOVES = Map.of(
            "queued", Set.of("answering", "answered", "dismissed"),
            "answering", Set.of("answered", "dismissed", "queued"),
            "answered", Set.of(),
            "dismissed", Set.of("queued"));

    public static boolean canMove(String from, String to) {
        return SESSION_MOVES.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean canMoveQuestion(String from, String to) {
        return QUESTION_MOVES.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Joining: never after the end; a removed person can't come back; when the host has locked the session
     * only people who were already in it can return.
     *
     * @param participantStatus null if the person has never joined
     */
    public static boolean canJoin(String sessionStatus, boolean locked, String participantStatus) {
        if ("ended".equals(sessionStatus) || "removed".equals(participantStatus)) {
            return false;
        }
        return !locked || participantStatus != null;
    }

    /** Questions and hands only while live, from active participants who aren't the host. */
    public static boolean canTakePart(String sessionStatus, String participantStatus) {
        return "live".equals(sessionStatus) && "active".equals(participantStatus);
    }

    public static boolean canAsk(String sessionStatus, String participantStatus, int alreadyQueued) {
        return canTakePart(sessionStatus, participantStatus) && alreadyQueued < MAX_QUEUED_PER_PERSON;
    }

    public static String aliasLabel(int aliasNumber) {
        return "Participant " + aliasNumber;
    }

    /** A queue entry as the ordering sees it. */
    public record Entry(String status, int votes, Instant createdAt) {}

    private static int rank(String status) {
        return switch (status) {
            case "answering" -> 0;
            case "queued" -> 1;
            case "answered" -> 2;
            default -> 3;
        };
    }

    /** The one being answered, then the queue by votes (ties: oldest first), then answered, then dismissed. */
    public static final Comparator<Entry> QUEUE_ORDER = Comparator.<Entry>comparingInt(e -> rank(e.status()))
            .thenComparing(Comparator.comparingInt(Entry::votes).reversed())
            .thenComparing(Entry::createdAt);

    public static <T> List<T> ordered(List<T> items, java.util.function.Function<T, Entry> entry) {
        return items.stream().sorted(Comparator.comparing(entry, QUEUE_ORDER)).toList();
    }
}
