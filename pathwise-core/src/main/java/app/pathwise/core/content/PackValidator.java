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

        Set<String> questionKeys = validateQuestions(pack.questionsOrEmpty(), skillKeys, errors);
        Set<String> assessmentOnly = new HashSet<>();
        pack.questionsOrEmpty().stream().filter(q -> !q.practiceEnabled()).forEach(q -> assessmentOnly.add(q.key()));

        validateAssessments(pack, byKey.keySet(), questionKeys, errors);

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
                validateSection(where + " / " + sectionKey, kind.get(), s, skillKeys, questionKeys, errors);
                if (kind.get() == SectionKind.CHECK) {
                    // Module checks reveal answers, so they must never use assessment-only items.
                    s.questionsOrEmpty().stream().filter(assessmentOnly::contains)
                            .forEach(q -> errors.add(where + " / " + sectionKey + ": " + q + " is assessment-only (practice: false) and can't be used in a module check"));
                }
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

    private static final Set<String> ASSESSMENT_KINDS = Set.of("diagnostic", "quiz", "topic_test", "practice_test", "challenge");
    private static final Set<String> AI_POLICIES = Set.of("blocked", "hints_only", "allowed");

    private static void validateAssessments(ContentPack pack, Set<String> topics, Set<String> questions, List<String> errors) {
        Set<String> keys = new HashSet<>();
        for (ContentPack.AssessmentDef a : pack.assessmentsOrEmpty()) {
            String where = "assessment " + a.key();
            key(a.key(), "assessment key", errors);
            if (!keys.add(a.key())) {
                errors.add("duplicate assessment key " + a.key());
            }
            if (a.kind() == null || !ASSESSMENT_KINDS.contains(a.kind())) {
                errors.add(where + ": kind must be one of " + ASSESSMENT_KINDS);
            }
            text(a.title(), where + " title", 2, 160, errors);
            if (a.topic() != null && !topics.contains(a.topic())) {
                errors.add(where + " refers to unknown topic " + a.topic());
            }
            if (a.timeLimitMinutes() != null && (a.timeLimitMinutes() < 1 || a.timeLimitMinutes() > 240)) {
                errors.add(where + ": timeLimitMinutes must be 1-240");
            }
            if (a.maxAttempts() != null && (a.maxAttempts() < 1 || a.maxAttempts() > 20)) {
                errors.add(where + ": maxAttempts must be 1-20");
            }
            if (a.aiPolicy() != null && !AI_POLICIES.contains(a.aiPolicy())) {
                errors.add(where + ": aiPolicy must be one of " + AI_POLICIES);
            }
            if (a.feedback() != null && !"immediate".equals(a.feedback()) && !"manual_release".equals(a.feedback())) {
                errors.add(where + ": packs support feedback immediate or manual_release (after_due needs a due date, set by a teacher)");
            }
            List<String> qs = a.questionsOrEmpty();
            if (qs.isEmpty() || qs.size() > 50) {
                errors.add(where + " needs 1-50 questions");
            }
            if (new HashSet<>(qs).size() != qs.size()) {
                errors.add(where + " lists a question twice");
            }
            for (String q : qs) {
                if (!questions.contains(q)) {
                    errors.add(where + " uses unknown question " + q);
                }
            }
        }
    }

    private static final Set<String> QUESTION_TYPES = Set.of("single_choice", "multi_choice", "numeric", "short_text");

    private static Set<String> validateQuestions(List<ContentPack.QuestionDef> questions, Set<String> skills, List<String> errors) {
        Set<String> keys = new HashSet<>();
        for (ContentPack.QuestionDef q : questions) {
            String where = "question " + q.key();
            key(q.key(), "question key", errors);
            if (!keys.add(q.key())) {
                errors.add("duplicate question key " + q.key());
            }
            if (q.type() == null || !QUESTION_TYPES.contains(q.type())) {
                errors.add(where + ": type must be one of " + QUESTION_TYPES);
                continue;
            }
            text(q.stem(), where + " stem", 3, 4000, errors);
            if (q.difficulty() == null || q.difficulty() < 1 || q.difficulty() > 5) {
                errors.add(where + ": difficulty must be 1-5");
            }
            if (q.skillsOrEmpty().isEmpty()) {
                errors.add(where + " must assess at least one skill");
            }
            for (String s : q.skillsOrEmpty()) {
                if (!skills.contains(s)) {
                    errors.add(where + " assesses unknown skill " + s);
                }
            }
            for (String h : q.hintsOrEmpty()) {
                text(h, where + " hint", 3, 1000, errors);
            }
            if (q.hintsOrEmpty().size() > 5) {
                errors.add(where + ": at most 5 hints");
            }
            switch (q.type()) {
                case "single_choice", "multi_choice" -> {
                    List<ContentPack.OptionDef> opts = q.optionsOrEmpty();
                    long correct = opts.stream().filter(ContentPack.OptionDef::isCorrect).count();
                    if (opts.size() < 2 || opts.size() > 8) {
                        errors.add(where + ": needs 2-8 options");
                    }
                    if ("single_choice".equals(q.type()) && correct != 1) {
                        errors.add(where + ": single_choice needs exactly one correct option");
                    }
                    if ("multi_choice".equals(q.type()) && correct < 1) {
                        errors.add(where + ": multi_choice needs at least one correct option");
                    }
                    Set<String> labels = new HashSet<>();
                    for (ContentPack.OptionDef o : opts) {
                        text(o.text(), where + " option", 1, 1000, errors);
                        if (o.text() != null && !labels.add(o.text().strip())) {
                            errors.add(where + ": duplicate option " + o.text());
                        }
                    }
                }
                case "numeric" -> {
                    if (q.answer() == null || !Double.isFinite(q.answer())) {
                        errors.add(where + ": numeric questions need an answer");
                    }
                    if (q.tolerance() != null && q.tolerance() < 0) {
                        errors.add(where + ": tolerance cannot be negative");
                    }
                }
                case "short_text" -> {
                    if (q.accepted() == null || q.accepted().isEmpty() || q.accepted().stream().anyMatch(a -> a == null || a.isBlank())) {
                        errors.add(where + ": short_text questions need accepted answers");
                    }
                }
                default -> { }
            }
        }
        return keys;
    }

    private static void validateSection(String where, SectionKind kind, SectionDef s, Set<String> skills,
                                        Set<String> questions, List<String> errors) {
        if (kind == SectionKind.CHECK) {
            if (s.questionsOrEmpty().isEmpty() || s.questionsOrEmpty().size() > 10) {
                errors.add(where + ": check sections need 1-10 questions");
            }
            for (String q : s.questionsOrEmpty()) {
                if (!questions.contains(q)) {
                    errors.add(where + ": unknown question " + q);
                }
            }
        }
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
