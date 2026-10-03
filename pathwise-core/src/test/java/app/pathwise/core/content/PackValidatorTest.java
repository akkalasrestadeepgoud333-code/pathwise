package app.pathwise.core.content;

import app.pathwise.core.content.ContentPack.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PackValidatorTest {

    private static Catalog catalog() {
        return new Catalog(new NodeDef("ug", "Undergraduate", null), new NodeDef("btech", "B.Tech", null),
                new NodeDef("core", "Core CS", null), new NodeDef("ds", "Data Structures", null));
    }

    private static TopicDef topic(String key, String parent, PrereqDef... prereqs) {
        return new TopicDef(key, "Topic " + key, null, parent, null, null,
                List.of(new SkillDef(key + "-skill", "Skill for " + key, null, null)), List.of(prereqs));
    }

    private static SectionDef text(String kind, String md) {
        return new SectionDef(null, kind, "A section", null, md, null, null, null, null);
    }

    private static ModuleDef module(String key, String topic, SectionDef... sections) {
        return new ModuleDef(key, topic, "Module " + key, null, List.of("Do a thing"), 10, 2,
                List.of(topic + "-skill"), List.of(sections));
    }

    private static ContentPack pack(List<TopicDef> topics, List<ModuleDef> modules) {
        return new ContentPack("ds-pack", 1, catalog(), topics, modules);
    }

    @Test
    void aWellFormedPackIsValid() {
        ContentPack p = pack(
                List.of(topic("arrays", null), topic("trees", null, new PrereqDef("arrays", null)),
                        topic("bst", "trees", new PrereqDef("trees", "recommended"))),
                List.of(module("arrays-intro", "arrays", text("overview", "# Hi"), text("explanation", "Text"),
                        new SectionDef(null, "visual", "Try it", null, null,
                                Map.of("type", "traversal", "tree", List.of(1, 2, 3), "orders", List.of("preorder")),
                                "A three-node tree with root 1", null, null))));
        assertThat(PackValidator.validate(p)).isEmpty();
    }

    @Test
    void reportsCyclesUnknownReferencesAndDuplicates() {
        ContentPack p = pack(
                List.of(topic("a", null, new PrereqDef("b", null)), topic("b", null, new PrereqDef("a", null)),
                        topic("c", "missing", new PrereqDef("ghost", "sometimes")), topic("c", null)),
                List.of(module("m", "nowhere", text("overview", "x")), module("m", "a")));
        List<String> errors = PackValidator.validate(p);
        assertThat(errors).anyMatch(e -> e.startsWith("prerequisite cycle"));
        assertThat(errors).anyMatch(e -> e.contains("unknown parent missing"));
        assertThat(errors).anyMatch(e -> e.contains("unknown prerequisite ghost"));
        assertThat(errors).anyMatch(e -> e.contains("strength must be required or recommended"));
        assertThat(errors).anyMatch(e -> e.contains("duplicate topic key c"));
        assertThat(errors).anyMatch(e -> e.contains("duplicate module key m"));
        assertThat(errors).anyMatch(e -> e.contains("unknown topic nowhere"));
        assertThat(errors).anyMatch(e -> e.contains("has no sections"));
    }

    @Test
    void validatesSectionsAndVisuals() {
        Map<String, Object> badTree = new HashMap<>();
        badTree.put("type", "traversal");
        List<Object> tree = new ArrayList<>();
        tree.add(1);
        tree.add(null);
        tree.add(3);
        tree.add(4); // child of the missing left node
        badTree.put("tree", tree);
        badTree.put("orders", List.of("sideways"));
        ContentPack p = pack(List.of(topic("t", null)), List.of(module("m", "t",
                text("overview", ""),
                new SectionDef(null, "visual", "V", null, null, badTree, "short", null, null),
                new SectionDef(null, "flashcards", "F", null, null, null, null, List.of(), null),
                new SectionDef(null, "resources", "R", null, null, null, null, null,
                        List.of(new LinkDef("Insecure link", "http://example.com", null, null))),
                new SectionDef(null, "quiz", "Q", null, null, null, null, null, null))));
        List<String> errors = PackValidator.validate(p);
        assertThat(errors).anyMatch(e -> e.contains("overview markdown"));
        assertThat(errors).anyMatch(e -> e.contains("slot 3 has no parent"));
        assertThat(errors).anyMatch(e -> e.contains("visual.orders"));
        assertThat(errors).anyMatch(e -> e.contains("alt"));
        assertThat(errors).anyMatch(e -> e.contains("needs cards"));
        assertThat(errors).anyMatch(e -> e.contains("https://"));
        assertThat(errors).anyMatch(e -> e.contains("unknown section kind quiz"));
    }

    @Test
    void sectionKeysAreDerivedStablyFromKindAndOccurrence() {
        Map<String, Integer> counts = new HashMap<>();
        assertThat(PackValidator.sectionKey(text("explanation", "x"), counts)).isEqualTo("explanation");
        assertThat(PackValidator.sectionKey(text("explanation", "x"), counts)).isEqualTo("explanation-2");
        assertThat(PackValidator.sectionKey(text("common_doubts", "x"), counts)).isEqualTo("common-doubts");
        assertThat(PackValidator.sectionKey(new SectionDef("custom", "overview", "t", null, "x", null, null, null, null), counts))
                .isEqualTo("custom");
    }

    @Test
    void visualSpecsForEveryType() {
        assertThat(Visuals.validate(Map.of("type", "bst-insert", "values", List.of(8, 3, 10)))).isEmpty();
        assertThat(Visuals.validate(Map.of("type", "bst-insert", "values", List.of("a")))).isNotEmpty();
        assertThat(Visuals.validate(Map.of("type", "stack-queue", "mode", "stack", "ops", List.of("push 3", "pop", "peek")))).isEmpty();
        assertThat(Visuals.validate(Map.of("type", "stack-queue", "mode", "deque", "ops", List.of("shove")))).hasSize(2);
        assertThat(Visuals.validate(Map.of("type", "graph-search", "nodes", List.of("A", "B", "C"),
                "edges", List.of(List.of("A", "B"), List.of("B", "C")), "start", "A"))).isEmpty();
        assertThat(Visuals.validate(Map.of("type", "graph-search", "nodes", List.of("A", "B"),
                "edges", List.of(List.of("A", "Z")), "start", "Q"))).hasSize(2);
        assertThat(Visuals.validate(Map.of("type", "hash-table", "buckets", 7, "keys", List.of(15, 22, 8)))).isEmpty();
        assertThat(Visuals.validate(Map.of("type", "hash-table", "buckets", 99, "keys", List.of()))).hasSize(2);
        assertThat(Visuals.validate(Map.of("type", "array", "values", List.of(1, 2)))).isEmpty();
        assertThat(Visuals.validate(Map.of("type", "rocket"))).isNotEmpty();
    }

    @Test
    void coverageIsNotMastery() {
        assertThat(Coverage.of(0, 0, 0)).isEqualTo(Coverage.NO_CONTENT);
        assertThat(Coverage.of(2, 0, 0)).isEqualTo(Coverage.NOT_STARTED);
        assertThat(Coverage.of(2, 1, 0)).isEqualTo(Coverage.IN_PROGRESS);
        assertThat(Coverage.of(2, 2, 2)).isEqualTo(Coverage.STUDIED);
        assertThat(Coverage.STUDIED.label()).isEqualTo("Studied");
    }
}
