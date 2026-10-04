package app.pathwise.core.games;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Match Pairs: connect each term with its meaning.
 * <p>
 * Scoring per pair: 10 points if matched without a wrong guess on that term, 5 after one, 0 after more.
 * Each matched pair is one learning observation for its skill.
 */
public final class MatchPairs implements Game<MatchPairs.Config, MatchPairs.Puzzle> {
    public static final String KEY = "match_pairs";
    static final int CLEAN = 10;
    static final int ONE_MISS = 5;

    public record Pair(String left, String right, String skill) {}

    public record Config(List<Pair> pairs, int perGame) {}

    /** {@code items} are in left-column order; {@code rightOrder} lists item ids in right-column order. */
    public record Item(String id, String left, String right, String skill) {}

    public record Puzzle(List<Item> items, List<String> rightOrder) {}

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public long minGapMillis() {
        return 200;
    }

    public static List<String> validate(Config c) {
        List<String> errors = new ArrayList<>();
        if (c.pairs() == null || c.pairs().size() < 3) {
            errors.add("needs at least 3 pairs");
            return errors;
        }
        if (c.perGame() < 3 || c.perGame() > 8 || c.perGame() > c.pairs().size()) {
            errors.add("perGame must be 3-8 and no more than the number of pairs");
        }
        Set<String> lefts = new HashSet<>();
        Set<String> rights = new HashSet<>();
        for (Pair p : c.pairs()) {
            if (p.left() == null || p.left().isBlank() || p.right() == null || p.right().isBlank() || p.skill() == null) {
                errors.add("every pair needs left, right and skill");
            } else if (!lefts.add(p.left()) || !rights.add(p.right())) {
                errors.add("duplicate pair text: " + p.left());
            }
        }
        return errors;
    }

    @Override
    public Puzzle generate(Config config, long seed) {
        SplitMix64 rng = new SplitMix64(seed);
        List<Integer> picks = new ArrayList<>(IntStream.range(0, config.pairs().size()).boxed().toList());
        rng.shuffle(picks);
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < config.perGame(); i++) {
            Pair p = config.pairs().get(picks.get(i));
            items.add(new Item("p" + (i + 1), p.left(), p.right(), p.skill()));
        }
        List<String> right = new ArrayList<>(items.stream().map(Item::id).toList());
        rng.shuffle(right);
        return new Puzzle(items, right);
    }

    @Override
    public Session start(Puzzle puzzle) {
        return new Play(puzzle);
    }

    private static final class Play implements Session {
        private final Puzzle puzzle;
        private final Map<String, Item> byId = new HashMap<>();
        private final Map<String, Integer> misses = new HashMap<>();
        private final Set<String> matched = new HashSet<>();
        private final List<StepEvent> events = new ArrayList<>();
        private int score;
        private int clean;

        Play(Puzzle puzzle) {
            this.puzzle = puzzle;
            puzzle.items().forEach(i -> byId.put(i.id(), i));
        }

        @Override
        public void apply(GameAction action) {
            if (!"match".equals(action.type())) {
                throw new InvalidActionException("unknown action " + action.type());
            }
            String left = action.a();
            String right = action.b();
            if (!byId.containsKey(left) || !byId.containsKey(right)) {
                throw new InvalidActionException("no such item");
            }
            if (matched.contains(left) || matched.contains(right)) {
                throw new InvalidActionException("item already matched");
            }
            if (left.equals(right)) {
                int m = misses.getOrDefault(left, 0);
                score += m == 0 ? CLEAN : m == 1 ? ONE_MISS : 0;
                if (m == 0) {
                    clean++;
                }
                matched.add(left);
                Item item = byId.get(left);
                double partial = m == 0 ? 1.0 : m == 1 ? 0.5 : 0.0;
                events.add(new StepEvent(item.skill(), m == 0, partial,
                        m == 0 ? "Matched \"" + item.left() + "\" first time" : "Matched \"" + item.left() + "\" after " + m + (m == 1 ? " wrong guess" : " wrong guesses")));
            } else {
                misses.merge(left, 1, Integer::sum);
            }
        }

        @Override
        public boolean isOver() {
            return matched.size() == puzzle.items().size();
        }

        @Override
        public GameResult result() {
            int n = puzzle.items().size();
            return new GameResult(score, n * CLEAN, n == 0 ? 0 : (double) clean / n, List.copyOf(events));
        }
    }
}
