package app.pathwise.core.catalog;

import app.pathwise.core.catalog.Graphs.Edge;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphsAndLayoutTest {

    private static Edge<String> e(String from, String to) {
        return new Edge<>(from, to);
    }

    @Test
    void detectsCyclesAndReportsThePath() {
        List<String> nodes = List.of("a", "b", "c", "d");
        assertThat(Graphs.findCycle(nodes, List.of(e("a", "b"), e("b", "c"), e("a", "c")))).isEmpty();
        assertThat(Graphs.findCycle(nodes, List.of(e("a", "b"), e("b", "c"), e("c", "a"))))
                .hasValueSatisfying(c -> assertThat(c).containsExactly("a", "b", "c", "a"));
        assertThat(Graphs.findCycle(nodes, List.of(e("d", "d")))).isPresent();
    }

    @Test
    void layersUseTheLongestPath() {
        // arrays → lists → trees, and arrays → trees directly: trees must still come after lists.
        Map<String, Integer> layers = Graphs.layers(List.of("arrays", "lists", "trees", "hashing"),
                List.of(e("arrays", "lists"), e("lists", "trees"), e("arrays", "trees"), e("arrays", "hashing")));
        assertThat(layers).containsEntry("arrays", 0).containsEntry("lists", 1)
                .containsEntry("trees", 2).containsEntry("hashing", 1);
        assertThatThrownBy(() -> Graphs.layers(List.of("x", "y"), List.of(e("x", "y"), e("y", "x"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void descendantsFollowEdgesTransitively() {
        assertThat(Graphs.descendants("a", List.of(e("a", "b"), e("b", "c"), e("x", "y"))))
                .containsExactlyInAnyOrder("b", "c");
    }

    @Test
    void layoutPlacesPrerequisitesAboveDependentsWithoutOverlap() {
        List<String> keys = List.of("arrays", "lists", "stacks", "recursion", "trees", "hashing");
        List<Edge<String>> edges = List.of(e("arrays", "lists"), e("arrays", "stacks"), e("lists", "trees"),
                e("recursion", "trees"), e("arrays", "hashing"));
        MapLayout.Result r = MapLayout.compute(keys, edges, Map.of("arrays>stacks", false));

        assertThat(r.nodes()).hasSize(6);
        for (Edge<String> edge : edges) {
            assertThat(r.node(edge.from()).y()).isLessThan(r.node(edge.to()).y());
        }
        for (MapLayout.Node a : r.nodes()) {
            for (MapLayout.Node b : r.nodes()) {
                if (a != b && a.layer() == b.layer()) {
                    assertThat(Math.abs(a.x() - b.x())).isGreaterThanOrEqualTo(MapLayout.NODE_WIDTH);
                }
            }
            assertThat(a.x() + MapLayout.NODE_WIDTH).isLessThanOrEqualTo(r.width());
            assertThat(a.y() + MapLayout.NODE_HEIGHT).isLessThanOrEqualTo(r.height());
        }
        assertThat(r.layerCount()).isEqualTo(3);
        assertThat(r.edges()).hasSize(5);
        assertThat(r.edges()).filteredOn(p -> !p.required()).extracting(MapLayout.EdgePath::to).containsExactly("stacks");
        assertThat(r.edges().get(0).path()).matches("M\\d+ \\d+( C\\d+ \\d+, \\d+ \\d+, \\d+ \\d+( L\\d+ \\d+)?)+");
    }

    @Test
    void barycentreOrderingAvoidsAnObviousCrossing() {
        // b1 depends on a2, b2 on a1: after sweeps, b-order should mirror a-order (no crossing).
        MapLayout.Result r = MapLayout.compute(List.of("a1", "a2", "b1", "b2"),
                List.of(e("a2", "b1"), e("a1", "b2")), Map.of());
        boolean aOrder = r.node("a1").x() < r.node("a2").x();
        boolean bOrder = r.node("b2").x() < r.node("b1").x();
        assertThat(aOrder).isEqualTo(bOrder);
    }

    @Test
    void longEdgesGetWaypointsSoTheyRouteBetweenCards() {
        // a → b → c → d plus a long edge a → d: the long edge must pass through a slot in layers 1 and 2.
        MapLayout.Result r = MapLayout.compute(List.of("a", "b", "c", "d"),
                List.of(e("a", "b"), e("b", "c"), e("c", "d"), e("a", "d")), Map.of());
        String longPath = r.edges().stream().filter(p -> p.from().equals("a") && p.to().equals("d")).findFirst().orElseThrow().path();
        assertThat(longPath.split(" L")).hasSize(3); // two straight runs through two waypoint slots
        // Layers with a waypoint are wider than one card, so the waypoint never sits on top of "b" or "c".
        assertThat(r.width()).isGreaterThan(MapLayout.NODE_WIDTH + 2 * 28);
    }

    @Test
    void emptyGraphHasNoLayers() {
        MapLayout.Result r = MapLayout.compute(List.of(), List.of(), Map.of());
        assertThat(r.nodes()).isEmpty();
        assertThat(r.layerCount()).isZero();
    }
}
