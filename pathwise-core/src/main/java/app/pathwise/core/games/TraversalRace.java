package app.pathwise.core.games;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Traversal Race: tap the nodes of a generated binary search tree in the requested order.
 * <p>
 * Scoring per tap: 10 points first time, 4 points after one miss; a second miss on the same node reveals it
 * (0 points). Each round produces one learning observation for the traversal's skill: the share of nodes
 * tapped right first time.
 */
public final class TraversalRace implements Game<TraversalRace.Config, TraversalRace.Puzzle> {
    public static final String KEY = "traversal_race";
    public static final List<String> ORDERS = List.of("preorder", "inorder", "postorder", "level");
    static final int FIRST_TRY = 10;
    static final int AFTER_MISS = 4;
    static final int MISSES_BEFORE_REVEAL = 2;

    /** {@code skills} maps each order to the skill key it gives evidence for. */
    public record Config(List<String> orders, int rounds, int minNodes, int maxNodes, Map<String, String> skills) {}

    public record Node(int value, Integer left, Integer right) {}

    public record Round(String order, String skill, int root, List<Node> nodes, List<Integer> answer) {}

    public record Puzzle(List<Round> rounds) {}

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
        return 120;
    }

    /** Problems with a config, in plain language (empty when valid). */
    public static List<String> validate(Config c) {
        List<String> errors = new ArrayList<>();
        if (c.orders() == null || c.orders().isEmpty() || !ORDERS.containsAll(c.orders())) {
            errors.add("orders must be a non-empty subset of " + ORDERS);
        }
        if (c.rounds() < 1 || c.rounds() > 8) {
            errors.add("rounds must be 1-8");
        }
        if (c.minNodes() < 3 || c.maxNodes() > 12 || c.minNodes() > c.maxNodes()) {
            errors.add("node counts must satisfy 3 <= minNodes <= maxNodes <= 12");
        }
        if (c.orders() != null && (c.skills() == null || !c.skills().keySet().containsAll(c.orders()))) {
            errors.add("skills must name a skill for every order");
        }
        return errors;
    }

    @Override
    public Puzzle generate(Config config, long seed) {
        SplitMix64 rng = new SplitMix64(seed);
        List<Round> rounds = new ArrayList<>();
        int offset = rng.nextInt(config.orders().size());
        for (int r = 0; r < config.rounds(); r++) {
            String order = config.orders().get((offset + r) % config.orders().size());
            int n = config.minNodes() + rng.nextInt(config.maxNodes() - config.minNodes() + 1);
            Set<Integer> used = new HashSet<>();
            List<Integer> inserted = new ArrayList<>();
            while (inserted.size() < n) {
                int v = 1 + rng.nextInt(99);
                if (used.add(v)) {
                    inserted.add(v);
                }
            }
            Map<Integer, int[]> children = new LinkedHashMap<>(); // value -> {left, right}, 0 = none
            int root = inserted.get(0);
            children.put(root, new int[2]);
            for (int v : inserted.subList(1, inserted.size())) {
                int at = root;
                while (true) {
                    int side = v < at ? 0 : 1;
                    int next = children.get(at)[side];
                    if (next == 0) {
                        children.get(at)[side] = v;
                        children.put(v, new int[2]);
                        break;
                    }
                    at = next;
                }
            }
            List<Node> nodes = new ArrayList<>();
            children.forEach((v, kids) -> nodes.add(new Node(v, kids[0] == 0 ? null : kids[0], kids[1] == 0 ? null : kids[1])));
            rounds.add(new Round(order, config.skills().get(order), root, nodes, traverse(order, root, children)));
        }
        return new Puzzle(rounds);
    }

    static List<Integer> traverse(String order, int root, Map<Integer, int[]> children) {
        List<Integer> out = new ArrayList<>();
        if ("level".equals(order)) {
            Deque<Integer> queue = new ArrayDeque<>(List.of(root));
            while (!queue.isEmpty()) {
                int v = queue.poll();
                out.add(v);
                for (int child : children.get(v)) {
                    if (child != 0) {
                        queue.add(child);
                    }
                }
            }
            return out;
        }
        walk(order, root, children, out);
        return out;
    }

    private static void walk(String order, int v, Map<Integer, int[]> children, List<Integer> out) {
        if (v == 0) {
            return;
        }
        int[] kids = children.get(v);
        if ("preorder".equals(order)) {
            out.add(v);
        }
        walk(order, kids[0], children, out);
        if ("inorder".equals(order)) {
            out.add(v);
        }
        walk(order, kids[1], children, out);
        if ("postorder".equals(order)) {
            out.add(v);
        }
    }

    @Override
    public Session start(Puzzle puzzle) {
        return new Play(puzzle);
    }

    private static final class Play implements Session {
        private final Puzzle puzzle;
        private final List<StepEvent> events = new ArrayList<>();
        private int round;
        private int position;
        private int misses;
        private int firstTryInRound;
        private int firstTryTotal;
        private int score;

        Play(Puzzle puzzle) {
            this.puzzle = puzzle;
        }

        @Override
        public void apply(GameAction action) {
            if (!"tap".equals(action.type())) {
                throw new InvalidActionException("unknown action " + action.type());
            }
            Round r = puzzle.rounds().get(round);
            int value;
            try {
                value = Integer.parseInt(action.a());
            } catch (NumberFormatException e) {
                throw new InvalidActionException("not a node value");
            }
            if (r.nodes().stream().noneMatch(n -> n.value() == value)) {
                throw new InvalidActionException("no such node " + value);
            }
            if (r.answer().subList(0, position).contains(value)) {
                throw new InvalidActionException("node already visited");
            }
            if (value == r.answer().get(position)) {
                if (misses == 0) {
                    score += FIRST_TRY;
                    firstTryInRound++;
                    firstTryTotal++;
                } else {
                    score += AFTER_MISS;
                }
                advance(r);
            } else if (++misses >= MISSES_BEFORE_REVEAL) {
                advance(r); // revealed: no points
            }
        }

        private void advance(Round r) {
            position++;
            misses = 0;
            if (position == r.answer().size()) {
                int n = r.answer().size();
                events.add(new StepEvent(r.skill(), firstTryInRound == n, (double) firstTryInRound / n,
                        firstTryInRound + " of " + n + " nodes right first time in " + label(r.order())));
                round++;
                position = 0;
                firstTryInRound = 0;
            }
        }

        @Override
        public boolean isOver() {
            return round >= puzzle.rounds().size();
        }

        @Override
        public GameResult result() {
            int total = puzzle.rounds().stream().mapToInt(r -> r.answer().size()).sum();
            return new GameResult(score, total * FIRST_TRY, total == 0 ? 0 : (double) firstTryTotal / total, List.copyOf(events));
        }
    }

    static String label(String order) {
        return "level".equals(order) ? "level order" : order;
    }
}
