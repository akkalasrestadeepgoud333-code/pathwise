package app.pathwise.web.learn;

import app.pathwise.core.catalog.Graphs;
import app.pathwise.core.catalog.MapLayout;
import app.pathwise.core.content.Coverage;
import app.pathwise.core.content.SectionKind;
import app.pathwise.core.learner.MasteryState;
import app.pathwise.data.learner.EvidenceDao.MasteryRow;
import app.pathwise.web.learner.LearnerService;
import app.pathwise.web.questions.QuestionService;
import app.pathwise.core.security.Actor;
import app.pathwise.data.catalog.CatalogDao.PrereqRow;
import app.pathwise.data.catalog.CatalogDao.SubjectRow;
import app.pathwise.data.catalog.CatalogDao.TopicRow;
import app.pathwise.data.content.ContentDao.ModuleRow;
import app.pathwise.data.content.ContentDao.SectionRow;
import app.pathwise.data.content.ProgressDao.ModuleProgress;
import app.pathwise.web.catalog.CatalogService;
import app.pathwise.web.catalog.CatalogService.SubjectGraph;
import app.pathwise.web.content.ContentService;
import app.pathwise.web.content.ContentService.ModuleBundle;
import app.pathwise.web.content.Markdown;
import app.pathwise.web.content.SafeHtml;
import app.pathwise.web.platform.Json;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds the learner-facing views (subjects, Learning Map, topic, module player, Today) from the
 * catalogue and content services. Everything about progress here is coverage, labelled honestly.
 */
public final class LearnService {
    private static final double RING_CIRCUMFERENCE = 2 * Math.PI * 14;

    private final CatalogService catalog;
    private final ContentService content;
    private final LearnerService learner;
    private final QuestionService questions;

    public LearnService(CatalogService catalog, ContentService content, LearnerService learner, QuestionService questions) {
        this.catalog = catalog;
        this.content = content;
        this.learner = learner;
        this.questions = questions;
    }

    /**
     * A topic's mastery summary from its core skills that have evidence: Mastered if all are mastered,
     * Proficient if all are at least proficient, Needs practice if any needs it, otherwise Developing.
     * Null when there is no evidence yet (the map then shows coverage instead).
     */
    static MasteryState topicMastery(List<UUID> coreSkills, Map<UUID, MasteryRow> mastery) {
        List<MasteryState> states = coreSkills.stream().filter(mastery::containsKey)
                .map(s -> MasteryState.fromDb(mastery.get(s).state())).toList();
        if (states.isEmpty() || states.stream().allMatch(s -> s == MasteryState.EXPLORING)) {
            return states.isEmpty() ? null : MasteryState.EXPLORING;
        }
        if (states.size() == coreSkills.size() && states.stream().allMatch(s -> s == MasteryState.MASTERED)) {
            return MasteryState.MASTERED;
        }
        if (states.size() == coreSkills.size() && states.stream().allMatch(s -> s.atLeast(MasteryState.PROFICIENT))) {
            return MasteryState.PROFICIENT;
        }
        if (states.contains(MasteryState.NEEDS_SUPPORT)) {
            return MasteryState.NEEDS_SUPPORT;
        }
        return MasteryState.DEVELOPING;
    }

    // ------------------------------------------------------------------ view models

    public record SubjectCard(String key, String title, String description, String course, int topics, int modules,
                              int modulesDone, int percent) {}

    public record MapNode(String key, String title, String parentTitle, int x, int y, String coverage, String coverageLabel,
                          int modulesDone, int modulesTotal, String ringDash, boolean optional, boolean advanced,
                          boolean current, List<String> buildsOn, int stage, String mastery, String masteryLabel) {}

    public record MapEdge(String path, boolean required, boolean studied) {}

    public record Stage(int number, List<MapNode> nodes) {}

    public record MapView(SubjectCard subject, int width, int height, List<MapNode> nodes, List<MapEdge> edges,
                          List<Stage> stages, String currentTitle) {}

    public record TopicLink(String key, String title, String strength, String coverageLabel, boolean met) {}

    public record SkillItem(UUID id, String title, String description, boolean core, String state, String stateLabel,
                            int evidence, boolean practicable) {}

    public record ModuleItem(String key, String title, String summary, int estMinutes, int difficulty, String state) {}

    public record TopicView(String subjectKey, String subjectTitle, String key, String title, String summary,
                            String parentKey, String parentTitle, boolean optional, boolean advanced, String coverage,
                            String coverageLabel, List<SkillItem> skills, List<TopicLink> prerequisites,
                            List<TopicLink> unlocks, List<TopicLink> subtopics, List<ModuleItem> modules,
                            List<String> buildsOn, String startModuleKey) {}

    public record OutlineItem(String key, String title, String kind, boolean done, boolean current, boolean required) {}

    public record Card(SafeHtml front, SafeHtml back) {}

    public record Link(String title, String url, String kind, String licence) {}

    public record SectionContent(String key, String title, String kind, SafeHtml html, String visualJson, String alt,
                                 List<Card> cards, List<Link> links, List<String> questionKeys, String id) {}

    public record PlayerView(String subjectKey, String subjectTitle, String topicKey, String topicTitle,
                             String moduleKey, String moduleTitle, String moduleSummary, List<String> objectives,
                             int estMinutes, int difficulty, List<OutlineItem> outline, SectionContent section,
                             int index, int total, int doneCount, int percent, String prevKey, String nextKey,
                             boolean moduleCompleted) {}

    public record ContinueCard(String moduleKey, String title, String topicTitle, String subjectTitle, int percent,
                               boolean completed) {}

    public record Suggestion(String moduleKey, String title, String topicTitle, String subjectTitle, String reason) {}

    public record TodayView(ContinueCard continueCard, Suggestion suggestion, List<SubjectCard> subjects) {}

    public record CompleteResult(String nextKey, boolean moduleCompletedNow) {}

    // ------------------------------------------------------------------ subjects

    public List<SubjectCard> subjects(Actor actor) {
        List<SubjectCard> out = new ArrayList<>();
        for (SubjectRow s : catalog.subjects()) {
            catalog.graph(s.key()).ifPresent(g -> out.add(card(actor, g, content.modulesOfSubject(s.id()))));
        }
        return out;
    }

    private SubjectCard card(Actor actor, SubjectGraph g, List<ModuleRow> modules) {
        Map<UUID, ModuleProgress> progress = content.progressInSubject(actor, g.subject().id());
        int done = (int) modules.stream().filter(m -> progress.containsKey(m.id()) && progress.get(m.id()).completed()).count();
        int percent = modules.isEmpty() ? 0 : Math.round(100f * done / modules.size());
        return new SubjectCard(g.subject().key(), g.subject().title(), g.subject().description(), g.subject().courseTitle(),
                g.topics().size(), modules.size(), done, percent);
    }

    // ------------------------------------------------------------------ map

    public Optional<MapView> map(Actor actor, String subjectKey) {
        Optional<SubjectGraph> graph = catalog.graph(subjectKey);
        if (graph.isEmpty()) {
            return Optional.empty();
        }
        SubjectGraph g = graph.get();
        List<ModuleRow> modules = content.modulesOfSubject(g.subject().id());
        Map<UUID, ModuleProgress> progress = content.progressInSubject(actor, g.subject().id());
        Map<UUID, Coverage> coverage = coverage(g, modules, progress);
        Map<UUID, int[]> counts = counts(g, modules, progress);
        UUID current = currentTopic(modules, progress);
        Map<UUID, MasteryRow> mastery = learner.mastery(actor);

        List<String> keys = g.topics().stream().map(TopicRow::key).toList();
        Map<UUID, String> keyOf = g.topics().stream().collect(Collectors.toMap(TopicRow::id, TopicRow::key));
        List<Graphs.Edge<String>> edges = new ArrayList<>();
        Map<String, Boolean> required = new HashMap<>();
        for (PrereqRow p : g.prerequisites()) {
            String from = keyOf.get(p.prerequisiteId());
            String to = keyOf.get(p.topicId());
            edges.add(new Graphs.Edge<>(from, to));
            required.put(from + ">" + to, "required".equals(p.strength()));
        }
        MapLayout.Result layout = MapLayout.compute(keys, edges, required);

        List<MapNode> nodes = new ArrayList<>();
        Map<String, MapNode> byKey = new HashMap<>();
        for (MapLayout.Node n : layout.nodes()) {
            TopicRow t = g.topic(n.key()).orElseThrow();
            Coverage cov = coverage.get(t.id());
            int[] c = counts.get(t.id());
            String dash = String.format(java.util.Locale.ROOT, "%.2f %.2f",
                    c[1] == 0 ? 0 : RING_CIRCUMFERENCE * c[0] / c[1], RING_CIRCUMFERENCE);
            MapNode node = new MapNode(t.key(), t.title(), parentTitle(g, t), n.x(), n.y(), cov.cssClass(), cov.label(),
                    c[0], c[1], dash, t.optional(), t.advanced(), t.id().equals(current),
                    unmetRequired(g, t, coverage), n.layer() + 1,
                    masteryOf(g, t, mastery) == null ? null : masteryOf(g, t, mastery).cssClass(),
                    masteryOf(g, t, mastery) == null ? null : masteryOf(g, t, mastery).label());
            nodes.add(node);
            byKey.put(t.key(), node);
        }
        List<MapEdge> mapEdges = layout.edges().stream()
                .map(e -> new MapEdge(e.path(), e.required(),
                        "studied".equals(byKey.get(e.from()).coverage()) && "studied".equals(byKey.get(e.to()).coverage())))
                .toList();
        Map<Integer, List<MapNode>> stages = new LinkedHashMap<>();
        nodes.forEach(n -> stages.computeIfAbsent(n.stage(), s -> new ArrayList<>()).add(n));
        List<Stage> stageList = stages.entrySet().stream().map(e -> new Stage(e.getKey(), e.getValue())).toList();
        String currentTitle = current == null ? null : g.topicById(current).map(TopicRow::title).orElse(null);
        return Optional.of(new MapView(card(actor, g, modules), layout.width(), layout.height(), nodes, mapEdges,
                stageList, currentTitle));
    }

    private static MasteryState masteryOf(SubjectGraph g, TopicRow t, Map<UUID, MasteryRow> mastery) {
        return topicMastery(g.skillsOf(t.id()).stream().filter(s -> s.core()).map(s -> s.id()).toList(), mastery);
    }

    // ------------------------------------------------------------------ topic

    public Optional<TopicView> topic(Actor actor, String subjectKey, String topicKey) {
        Optional<SubjectGraph> graph = catalog.graph(subjectKey);
        if (graph.isEmpty() || graph.get().topic(topicKey).isEmpty()) {
            return Optional.empty();
        }
        SubjectGraph g = graph.get();
        TopicRow t = g.topic(topicKey).orElseThrow();
        List<ModuleRow> modules = content.modulesOfSubject(g.subject().id());
        Map<UUID, ModuleProgress> progress = content.progressInSubject(actor, g.subject().id());
        Map<UUID, Coverage> coverage = coverage(g, modules, progress);

        List<TopicLink> prereqs = new ArrayList<>();
        List<TopicLink> unlocks = new ArrayList<>();
        for (PrereqRow p : g.prerequisites()) {
            if (p.topicId().equals(t.id())) {
                TopicRow pre = g.topicById(p.prerequisiteId()).orElseThrow();
                Coverage pc = coverage.get(pre.id());
                prereqs.add(new TopicLink(pre.key(), pre.title(), p.strength(), pc.label(), pc == Coverage.STUDIED));
            }
            if (p.prerequisiteId().equals(t.id())) {
                TopicRow dep = g.topicById(p.topicId()).orElseThrow();
                unlocks.add(new TopicLink(dep.key(), dep.title(), p.strength(), coverage.get(dep.id()).label(),
                        coverage.get(dep.id()) == Coverage.STUDIED));
            }
        }
        List<TopicLink> subtopics = g.topics().stream().filter(x -> t.id().equals(x.parentId()))
                .map(x -> new TopicLink(x.key(), x.title(), null, coverage.get(x.id()).label(), coverage.get(x.id()) == Coverage.STUDIED))
                .toList();
        List<ModuleItem> moduleItems = modules.stream().filter(m -> m.topicId().equals(t.id()))
                .map(m -> new ModuleItem(m.key(), m.title(), m.summary(), m.estMinutes(), m.difficulty(), moduleState(progress.get(m.id()))))
                .toList();
        String start = moduleItems.stream().filter(m -> !"done".equals(m.state())).map(ModuleItem::key).findFirst()
                .orElse(moduleItems.isEmpty() ? null : moduleItems.get(0).key());
        Optional<TopicRow> parent = t.parentId() == null ? Optional.empty() : g.topicById(t.parentId());
        Coverage cov = coverage.get(t.id());
        return Optional.of(new TopicView(g.subject().key(), g.subject().title(), t.key(), t.title(), t.summary(),
                parent.map(TopicRow::key).orElse(null), parent.map(TopicRow::title).orElse(null), t.optional(), t.advanced(),
                cov.cssClass(), cov.label(),
                skillItems(actor, g, t),
                prereqs, unlocks, subtopics, moduleItems, unmetRequired(g, t, coverage), start));
    }

    private List<SkillItem> skillItems(Actor actor, SubjectGraph g, TopicRow t) {
        Map<UUID, MasteryRow> mastery = learner.mastery(actor);
        var supply = questions.supply();
        return g.skillsOf(t.id()).stream().map(s -> {
            MasteryRow m = mastery.get(s.id());
            MasteryState st = m == null ? MasteryState.NOT_STARTED : MasteryState.fromDb(m.state());
            return new SkillItem(s.id(), s.title(), s.description(), s.core(), st.cssClass(), st.label(),
                    m == null ? 0 : m.evidenceCount(), supply.containsKey(s.id()));
        }).toList();
    }

    // ------------------------------------------------------------------ module player

    public Optional<PlayerView> player(Actor actor, String moduleKey, String sectionKey) {
        Optional<ModuleBundle> bundle = content.module(moduleKey);
        if (bundle.isEmpty() || bundle.get().sections().isEmpty()) {
            return Optional.empty();
        }
        ModuleBundle b = bundle.get();
        List<SectionRow> sections = b.sections();
        Set<UUID> done = content.completedSections(actor, b.module().id());
        Optional<ModuleProgress> progress = content.progress(actor, b.module().id());

        int index;
        if (sectionKey != null) {
            index = indexOf(sections, sectionKey);
            if (index < 0) {
                return Optional.empty();
            }
        } else {
            // Resume: first unfinished section, else the last one visited, else the start.
            index = 0;
            for (int i = 0; i < sections.size(); i++) {
                if (!done.contains(sections.get(i).id())) {
                    index = i;
                    break;
                }
            }
            if (done.size() == sections.size()) {
                UUID last = progress.map(ModuleProgress::lastSectionId).orElse(null);
                for (int i = 0; i < sections.size(); i++) {
                    if (sections.get(i).id().equals(last)) {
                        index = i;
                    }
                }
            }
        }
        SectionRow current = sections.get(index);
        content.visit(actor, b.module().id(), current.id());

        List<OutlineItem> outline = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            SectionRow s = sections.get(i);
            outline.add(new OutlineItem(s.key(), s.title(), s.kind(), done.contains(s.id()), i == index, s.required()));
        }
        Optional<SubjectAndTopic> where = locate(b.module());
        int percent = Math.round(100f * done.size() / sections.size());
        return Optional.of(new PlayerView(
                where.map(SubjectAndTopic::subjectKey).orElse(null), where.map(SubjectAndTopic::subjectTitle).orElse(null),
                where.map(SubjectAndTopic::topicKey).orElse(null), where.map(SubjectAndTopic::topicTitle).orElse(null),
                b.module().key(), b.module().title(), b.module().summary(), b.module().objectives(),
                b.module().estMinutes(), b.module().difficulty(), outline, sectionContent(b, current),
                index + 1, sections.size(), done.size(), percent,
                index > 0 ? sections.get(index - 1).key() : null,
                index < sections.size() - 1 ? sections.get(index + 1).key() : null,
                progress.map(ModuleProgress::completed).orElse(false)));
    }

    /**
     * Marks the section done and says where to go next. Empty if the module or section does not exist,
     * or if it is a check section: those are completed only by submitting answers ({@link #completeCheckedSection}).
     */
    public Optional<CompleteResult> completeSection(Actor actor, String moduleKey, String sectionKey) {
        return complete(actor, moduleKey, sectionKey, false);
    }

    /** Completes a check section after its answers were graded. */
    public Optional<CompleteResult> completeCheckedSection(Actor actor, String moduleKey, String sectionKey) {
        return complete(actor, moduleKey, sectionKey, true);
    }

    private Optional<CompleteResult> complete(Actor actor, String moduleKey, String sectionKey, boolean checked) {
        Optional<ModuleBundle> bundle = content.module(moduleKey);
        if (bundle.isEmpty()) {
            return Optional.empty();
        }
        List<SectionRow> sections = bundle.get().sections();
        int index = indexOf(sections, sectionKey);
        if (index < 0 || (SectionKind.CHECK.dbValue().equals(sections.get(index).kind()) && !checked)) {
            return Optional.empty();
        }
        ContentService.SectionOutcome outcome = content.completeSection(actor, bundle.get().module(), sections.get(index));
        String next = index < sections.size() - 1 ? sections.get(index + 1).key() : null;
        return Optional.of(new CompleteResult(next, outcome.moduleCompletedNow()));
    }

    private SectionContent sectionContent(ModuleBundle b, SectionRow s) {
        JsonNode body = parse(s.bodyJson());
        String markdown = body.path("markdown").asText("");
        SafeHtml html = Markdown.render(markdown);
        String visualJson = null;
        String alt = null;
        if (SectionKind.VISUAL.dbValue().equals(s.kind())) {
            visualJson = Json.write(body.path("visual"));
            alt = body.path("alt").asText("");
        }
        List<Card> cards = SectionKind.FLASHCARDS.dbValue().equals(s.kind())
                ? b.flashcards().stream().map(f -> new Card(Markdown.inline(f.front()), Markdown.inline(f.back()))).toList()
                : List.of();
        List<Link> links = SectionKind.RESOURCES.dbValue().equals(s.kind())
                ? b.resources().stream().map(r -> new Link(r.title(), r.url(), r.kind(), r.licenceNote())).toList()
                : List.of();
        List<String> questionKeys = new ArrayList<>();
        body.path("questions").forEach(q -> questionKeys.add(q.asText()));
        return new SectionContent(s.key(), s.title(), s.kind(), html, visualJson, alt, cards, links, questionKeys, s.id().toString());
    }

    // ------------------------------------------------------------------ today

    public TodayView today(Actor actor) {
        List<SubjectCard> subjects = subjects(actor);
        ContinueCard cont = content.mostRecent(actor).map(r -> {
            int percent = content.module(r.moduleKey()).map(b -> {
                Set<UUID> done = content.completedSections(actor, b.module().id());
                return b.sections().isEmpty() ? 0 : Math.round(100f * done.size() / b.sections().size());
            }).orElse(0);
            return new ContinueCard(r.moduleKey(), r.moduleTitle(), r.topicTitle(), r.subjectTitle(), percent, r.completed());
        }).orElse(null);
        Suggestion suggestion = (cont == null || cont.completed()) ? suggest(actor, subjects) : null;
        return new TodayView(cont, suggestion, subjects);
    }

    /**
     * A transparent, coverage-based starting suggestion (the evidence-based engine replaces this in M2):
     * the first topic, in map order, that is not yet studied and whose required prerequisites are studied.
     */
    private Suggestion suggest(Actor actor, List<SubjectCard> subjects) {
        for (SubjectCard s : subjects) {
            SubjectGraph g = catalog.graph(s.key()).orElse(null);
            if (g == null) {
                continue;
            }
            List<ModuleRow> modules = content.modulesOfSubject(g.subject().id());
            Map<UUID, ModuleProgress> progress = content.progressInSubject(actor, g.subject().id());
            Map<UUID, Coverage> coverage = coverage(g, modules, progress);
            Map<String, Integer> layers = Graphs.layers(g.topics().stream().map(TopicRow::key).toList(), edges(g));
            List<TopicRow> ordered = new ArrayList<>(g.topics());
            ordered.sort(Comparator.comparingInt((TopicRow t) -> layers.get(t.key())).thenComparingInt(TopicRow::position));
            for (TopicRow t : ordered) {
                Coverage cov = coverage.get(t.id());
                if (cov == Coverage.STUDIED || cov == Coverage.NO_CONTENT || t.optional() || !unmetRequired(g, t, coverage).isEmpty()) {
                    continue;
                }
                Optional<ModuleRow> module = modules.stream()
                        .filter(m -> m.topicId().equals(t.id()) && !(progress.containsKey(m.id()) && progress.get(m.id()).completed()))
                        .findFirst();
                if (module.isEmpty()) {
                    continue;
                }
                List<String> studiedPrereqs = g.prerequisites().stream()
                        .filter(p -> p.topicId().equals(t.id()) && "required".equals(p.strength()))
                        .map(p -> g.topicById(p.prerequisiteId()).map(TopicRow::title).orElse(""))
                        .toList();
                String reason = studiedPrereqs.isEmpty()
                        ? t.title() + " has no prerequisites, so it's a natural place to begin."
                        : "You've studied " + joinTitles(studiedPrereqs) + ", which " + t.title() + " builds on.";
                return new Suggestion(module.get().key(), module.get().title(), t.title(), g.subject().title(), reason);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    private record SubjectAndTopic(String subjectKey, String subjectTitle, String topicKey, String topicTitle) {}

    private Optional<SubjectAndTopic> locate(ModuleRow module) {
        for (SubjectRow s : catalog.subjects()) {
            Optional<SubjectGraph> g = catalog.graph(s.key());
            if (g.isPresent()) {
                Optional<TopicRow> t = g.get().topicById(module.topicId());
                if (t.isPresent()) {
                    return Optional.of(new SubjectAndTopic(s.key(), s.title(), t.get().key(), t.get().title()));
                }
            }
        }
        return Optional.empty();
    }

    private static Map<UUID, Coverage> coverage(SubjectGraph g, List<ModuleRow> modules, Map<UUID, ModuleProgress> progress) {
        Map<UUID, int[]> counts = counts(g, modules, progress);
        Map<UUID, Coverage> out = new HashMap<>();
        for (TopicRow t : g.topics()) {
            int[] c = counts.get(t.id());
            out.put(t.id(), Coverage.of(c[1], c[2], c[0]));
        }
        return out;
    }

    /** Per topic: [completed modules, total modules, started modules]. */
    private static Map<UUID, int[]> counts(SubjectGraph g, List<ModuleRow> modules, Map<UUID, ModuleProgress> progress) {
        Map<UUID, int[]> counts = new HashMap<>();
        g.topics().forEach(t -> counts.put(t.id(), new int[3]));
        for (ModuleRow m : modules) {
            int[] c = counts.get(m.topicId());
            if (c == null) {
                continue;
            }
            c[1]++;
            ModuleProgress p = progress.get(m.id());
            if (p != null) {
                c[2]++;
                if (p.completed()) {
                    c[0]++;
                }
            }
        }
        return counts;
    }

    private static UUID currentTopic(List<ModuleRow> modules, Map<UUID, ModuleProgress> progress) {
        return modules.stream().filter(m -> progress.containsKey(m.id()))
                .max(Comparator.comparing(m -> progress.get(m.id()).lastSeenAt()))
                .map(ModuleRow::topicId).orElse(null);
    }

    private static List<String> unmetRequired(SubjectGraph g, TopicRow t, Map<UUID, Coverage> coverage) {
        return g.prerequisites().stream()
                .filter(p -> p.topicId().equals(t.id()) && "required".equals(p.strength())
                        && coverage.get(p.prerequisiteId()) != Coverage.STUDIED)
                .map(p -> g.topicById(p.prerequisiteId()).map(TopicRow::title).orElse("?"))
                .toList();
    }

    private static List<Graphs.Edge<String>> edges(SubjectGraph g) {
        Map<UUID, String> keyOf = g.topics().stream().collect(Collectors.toMap(TopicRow::id, TopicRow::key));
        return g.prerequisites().stream().map(p -> new Graphs.Edge<>(keyOf.get(p.prerequisiteId()), keyOf.get(p.topicId()))).toList();
    }

    private static String parentTitle(SubjectGraph g, TopicRow t) {
        return t.parentId() == null ? null : g.topicById(t.parentId()).map(TopicRow::title).orElse(null);
    }

    private static String moduleState(ModuleProgress p) {
        if (p == null) {
            return "new";
        }
        return p.completed() ? "done" : "started";
    }

    private static int indexOf(List<SectionRow> sections, String key) {
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static String joinTitles(List<String> titles) {
        if (titles.size() == 1) {
            return titles.get(0);
        }
        return String.join(", ", titles.subList(0, titles.size() - 1)) + " and " + titles.get(titles.size() - 1);
    }

    private static JsonNode parse(String json) {
        try {
            return Json.mapper().readTree(json);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Stored section body is not valid JSON", e);
        }
    }
}
