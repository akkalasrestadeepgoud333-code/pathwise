package app.pathwise.core.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Top-down layered (Sugiyama-style) layout of a prerequisite DAG for the Learning Map. It is computed on
 * the server, so the map renders as plain SVG with no client-side graph library.
 *
 * <ol>
 *   <li>Layers by longest path: every prerequisite sits above whatever depends on it.</li>
 *   <li>Edges spanning several layers get invisible waypoints ("dummy nodes") in each layer they
 *       cross. Ordering then makes room for them, so long edges route between cards, not behind them.</li>
 *   <li>Order within each layer by repeated barycentre sweeps, which reduces crossings.</li>
 *   <li>Each layer is centred horizontally. Edges are smooth vertical curves through their waypoints.</li>
 * </ol>
 */
public final class MapLayout {
    public static final int NODE_WIDTH = 208;
    public static final int NODE_HEIGHT = 72;
    private static final int DUMMY_WIDTH = 16;
    private static final int GAP_X = 32;
    private static final int GAP_Y = 64;
    private static final int PAD = 28;
    private static final int SWEEPS = 8;

    private MapLayout() {}

    public record Node(String key, int layer, int order, int x, int y) {
        public int centerX() {
            return x + NODE_WIDTH / 2;
        }
    }

    /** {@code path} is an SVG path from the prerequisite's bottom edge to the dependent's top edge. */
    public record EdgePath(String from, String to, String path, boolean required) {}

    public record Result(List<Node> nodes, List<EdgePath> edges, int width, int height, int layerCount) {
        public Node node(String key) {
            return nodes.stream().filter(n -> n.key().equals(key)).findFirst().orElseThrow();
        }
    }

    /** An item in a layer: a real topic, or a waypoint of a long edge. */
    private record Item(String id, boolean dummy) {}

    /**
     * @param keys         node keys in their preferred (authoring) order
     * @param edges        prerequisite edges, {@code from → to}
     * @param requiredness whether each edge is required (solid) or recommended (dashed), keyed "from>to"
     */
    public static Result compute(List<String> keys, List<Graphs.Edge<String>> edges, Map<String, Boolean> requiredness) {
        Map<String, Integer> layerOf = new HashMap<>(Graphs.layers(keys, edges));
        Map<String, Integer> authorRank = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            authorRank.put(keys.get(i), i);
        }

        TreeMap<Integer, List<Item>> layers = new TreeMap<>();
        for (String k : keys) {
            layers.computeIfAbsent(layerOf.get(k), l -> new ArrayList<>()).add(new Item(k, false));
        }

        // Split long edges into unit-length segments through dummy waypoints.
        Map<String, List<String>> preds = new HashMap<>();
        Map<String, List<String>> succs = new HashMap<>();
        Map<Graphs.Edge<String>, List<String>> chains = new LinkedHashMap<>();
        int dummyCount = 0;
        for (Graphs.Edge<String> e : edges) {
            Integer from = layerOf.get(e.from());
            Integer to = layerOf.get(e.to());
            if (from == null || to == null) {
                continue;
            }
            List<String> chain = new ArrayList<>();
            chain.add(e.from());
            for (int l = from + 1; l < to; l++) {
                String dummy = "\u0000dummy" + (dummyCount++);
                layerOf.put(dummy, l);
                authorRank.put(dummy, authorRank.get(e.from()));
                layers.get(l).add(new Item(dummy, true));
                chain.add(dummy);
            }
            chain.add(e.to());
            for (int i = 0; i + 1 < chain.size(); i++) {
                succs.computeIfAbsent(chain.get(i), k -> new ArrayList<>()).add(chain.get(i + 1));
                preds.computeIfAbsent(chain.get(i + 1), k -> new ArrayList<>()).add(chain.get(i));
            }
            chains.put(e, chain);
        }

        Map<String, Double> position = new HashMap<>();
        layers.values().forEach(layer -> reindex(layer, position));
        for (int sweep = 0; sweep < SWEEPS; sweep++) {
            boolean down = sweep % 2 == 0;
            List<Integer> order = new ArrayList<>(layers.keySet());
            if (!down) {
                order = order.reversed();
            }
            for (int l : order) {
                List<Item> layer = layers.get(l);
                Map<String, List<String>> neighbours = down ? preds : succs;
                Map<String, Double> bary = new HashMap<>();
                for (Item it : layer) {
                    List<String> ns = neighbours.getOrDefault(it.id(), List.of());
                    bary.put(it.id(), ns.isEmpty() ? position.get(it.id())
                            : ns.stream().mapToDouble(position::get).average().orElse(position.get(it.id())));
                }
                layer.sort(Comparator.comparingDouble((Item it) -> bary.get(it.id()))
                        .thenComparingInt(it -> authorRank.get(it.id())));
                reindex(layer, position);
            }
        }

        int widest = layers.values().stream().mapToInt(MapLayout::layerWidth).max().orElse(0);
        Map<String, int[]> anchor = new HashMap<>(); // id → {centreX, topY}
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Item>> entry : layers.entrySet()) {
            List<Item> layer = entry.getValue();
            int x = PAD + (widest - layerWidth(layer)) / 2;
            int y = PAD + entry.getKey() * (NODE_HEIGHT + GAP_Y);
            int realOrder = 0;
            for (Item it : layer) {
                int w = it.dummy() ? DUMMY_WIDTH : NODE_WIDTH;
                anchor.put(it.id(), new int[]{x + w / 2, y});
                if (!it.dummy()) {
                    nodes.put(it.id(), new Node(it.id(), entry.getKey(), realOrder++, x, y));
                }
                x += w + GAP_X;
            }
        }

        // Spread connection points along each card's bottom (outgoing) and top (incoming) edge, ordered
        // by where the neighbour sits, so lines fan out instead of all leaving from one point.
        Map<Graphs.Edge<String>, Integer> outPort = new HashMap<>();
        Map<Graphs.Edge<String>, Integer> inPort = new HashMap<>();
        Map<String, List<Graphs.Edge<String>>> outgoing = new HashMap<>();
        Map<String, List<Graphs.Edge<String>>> incoming = new HashMap<>();
        chains.keySet().forEach(e -> {
            outgoing.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e);
            incoming.computeIfAbsent(e.to(), k -> new ArrayList<>()).add(e);
        });
        outgoing.forEach((node, list) -> {
            list.sort(Comparator.comparingInt(e -> anchor.get(chains.get(e).get(1))[0]));
            assignPorts(list, nodes.get(node).x(), outPort);
        });
        incoming.forEach((node, list) -> {
            list.sort(Comparator.comparingInt(e -> anchor.get(chains.get(e).get(chains.get(e).size() - 2))[0]));
            assignPorts(list, nodes.get(node).x(), inPort);
        });

        List<EdgePath> paths = new ArrayList<>();
        for (Map.Entry<Graphs.Edge<String>, List<String>> entry : chains.entrySet()) {
            List<String> chain = entry.getValue();
            StringBuilder d = new StringBuilder();
            int[] start = anchor.get(chain.get(0));
            int px = outPort.get(entry.getKey());
            int py = start[1] + NODE_HEIGHT;
            d.append(String.format(Locale.ROOT, "M%d %d", px, py));
            for (int i = 1; i < chain.size(); i++) {
                int[] a = anchor.get(chain.get(i));
                boolean last = i == chain.size() - 1;
                int tx = last ? inPort.get(entry.getKey()) : a[0];
                int ty = a[1]; // top of the target card, or of the waypoint's slot
                int my = (py + ty) / 2;
                d.append(String.format(Locale.ROOT, " C%d %d, %d %d, %d %d", px, my, tx, my, tx, ty));
                px = tx;
                py = ty;
                if (!last) {
                    // Pass straight down through the waypoint's slot.
                    py = a[1] + NODE_HEIGHT;
                    d.append(String.format(Locale.ROOT, " L%d %d", px, py));
                }
            }
            Graphs.Edge<String> e = entry.getKey();
            paths.add(new EdgePath(e.from(), e.to(), d.toString(), requiredness.getOrDefault(e.from() + ">" + e.to(), true)));
        }

        int layerCount = layers.isEmpty() ? 0 : layers.lastKey() + 1;
        int width = PAD * 2 + widest;
        int height = layerCount == 0 ? PAD * 2 : PAD * 2 + layerCount * NODE_HEIGHT + (layerCount - 1) * GAP_Y;
        List<Node> ordered = new ArrayList<>(nodes.values());
        ordered.sort(Comparator.comparingInt(Node::layer).thenComparingInt(Node::order));
        return new Result(List.copyOf(ordered), List.copyOf(paths), width, height, layerCount);
    }

    /** Evenly spaced ports across the middle 60% of a card. */
    private static void assignPorts(List<Graphs.Edge<String>> edges, int cardX, Map<Graphs.Edge<String>, Integer> ports) {
        double span = NODE_WIDTH * 0.6;
        double left = cardX + NODE_WIDTH * 0.2;
        for (int i = 0; i < edges.size(); i++) {
            ports.put(edges.get(i), (int) Math.round(left + span * (i + 1) / (edges.size() + 1)));
        }
    }

    private static int layerWidth(List<Item> layer) {
        int w = 0;
        for (Item it : layer) {
            w += it.dummy() ? DUMMY_WIDTH : NODE_WIDTH;
        }
        return w + Math.max(0, layer.size() - 1) * GAP_X;
    }

    private static void reindex(List<Item> layer, Map<String, Double> position) {
        for (int i = 0; i < layer.size(); i++) {
            position.put(layer.get(i).id(), (double) i);
        }
    }
}
