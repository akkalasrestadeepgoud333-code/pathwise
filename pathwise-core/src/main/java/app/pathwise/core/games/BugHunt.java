package app.pathwise.core.games;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bug Hunt: find the faulty line in a short snippet, then say what kind of bug it is.
 * <p>
 * Per snippet: finding the line first time is 10 points (5 after one miss; a second miss reveals it), and
 * naming the bug class is 10 more (one guess). Each snippet is one observation for its skill: half for the
 * line, half for the class.
 */
public final class BugHunt implements Game<BugHunt.Config, BugHunt.Puzzle> {
    public static final String KEY = "bug_hunt";
    static final int LINE_FIRST = 10;
    static final int LINE_AFTER_MISS = 5;
    static final int CLASS_RIGHT = 10;

    /** Bug classes and their labels, shown as the choices. */
    public static final Map<String, String> CLASSES;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("off_by_one", "Off by one");
        m.put("wrong_condition", "Wrong condition");
        m.put("null_reference", "Missing null check");
        m.put("missing_base_case", "Missing or wrong base case");
        m.put("wrong_order", "Steps in the wrong order");
        m.put("wrong_update", "Wrong update or return");
        CLASSES = java.util.Collections.unmodifiableMap(m);
    }

    public record Snippet(String title, String language, String code, int bugLine, String bugClass, String skill,
                          String explanation) {
        public int lineCount() {
            return code == null ? 0 : code.split("\n", -1).length;
        }
    }

    public record Config(int rounds, List<Snippet> snippets) {}

    public record Choice(String key, String label) {}

    public record Puzzle(List<Snippet> rounds, List<Choice> classes) {}

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
        return 300;
    }

    public static List<String> validate(Config c) {
        List<String> errors = new ArrayList<>();
        if (c.snippets() == null || c.snippets().isEmpty()) {
            errors.add("needs snippets");
            return errors;
        }
        if (c.rounds() < 1 || c.rounds() > c.snippets().size() || c.rounds() > 10) {
            errors.add("rounds must be 1-10 and no more than the number of snippets");
        }
        Set<String> titles = new HashSet<>();
        for (Snippet s : c.snippets()) {
            String where = "snippet '" + s.title() + "'";
            if (s.title() == null || !titles.add(s.title())) {
                errors.add("snippet titles must be present and unique");
            }
            if (s.code() == null || s.code().isBlank() || s.lineCount() > 25) {
                errors.add(where + ": code must be 1-25 lines");
            } else if (s.bugLine() < 1 || s.bugLine() > s.lineCount()) {
                errors.add(where + ": bugLine is outside the code");
            }
            if (!CLASSES.containsKey(s.bugClass())) {
                errors.add(where + ": bugClass must be one of " + CLASSES.keySet());
            }
            if (s.skill() == null || s.explanation() == null || s.explanation().isBlank()) {
                errors.add(where + ": needs a skill and an explanation");
            }
        }
        return errors;
    }

    @Override
    public Puzzle generate(Config config, long seed) {
        SplitMix64 rng = new SplitMix64(seed);
        List<Snippet> pool = new ArrayList<>(config.snippets());
        rng.shuffle(pool);
        List<Choice> classes = CLASSES.entrySet().stream().map(e -> new Choice(e.getKey(), e.getValue())).toList();
        return new Puzzle(List.copyOf(pool.subList(0, config.rounds())), classes);
    }

    @Override
    public Session start(Puzzle puzzle) {
        return new Play(puzzle);
    }

    private static final class Play implements Session {
        private final Puzzle puzzle;
        private final List<StepEvent> events = new ArrayList<>();
        private int round;
        private boolean lineFound;
        private int lineMisses;
        private double linePart;
        private int score;
        private int perfect;

        Play(Puzzle puzzle) {
            this.puzzle = puzzle;
        }

        @Override
        public void apply(GameAction action) {
            Snippet s = puzzle.rounds().get(round);
            switch (action.type()) {
                case "line" -> {
                    if (lineFound) {
                        throw new InvalidActionException("line already found");
                    }
                    int line;
                    try {
                        line = Integer.parseInt(action.a());
                    } catch (NumberFormatException e) {
                        throw new InvalidActionException("not a line number");
                    }
                    if (line < 1 || line > s.lineCount()) {
                        throw new InvalidActionException("no such line");
                    }
                    if (line == s.bugLine()) {
                        score += lineMisses == 0 ? LINE_FIRST : LINE_AFTER_MISS;
                        linePart = lineMisses == 0 ? 0.5 : 0.25;
                        lineFound = true;
                    } else if (++lineMisses >= 2) {
                        linePart = 0; // revealed
                        lineFound = true;
                    }
                }
                case "class" -> {
                    if (!lineFound) {
                        throw new InvalidActionException("find the line first");
                    }
                    if (!CLASSES.containsKey(action.a())) {
                        throw new InvalidActionException("unknown bug class");
                    }
                    boolean right = s.bugClass().equals(action.a());
                    if (right) {
                        score += CLASS_RIGHT;
                    }
                    double partial = linePart + (right ? 0.5 : 0);
                    if (partial == 1.0) {
                        perfect++;
                    }
                    events.add(new StepEvent(s.skill(), partial == 1.0, partial, note(s, linePart, right)));
                    round++;
                    lineFound = false;
                    lineMisses = 0;
                    linePart = 0;
                }
                default -> throw new InvalidActionException("unknown action " + action.type());
            }
        }

        private static String note(Snippet s, double linePart, boolean right) {
            String line = linePart == 0.5 ? "found line " + s.bugLine() + " first time"
                    : linePart > 0 ? "found line " + s.bugLine() + " after a miss" : "line " + s.bugLine() + " was revealed";
            return s.title() + ": " + line + (right ? ", and named the bug" : ", but the bug was \"" + CLASSES.get(s.bugClass()) + "\"");
        }

        @Override
        public boolean isOver() {
            return round >= puzzle.rounds().size();
        }

        @Override
        public GameResult result() {
            int n = puzzle.rounds().size();
            return new GameResult(score, n * (LINE_FIRST + CLASS_RIGHT), n == 0 ? 0 : (double) perfect / n, List.copyOf(events));
        }
    }
}
