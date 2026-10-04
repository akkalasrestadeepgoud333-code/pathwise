package app.pathwise.core.games;

import app.pathwise.core.games.Game.GameAction;
import app.pathwise.core.games.Game.GameResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Game rule cores: the shared vectors (also run by the JS tests, for parity), deterministic generation,
 * and replay verification that rejects tampering.
 */
class GamesTest {
    private static final ObjectMapper JSON = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static JsonNode vectors(String key) {
        try (InputStream in = GamesTest.class.getResourceAsStream("/game-vectors/" + key + ".json")) {
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Vectors write actions compactly; this expands them (one action every 400 ms). */
    static List<GameAction> actions(String game, JsonNode list) {
        List<GameAction> out = new ArrayList<>();
        int i = 0;
        for (JsonNode a : list) {
            long t = 400L * i++;
            out.add(switch (game) {
                case TraversalRace.KEY -> new GameAction("tap", a.asText(), null, t);
                case MatchPairs.KEY -> new GameAction("match", a.get(0).asText(), a.get(1).asText(), t);
                default -> new GameAction(a.get(0).asText(), a.get(1).asText(), null, t);
            });
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @TestFactory
    Stream<DynamicTest> sharedVectors() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (var t : GameCatalog.all()) {
            JsonNode v = vectors(t.key());
            Class<?> puzzleType = switch (t.key()) {
                case TraversalRace.KEY -> TraversalRace.Puzzle.class;
                case MatchPairs.KEY -> MatchPairs.Puzzle.class;
                default -> BugHunt.Puzzle.class;
            };
            Object puzzle = JSON.treeToValue(v.get("puzzle"), puzzleType);
            for (JsonNode c : v.get("cases")) {
                tests.add(DynamicTest.dynamicTest(t.key() + ": " + c.get("name").asText(), () -> {
                    Replay.Verdict verdict = Replay.verify((Game) t.game(), puzzle, actions(t.key(), c.get("actions")), 60_000);
                    if (c.path("invalid").asBoolean()) {
                        assertThat(verdict.accepted()).isFalse();
                        assertThat(verdict.reason()).startsWith("impossible action");
                        return;
                    }
                    assertThat(verdict.accepted()).as(verdict.reason()).isTrue();
                    GameResult r = verdict.result();
                    JsonNode e = c.get("expect");
                    assertThat(r.score()).isEqualTo(e.get("score").asInt());
                    assertThat(r.maxScore()).isEqualTo(e.get("maxScore").asInt());
                    assertThat(r.accuracy()).isCloseTo(e.get("accuracy").asDouble(), within(1e-9));
                    assertThat(r.events()).hasSize(e.get("events").size());
                    for (int i = 0; i < r.events().size(); i++) {
                        JsonNode ev = e.get("events").get(i);
                        assertThat(r.events().get(i).skillKey()).isEqualTo(ev.get("skillKey").asText());
                        assertThat(r.events().get(i).correct()).isEqualTo(ev.get("correct").asBoolean());
                        assertThat(r.events().get(i).partial()).isCloseTo(ev.get("partial").asDouble(), within(1e-9));
                    }
                }));
            }
        }
        return tests.stream();
    }

    private static TraversalRace.Config raceConfig() {
        return new TraversalRace.Config(List.of("preorder", "inorder", "postorder", "level"), 4, 5, 8,
                Map.of("preorder", "preorder", "inorder", "inorder", "postorder", "postorder", "level", "level-order"));
    }

    @Test
    void generationIsDeterministicAndProducesValidTrees() {
        TraversalRace race = new TraversalRace();
        TraversalRace.Puzzle a = race.generate(raceConfig(), 42L);
        assertThat(race.generate(raceConfig(), 42L)).isEqualTo(a);
        assertThat(race.generate(raceConfig(), 43L)).isNotEqualTo(a);
        for (TraversalRace.Round r : a.rounds()) {
            assertThat(r.nodes()).hasSizeBetween(5, 8);
            assertThat(new HashSet<>(r.answer())).hasSize(r.nodes().size());
            if (r.order().equals("inorder")) {
                assertThat(r.answer()).isSorted(); // a BST's inorder is sorted
            }
            if (r.order().equals("preorder") || r.order().equals("level")) {
                assertThat(r.answer().get(0)).isEqualTo(r.root());
            }
            if (r.order().equals("postorder")) {
                assertThat(r.answer().get(r.answer().size() - 1)).isEqualTo(r.root());
            }
        }
        assertThat(a.rounds()).extracting(TraversalRace.Round::order).doesNotHaveDuplicates();

        MatchPairs pairs = new MatchPairs();
        MatchPairs.Config pc = new MatchPairs.Config(List.of(
                new MatchPairs.Pair("A", "1", "s"), new MatchPairs.Pair("B", "2", "s"), new MatchPairs.Pair("C", "3", "s"),
                new MatchPairs.Pair("D", "4", "s"), new MatchPairs.Pair("E", "5", "s")), 4);
        MatchPairs.Puzzle p = pairs.generate(pc, 7L);
        assertThat(pairs.generate(pc, 7L)).isEqualTo(p);
        assertThat(p.items()).hasSize(4);
        assertThat(p.rightOrder()).containsExactlyInAnyOrderElementsOf(p.items().stream().map(MatchPairs.Item::id).toList());
    }

    @Test
    void aPerfectPlayOfAGeneratedPuzzleScoresFull() {
        TraversalRace race = new TraversalRace();
        TraversalRace.Puzzle puzzle = race.generate(raceConfig(), 2026L);
        List<GameAction> actions = new ArrayList<>();
        long t = 0;
        for (TraversalRace.Round r : puzzle.rounds()) {
            for (int v : r.answer()) {
                actions.add(new GameAction("tap", Integer.toString(v), null, t += 250));
            }
        }
        Replay.Verdict v = Replay.verify(race, puzzle, actions, t + 1000);
        assertThat(v.accepted()).isTrue();
        assertThat(v.result().score()).isEqualTo(v.result().maxScore());
        assertThat(v.result().events()).hasSize(4).allMatch(Game.StepEvent::correct);
    }

    @Test
    void replayRejectsTamperedLogs() {
        TraversalRace race = new TraversalRace();
        TraversalRace.Puzzle puzzle = JSON.convertValue(vectors(TraversalRace.KEY).get("puzzle"), TraversalRace.Puzzle.class);
        List<String> taps = List.of("5", "3", "1", "4", "8", "2", "1", "3");
        List<GameAction> ok = new ArrayList<>();
        for (int i = 0; i < taps.size(); i++) {
            ok.add(new GameAction("tap", taps.get(i), null, 1000L * i)); // last action at 7 s
        }
        assertThat(Replay.verify(race, puzzle, ok, 10_000).accepted()).isTrue();

        List<GameAction> tooFast = new ArrayList<>();
        for (int i = 0; i < taps.size(); i++) {
            tooFast.add(new GameAction("tap", taps.get(i), null, 10L * i));
        }
        assertThat(Replay.verify(race, puzzle, tooFast, 10_000).reason()).contains("faster than humanly plausible");
        assertThat(Replay.verify(race, puzzle, ok, 500).reason()).contains("exceeds the time since the game started");
        assertThat(Replay.verify(race, puzzle, ok.subList(0, 5), 10_000).reason()).isEqualTo("game not finished");
        List<GameAction> extra = new ArrayList<>(ok);
        extra.add(new GameAction("tap", "3", null, 9000));
        assertThat(Replay.verify(race, puzzle, extra, 20_000).reason()).isEqualTo("actions after the game ended");
        List<GameAction> backwards = new ArrayList<>(ok);
        backwards.set(3, new GameAction("tap", "4", null, 0));
        assertThat(Replay.verify(race, puzzle, backwards, 10_000).reason()).isEqualTo("timestamps go backwards");
        assertThat(Replay.verify(race, puzzle, List.of(), 10_000).accepted()).isFalse();
    }

    @Test
    void configsAreValidated() {
        assertThat(TraversalRace.validate(raceConfig())).isEmpty();
        assertThat(TraversalRace.validate(new TraversalRace.Config(List.of("sideways"), 0, 2, 20, Map.of()))).hasSize(4);
        assertThat(MatchPairs.validate(new MatchPairs.Config(List.of(new MatchPairs.Pair("A", "1", "s")), 3))).isNotEmpty();
        assertThat(BugHunt.validate(new BugHunt.Config(1, List.of(
                new BugHunt.Snippet("X", "java", "a\nb", 5, "typo", null, ""))))).hasSize(3);
    }

    @Test
    void splitMixIsStableAcrossVersions() {
        // Pinned outputs: changing the generator would silently change every stored game's puzzle.
        SplitMix64 r = new SplitMix64(0L);
        assertThat(r.nextLong()).isEqualTo(0xE220A8397B1DCDAFL);
        assertThat(r.nextLong()).isEqualTo(0x6E789E6AA1B965F4L);
    }
}
