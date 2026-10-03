package app.pathwise.core.content;

import app.pathwise.core.catalog.Graphs;
import app.pathwise.core.content.ContentPack.ModuleDef;
import app.pathwise.core.content.ContentPack.SectionDef;
import app.pathwise.core.content.ContentPack.TopicDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks a content pack before anything is written: keys, references, the prerequisite DAG, section
 * shapes and visual specs. Returns human-readable problems; an empty list means the pack is valid.
 */
public final class PackValidator {
    private static final Pattern KEY = Pattern.compile("^[a-z0-9][a-z0-9-]{1,80}$");
    private static final Pattern SECTION_KEY = Pattern.compile("^[a-z0-9][a-z0-9-]{0,80}$");

    private PackValidator() {}

    public static List<String> validate(ContentPack pack) {
        List<String> errors = new ArrayList<>();
        if (pack == null) {
            return List.of("pack is empty");
        }
        key(pack.key(), "pack.key", errors);
        if (pack.version() < 1) {
            errors.add("pack.version must be 1 or more");
        }
        if (pack.catalog() == null || pack.catalog().subject() == null || pack.catalog().course() == null
                || pack.catalog().program() == null || pack.catalog().level() == null) {
            errors.add("catalog must define level, program, course and subject");
        } else {
            for (var n : List.of(pack.catalog().level(), pack.catalog().program(), pack.catalog().course(), pack.catalog().subject())) {
                key(n.key(), "catalog key", errors);
                text(n.title(), "catalog title for " + n.key(), 2, 120, errors);
            }
        }

        List<TopicDef> topics = pack.topics() == null ? List.of() : pack.topics();
        if (topics.isEmpty()) {
            errors.add("pack has no topics");
        }
        Map<String, TopicDef> byKey = new HashMap<>();
        Set<String> skillKeys = new HashSet<>();
        for (TopicDef t : topics) {
            key(t.key(), "topic key", errors);
            text(t.title(), "title of topic " + t.key(), 2, 120, errors);
            if (byKey.put(t.key(), t) != null) {
                errors.add("duplicate topic key " + t.key());
            }
            Set<String> local = new HashSet<>();
            for (var s : t.skillsOrEmpty()) {
                key(s.key(), "skill key in " + t.key(), errors);
                text(s.title(), "title of skill " + s.key(), 2, 160, errors);
                if (!local.add(s.key())) {
                    errors.add("duplicate skill " + s.key() + " in topic " + t.key());
                }
                if (!skillKeys.add(s.key())) {
                    errors.add("skill key " + s.key() + " is used in more than one topic; skill keys must be unique within a pack");
                }
            }
        }

        List<Graphs.Edge<String>> edges = new ArrayList<>();
        for (TopicDef t : topics) {
            if (t.parent() != null && !byKey.containsKey(t.parent())) {
                errors.add("topic " + t.key() + " has unknown parent " + t.parent());
            }
            if (t.key() != null && t.key().equals(t.parent())) {
                errors.add("topic " + t.key() + " cannot be its own parent");
            }
            for (var p : t.prerequisitesOrEmpty()) {
                if (!byKey.containsKey(p.topic())) {
                    errors.add("topic " + t.key() + " lists unknown prerequisite " + p.topic());
                } else {
                    edges.add(new Graphs.Edge<>(p.topic(), t.key()));
                }
                if (!"required".equals(p.strengthOrDefault()) && !"recommended".equals(p.strengthOrDefault())) {
                    errors.add("prerequisite strength must be required or recommended (topic " + t.key() + ")");
                }
            }
        }
        Optional<List<String>> cycle = Graphs.findCycle(byKey.keySet(), edges);
        cycle.ifPresent(c -> errors.add("prerequisite cycle: " + String.join(" → ", c)));
        List<Graphs.Edge<String>> parentEdges = new ArrayList<>();
        for (TopicDef t : topics) {
            if (t.parent() != null && byKey.containsKey(t.parent())) {
                parentEdges.add(new Graphs.Edge<>(t.parent(), t.key()));
            }
        }
        Graphs.findCycle(byKey.keySet(), parentEdges).ifPresent(c -> errors.add("topic nesting cycle: " + String.join(" → ", c)));

        Set<String> moduleKeys = new HashSet<>();
        for (ModuleDef m : pack.modules() == null ? List.<ModuleDef>of() : pack.modules()) {
            String where = "module " + m.key();
            key(m.key(), "module key", errors);
            if (!moduleKeys.add(m.key())) {
                errors.add("duplicate module key " + m.key());
            }
            if (!byKey.containsKey(m.topic())) {
                errors.add(where + " belongs to unknown topic " + m.topic());
            }
            text(m.title(), "title of " + where, 2, 160, errors);
            if (m.estMinutes() != null && (m.estMinutes() < 1 || m.estMinutes() > 240)) {
                errors.add(where + ": estMinutes must be 1-240");
            }
            if (m.difficulty() != null && (m.difficulty() < 1 || m.difficulty() > 5)) {
                errors.add(where + ": difficulty must be 1-5");
            }
            for (String s : m.skillsOrEmpty()) {
                if (!skillKeys.contains(s)) {
                    errors.add(where + " targets unknown skill " + s);
                }
            }
            if (m.sectionsOrEmpty().isEmpty()) {
                errors.add(where + " has no sections");
            }
            Set<String> sectionKeys = new HashSet<>();
            Map<String, Integer> kindCount = new HashMap<>();
            for (SectionDef s : m.sectionsOrEmpty()) {
                Optional<SectionKind> kind = SectionKind.parse(s.kind());
                if (kind.isEmpty()) {
                    errors.add(where + ": unknown section kind " + s.kind());
                    continue;
                }
                String sectionKey = sectionKey(s, kindCount);
                if (!SECTION_KEY.matcher(sectionKey).matches()) {
                    errors.add(where + ": invalid section key " + sectionKey);
                }
                if (!sectionKeys.add(sectionKey)) {
                    errors.add(where + ": duplicate section key " + sectionKey);
                }
                text(s.title(), where + " section title", 1, 160, errors);
                validateSection(where + " / " + sectionKey, kind.get(), s, skillKeys, errors);
            }
        }
        return errors;
    }

    /** The section's stable key: explicit, or derived from kind and occurrence ("explanation-2"). */
    public static String sectionKey(SectionDef s, Map<String, Integer> kindCount) {
        if (s.key() != null && !s.key().isBlank()) {
            return s.key();
        }
        String kind = SectionKind.parse(s.kind()).map(SectionKind::dbValue).orElse("section").replace('_', '-');
        int n = kindCount.merge(kind, 1, Integer::sum);
        return n == 1 ? kind : kind + "-" + n;
    }

    private static void validateSection(String where, SectionKind kind, SectionDef s, Set<String> skills, List<String> errors) {
        if (kind.isText()) {
            text(s.markdown(), where + " markdown", 1, 20_000, errors);
        }
        switch (kind) {
            case VISUAL -> {
                Visuals.validate(s.visual()).forEach(e -> errors.add(where + ": " + e));
                text(s.alt(), where + " alt (text description for screen readers)", 10, 1000, errors);
            }
            case FLASHCARDS -> {
                if (s.cards() == null || s.cards().isEmpty()) {
                    errors.add(where + ": flashcards section needs cards");
                } else {
                    for (var c : s.cards()) {
                        text(c.front(), where + " card front", 1, 500, errors);
                        text(c.back(), where + " card back", 1, 1000, errors);
                        if (c.skill() != null && !skills.contains(c.skill())) {
                            errors.add(where + ": card refers to unknown skill " + c.skill());
                        }
                    }
                }
            }
            case RESOURCES -> {
                if (s.links() == null || s.links().isEmpty()) {
                    errors.add(where + ": resources section needs links");
                } else {
                    for (var l : s.links()) {
                        text(l.title(), where + " link title", 2, 200, errors);
                        if (l.url() == null || !l.url().startsWith("https://")) {
                            errors.add(where + ": links must use https:// (" + l.url() + ")");
                        }
                        if (l.kind() != null && !l.kind().equals("link") && !l.kind().equals("video_link")) {
                            errors.add(where + ": link kind must be link or video_link");
                        }
                    }
                }
            }
            default -> { }
        }
    }

    private static void key(String value, String what, List<String> errors) {
        if (value == null || !KEY.matcher(value).matches()) {
            errors.add(what + " '" + value + "' must be lowercase letters, digits and hyphens");
        }
    }

    private static void text(String value, String what, int min, int max, List<String> errors) {
        int len = value == null ? 0 : value.strip().length();
        if (len < min || len > max) {
            errors.add(what + " must be " + min + "-" + max + " characters");
        }
    }
}
