package app.pathwise.core.catalog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Directed-graph utilities for the topic prerequisite DAG. Edges point from prerequisite to dependent. */
public final class Graphs {
    private Graphs() {}

    /** A directed edge {@code from → to}: "from" must be learned before "to". */
    public record Edge<T>(T from, T to) {}

    /** Returns one cycle (as a closed path, first element repeated at the end) if the graph has any. */
    public static <T> Optional<List<T>> findCycle(Collection<T> nodes, Collection<Edge<T>> edges) {
        Map<T, List<T>> out = adjacency(nodes, edges);
        Map<T, Integer> state = new HashMap<>(); // 0 = unvisited, 1 = on stack, 2 = done
        Deque<T> path = new ArrayDeque<>();
        for (T start : out.keySet()) {
            if (state.getOrDefault(start, 0) != 0) {
                continue;
            }
            List<T> cycle = dfs(start, out, state, path);
            if (cycle != null) {
                return Optional.of(cycle);
            }
        }
        return Optional.empty();
    }

    private static <T> List<T> dfs(T node, Map<T, List<T>> out, Map<T, Integer> state, Deque<T> path) {
        state.put(node, 1);
        path.addLast(node);
        for (T next : out.getOrDefault(node, List.of())) {
            int s = state.getOrDefault(next, 0);
            if (s == 1) {
                List<T> cycle = new ArrayList<>();
                boolean on = false;
                for (T p : path) {
                    if (p.equals(next)) {
                        on = true;
                    }
                    if (on) {
                        cycle.add(p);
                    }
                }
                cycle.add(next);
                return cycle;
            }
            if (s == 0) {
                List<T> found = dfs(next, out, state, path);
                if (found != null) {
                    return found;
                }
            }
        }
        path.removeLast();
        state.put(node, 2);
        return null;
    }

    /**
     * Longest-path layering: nodes without prerequisites are layer 0; every other node sits one layer
     * after its deepest prerequisite. Requires an acyclic graph.
     */
    public static <T> Map<T, Integer> layers(Collection<T> nodes, Collection<Edge<T>> edges) {
        Map<T, List<T>> in = new LinkedHashMap<>();
        for (T n : nodes) {
            in.put(n, new ArrayList<>());
        }
        for (Edge<T> e : edges) {
            if (in.containsKey(e.to()) && in.containsKey(e.from())) {
                in.get(e.to()).add(e.from());
            }
        }
        Map<T, Integer> layer = new LinkedHashMap<>();
        for (T n : nodes) {
            layerOf(n, in, layer, new LinkedHashSet<>());
        }
        return layer;
    }

    private static <T> int layerOf(T n, Map<T, List<T>> in, Map<T, Integer> memo, Set<T> visiting) {
        Integer known = memo.get(n);
        if (known != null) {
            return known;
        }
        if (!visiting.add(n)) {
            throw new IllegalArgumentException("Graph has a cycle through " + n);
        }
        int l = 0;
        for (T p : in.get(n)) {
            l = Math.max(l, layerOf(p, in, memo, visiting) + 1);
        }
        visiting.remove(n);
        memo.put(n, l);
        return l;
    }

    /** All nodes that (transitively) depend on {@code node}. */
    public static <T> Set<T> descendants(T node, Collection<Edge<T>> edges) {
        Map<T, List<T>> out = new HashMap<>();
        for (Edge<T> e : edges) {
            out.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
        }
        Set<T> seen = new LinkedHashSet<>();
        Deque<T> todo = new ArrayDeque<>(out.getOrDefault(node, List.of()));
        while (!todo.isEmpty()) {
            T n = todo.poll();
            if (seen.add(n)) {
                todo.addAll(out.getOrDefault(n, List.of()));
            }
        }
        return Collections.unmodifiableSet(seen);
    }

    private static <T> Map<T, List<T>> adjacency(Collection<T> nodes, Collection<Edge<T>> edges) {
        Map<T, List<T>> out = new LinkedHashMap<>();
        for (T n : nodes) {
            out.put(n, new ArrayList<>());
        }
        for (Edge<T> e : edges) {
            out.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
            out.computeIfAbsent(e.to(), k -> new ArrayList<>());
        }
        return out;
    }
}
