package app.pathwise.core.games;

import java.util.List;

/**
 * One game template: generates a puzzle from a config and a seed, and plays it by rules that the server
 * uses to replay and verify a finished game. The browser runs the same scoring rules (in JS) for instant
 * feedback; shared test vectors keep the two in agreement.
 *
 * @param <C> the template's config (authored in a content pack)
 * @param <P> the generated puzzle (sent to the browser)
 */
public interface Game<C, P> {

    String key();

    int version();

    /** Faster than this between two actions is not humanly plausible, so the replay is rejected. */
    long minGapMillis();

    P generate(C config, long seed);

    Session start(P puzzle);

    /** A game in progress. Actions that are impossible in the current state throw {@link InvalidActionException}. */
    interface Session {
        void apply(GameAction action);

        boolean isOver();

        GameResult result();
    }

    /** A recorded action: what the player did, and when (milliseconds since the game started). */
    record GameAction(String type, String a, String b, long t) {}

    /** One learning observation produced by play: about one skill, with a short plain-language note. */
    record StepEvent(String skillKey, boolean correct, double partial, String note) {}

    record GameResult(int score, int maxScore, double accuracy, List<StepEvent> events) {}

    final class InvalidActionException extends RuntimeException {
        public InvalidActionException(String message) {
            super(message);
        }
    }
}
