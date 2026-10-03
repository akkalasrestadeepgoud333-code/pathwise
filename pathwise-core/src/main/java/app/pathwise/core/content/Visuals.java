package app.pathwise.core.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Declarative interactive visuals rendered in the browser by {@code static/js/visuals}. The server
 * validates every spec so a content mistake fails at import time, not in front of a student.
 */
public final class Visuals {
    public static final Set<String> TYPES = Set.of(
            "tree", "traversal", "bst-insert", "array", "linked-list", "stack-queue", "graph-search", "hash-table");

    private static final Set<String> ORDERS = Set.of("preorder", "inorder", "postorder", "level-order");

    private Visuals() {}

    /** Returns problems found in a visual spec (empty when valid). */
    public static List<String> validate(Map<String, Object> spec) {
        List<String> errors = new ArrayList<>();
        if (spec == null) {
            errors.add("visual spec is missing");
            return errors;
        }
        Object type = spec.get("type");
        if (!(type instanceof String t) || !TYPES.contains(t)) {
            errors.add("visual.type must be one of " + TYPES);
            return errors;
        }
        switch (t) {
            case "tree", "traversal" -> {
                List<?> tree = list(spec, "tree", errors);
                if (tree != null) {
                    if (tree.isEmpty() || tree.get(0) == null) {
                        errors.add("visual.tree must start with a root value");
                    }
                    if (tree.size() > 31) {
                        errors.add("visual.tree supports at most 31 slots (5 levels)");
                    }
                    for (int i = 1; i < tree.size(); i++) {
                        if (tree.get(i) != null && tree.get((i - 1) / 2) == null) {
                            errors.add("visual.tree slot " + i + " has no parent");
                        }
                    }
                }
                if ("traversal".equals(t)) {
                    List<?> orders = list(spec, "orders", errors);
                    if (orders != null && (orders.isEmpty() || !ORDERS.containsAll(orders))) {
                        errors.add("visual.orders must be a non-empty subset of " + ORDERS);
                    }
                }
            }
            case "bst-insert" -> {
                List<?> values = list(spec, "values", errors);
                if (values != null && (values.isEmpty() || values.size() > 15 || !values.stream().allMatch(v -> v instanceof Integer))) {
                    errors.add("visual.values must be 1-15 whole numbers");
                }
            }
            case "array", "linked-list" -> {
                List<?> values = list(spec, "values", errors);
                if (values != null && (values.isEmpty() || values.size() > 12)) {
                    errors.add("visual.values must have 1-12 items");
                }
            }
            case "stack-queue" -> {
                Object mode = spec.get("mode");
                if (!"stack".equals(mode) && !"queue".equals(mode)) {
                    errors.add("visual.mode must be stack or queue");
                }
                List<?> ops = list(spec, "ops", errors);
                if (ops != null) {
                    for (Object op : ops) {
                        if (!(op instanceof String s) || !s.matches("(push|pop|enqueue|dequeue|peek)( \\S{1,6})?")) {
                            errors.add("visual.ops entries look like 'push 4', 'pop', 'enqueue 7', 'dequeue', 'peek'");
                            break;
                        }
                    }
                }
            }
            case "graph-search" -> {
                List<?> nodes = list(spec, "nodes", errors);
                List<?> edges = list(spec, "edges", errors);
                Object start = spec.get("start");
                if (nodes != null && (nodes.size() < 2 || nodes.size() > 12)) {
                    errors.add("visual.nodes must have 2-12 nodes");
                }
                if (nodes != null && !nodes.contains(start)) {
                    errors.add("visual.start must be one of the nodes");
                }
                if (edges != null && nodes != null) {
                    for (Object e : edges) {
                        if (!(e instanceof List<?> pair) || pair.size() != 2 || !nodes.contains(pair.get(0)) || !nodes.contains(pair.get(1))) {
                            errors.add("visual.edges must be pairs of node names");
                            break;
                        }
                    }
                }
            }
            case "hash-table" -> {
                Object size = spec.get("buckets");
                if (!(size instanceof Integer n) || n < 3 || n > 13) {
                    errors.add("visual.buckets must be a whole number from 3 to 13");
                }
                List<?> keys = list(spec, "keys", errors);
                if (keys != null && (keys.isEmpty() || keys.size() > 16 || !keys.stream().allMatch(k -> k instanceof Integer))) {
                    errors.add("visual.keys must be 1-16 whole numbers");
                }
            }
            default -> errors.add("unhandled visual type " + t);
        }
        return errors;
    }

    private static List<?> list(Map<String, Object> spec, String field, List<String> errors) {
        Object v = spec.get(field);
        if (v instanceof List<?> l) {
            return l;
        }
        errors.add("visual." + field + " must be a list");
        return null;
    }
}
