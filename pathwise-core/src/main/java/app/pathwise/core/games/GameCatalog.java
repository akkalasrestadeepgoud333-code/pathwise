package app.pathwise.core.games;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The game templates this build knows how to play and verify. */
public final class GameCatalog {

    public record Template(String key, String title, String teaches, Game<?, ?> game) {}

    private static final Map<String, Template> TEMPLATES = new LinkedHashMap<>();

    static {
        add(new Template(TraversalRace.KEY, "Traversal Race",
                "Tap the nodes of a tree in preorder, inorder, postorder or level order.", new TraversalRace()));
        add(new Template(MatchPairs.KEY, "Match Pairs", "Match each term with what it means.", new MatchPairs()));
        add(new Template(BugHunt.KEY, "Bug Hunt", "Find the faulty line in a short program and name the bug.", new BugHunt()));
    }

    private GameCatalog() {}

    private static void add(Template t) {
        TEMPLATES.put(t.key(), t);
    }

    public static Optional<Template> find(String key) {
        return Optional.ofNullable(TEMPLATES.get(key));
    }

    public static List<Template> all() {
        return List.copyOf(TEMPLATES.values());
    }
}
