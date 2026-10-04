package app.pathwise.core.games;

import app.pathwise.core.games.Game.GameAction;
import app.pathwise.core.games.Game.GameResult;

import java.util.List;

/**
 * Server-side verification of a finished game: replays the action log against the authoritative rules and
 * checks that the timing is plausible. The client's own score is never trusted.
 */
public final class Replay {
    public static final int MAX_ACTIONS = 2000;
    public static final long MAX_DURATION_MS = 30 * 60 * 1000L;
    /** Allowance for the client's clock starting slightly before the server recorded the start. */
    public static final long CLOCK_TOLERANCE_MS = 5000;

    private Replay() {}

    public record Verdict(boolean accepted, String reason, GameResult result, long durationMs) {
        static Verdict reject(String reason) {
            return new Verdict(false, reason, null, 0);
        }
    }

    /**
     * @param serverElapsedMs time between the server starting the attempt and receiving the log
     */
    public static <P> Verdict verify(Game<?, P> game, P puzzle, List<GameAction> actions, long serverElapsedMs) {
        if (actions == null || actions.isEmpty()) {
            return Verdict.reject("no actions");
        }
        if (actions.size() > MAX_ACTIONS) {
            return Verdict.reject("too many actions");
        }
        long last = -1;
        for (GameAction a : actions) {
            if (a == null || a.type() == null) {
                return Verdict.reject("malformed action");
            }
            if (a.t() < 0 || a.t() < last) {
                return Verdict.reject("timestamps go backwards");
            }
            if (last >= 0 && a.t() - last < game.minGapMillis()) {
                return Verdict.reject("actions faster than humanly plausible");
            }
            last = a.t();
        }
        if (last > MAX_DURATION_MS) {
            return Verdict.reject("game too long");
        }
        if (last > serverElapsedMs + CLOCK_TOLERANCE_MS) {
            return Verdict.reject("reported time exceeds the time since the game started");
        }
        Game.Session session = game.start(puzzle);
        try {
            for (GameAction a : actions) {
                if (session.isOver()) {
                    return Verdict.reject("actions after the game ended");
                }
                session.apply(a);
            }
        } catch (Game.InvalidActionException e) {
            return Verdict.reject("impossible action: " + e.getMessage());
        }
        if (!session.isOver()) {
            return Verdict.reject("game not finished");
        }
        return new Verdict(true, null, session.result(), last);
    }
}
