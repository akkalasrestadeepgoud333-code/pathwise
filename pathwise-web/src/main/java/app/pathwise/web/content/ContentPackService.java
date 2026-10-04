package app.pathwise.web.content;

import app.pathwise.core.content.ContentPack;
import app.pathwise.core.content.ContentPack.ModuleDef;
import app.pathwise.core.content.ContentPack.SectionDef;
import app.pathwise.core.content.ContentPack.TopicDef;
import app.pathwise.core.content.PackValidator;
import app.pathwise.core.content.SectionKind;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.core.security.PlatformPolicy;
import app.pathwise.data.Tx;
import app.pathwise.data.catalog.CatalogDao;
import app.pathwise.data.content.ContentDao;
import app.pathwise.data.questions.QuestionDao;
import app.pathwise.data.assessment.AssessmentDao;
import app.pathwise.data.platform.AuditDao;
import app.pathwise.web.platform.Json;
import app.pathwise.web.platform.events.EventBus;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Imports bundled content packs into the catalogue and content tables. Everything is validated first,
 * then written in a single transaction, so a pack is applied completely or not at all. Re-importing an
 * unchanged pack is a no-op; an edited pack updates rows in place by key, keeping students' progress.
 */
public final class ContentPackService {
    public static final String EVENT_PACK_IMPORTED = "content.pack_imported";

    public enum Outcome { IMPORTED, UNCHANGED, INVALID }

    public record ImportResult(String key, Outcome outcome, List<String> errors, int topics, int modules, int sections,
                               int questions) {
        ImportResult(String key, Outcome outcome, List<String> errors, int topics, int modules, int sections) {
            this(key, outcome, errors, topics, modules, sections, 0);
        }
    }

    public record PackStatus(String key, String title, int availableVersion, Integer importedVersion, Instant importedAt,
                             boolean upToDate, List<String> problems) {}

    private final Tx tx;
    private final ContentPackLoader loader;
    private final EventBus events;
    private final Clock clock;
    private final CatalogDao catalog = new CatalogDao();
    private final ContentDao content = new ContentDao();
    private final QuestionDao questions = new QuestionDao();
    private final AssessmentDao assessments = new AssessmentDao();
    private final app.pathwise.data.games.GameDao games = new app.pathwise.data.games.GameDao();
    private final AuditDao audit = new AuditDao();

    public ContentPackService(Tx tx, ContentPackLoader loader, EventBus events, Clock clock) {
        this.tx = tx;
        this.loader = loader;
        this.events = events;
        this.clock = clock;
    }

    /** For the admin console. */
    public List<PackStatus> statuses(Actor actor) {
        PlatformPolicy.require(actor, PlatformAction.USE_ADMIN_AREA);
        List<PackStatus> out = new ArrayList<>();
        for (String key : loader.available()) {
            try {
                ContentPackLoader.Loaded loaded = loader.load(key);
                Optional<ContentDao.PackRow> row = tx.read(c -> content.pack(c, key));
                out.add(new PackStatus(key, loaded.title(), loaded.pack().version(),
                        row.map(ContentDao.PackRow::version).orElse(null), row.map(ContentDao.PackRow::importedAt).orElse(null),
                        row.map(r -> r.checksum().equals(loaded.checksum())).orElse(false),
                        PackValidator.validate(loaded.pack())));
            } catch (ContentPackLoader.PackFormatException e) {
                out.add(new PackStatus(key, key, 0, null, null, false, List.of(e.getMessage())));
            }
        }
        return out;
    }

    /** Admin-triggered import (checks the policy). */
    public ImportResult importPack(Actor actor, String key, boolean force) {
        PlatformPolicy.require(actor, PlatformAction.USE_ADMIN_AREA);
        return importPack(key, actor.userId(), force);
    }

    /** System import (setup script, CLI, tests). */
    public List<ImportResult> importAll(boolean force) {
        return loader.available().stream().map(k -> importPack(k, null, force)).toList();
    }

    public ImportResult importPack(String key, UUID importedBy, boolean force) {
        ContentPackLoader.Loaded loaded;
        try {
            loaded = loader.load(key);
        } catch (ContentPackLoader.PackFormatException e) {
            return new ImportResult(key, Outcome.INVALID, List.of(e.getMessage()), 0, 0, 0);
        }
        ContentPack pack = loaded.pack();
        List<String> errors = PackValidator.validate(pack);
        if (!errors.isEmpty()) {
            return new ImportResult(key, Outcome.INVALID, errors, 0, 0, 0);
        }
        Optional<ContentDao.PackRow> existing = tx.read(c -> content.pack(c, key));
        if (!force && existing.isPresent() && existing.get().checksum().equals(loaded.checksum())) {
            return new ImportResult(key, Outcome.UNCHANGED, List.of(), 0, 0, 0);
        }
        Instant now = clock.instant();
        int[] sectionCount = {0};
        tx.run(c -> {
            var cat = pack.catalog();
            UUID level = catalog.upsertNode(c, "level", cat.level().key(), null, cat.level().title(), cat.level().description(), 0);
            UUID program = catalog.upsertNode(c, "program", cat.program().key(), level, cat.program().title(), cat.program().description(), 0);
            UUID course = catalog.upsertNode(c, "course", cat.course().key(), program, cat.course().title(), cat.course().description(), 0);
            UUID subject = catalog.upsertNode(c, "subject", cat.subject().key(), course, cat.subject().title(), cat.subject().description(), 0);

            Map<String, UUID> topicIds = new LinkedHashMap<>();
            Map<String, UUID> skillIds = new HashMap<>();
            List<TopicDef> ordered = parentsFirst(pack.topics());
            for (TopicDef t : ordered) {
                int position = pack.topics().indexOf(t);
                UUID parent = t.parent() == null ? null : topicIds.get(t.parent());
                UUID id = catalog.upsertTopic(c, subject, t.key(), parent, t.title().strip(), t.summary(), position,
                        Boolean.TRUE.equals(t.optional()), Boolean.TRUE.equals(t.advanced()));
                topicIds.put(t.key(), id);
                int i = 0;
                for (var s : t.skillsOrEmpty()) {
                    skillIds.put(s.key(), catalog.upsertSkill(c, id, s.key(), s.title().strip(), s.description(), s.isCore(), i++));
                }
            }
            catalog.archiveTopicsNotIn(c, subject, topicIds.keySet());
            List<CatalogDao.PrereqRow> edges = new ArrayList<>();
            for (TopicDef t : pack.topics()) {
                for (var p : t.prerequisitesOrEmpty()) {
                    edges.add(new CatalogDao.PrereqRow(topicIds.get(t.key()), topicIds.get(p.topic()), p.strengthOrDefault()));
                }
            }
            catalog.replacePrerequisites(c, subject, edges);

            for (ContentPack.QuestionDef q : pack.questionsOrEmpty()) {
                Map<String, Object> spec = new LinkedHashMap<>();
                if (q.answer() != null) {
                    spec.put("answer", q.answer());
                    spec.put("tolerance", q.tolerance() == null ? 0.0 : q.tolerance());
                }
                if (q.accepted() != null) {
                    spec.put("accepted", q.accepted());
                }
                UUID qid = questions.upsertPlatformQuestion(c, q.key(), q.type(), q.stem().strip(), Json.write(spec),
                        q.difficulty(), Json.write(q.hintsOrEmpty()), q.explanation() == null ? null : q.explanation().strip(),
                        q.practiceEnabled());
                questions.replaceOptions(c, qid, q.optionsOrEmpty().stream()
                        .map(o -> new String[]{o.text().strip(), Boolean.toString(o.isCorrect()), o.why() == null ? null : o.why().strip()})
                        .toList());
                questions.setSkills(c, qid, q.skillsOrEmpty().stream().map(skillIds::get).toList());
            }
            questions.retireNotIn(c, skillIds.values(), pack.questionsOrEmpty().stream().map(ContentPack.QuestionDef::key).toList());

            Map<String, Integer> positionInTopic = new HashMap<>();
            for (ModuleDef m : pack.modules()) {
                int position = positionInTopic.merge(m.topic(), 1, Integer::sum) - 1;
                UUID moduleId = content.upsertPlatformModule(c, topicIds.get(m.topic()), m.key(), m.title().strip(), m.summary(),
                        m.objectivesOrEmpty(), m.estMinutes() == null ? 10 : m.estMinutes(),
                        m.difficulty() == null ? 2 : m.difficulty(), position, now);
                content.setModuleSkills(c, moduleId, m.skillsOrEmpty().stream().map(skillIds::get).toList());

                Map<String, Integer> kindCount = new HashMap<>();
                Set<String> keys = new LinkedHashSet<>();
                List<String[]> cards = new ArrayList<>();
                List<UUID> cardSkills = new ArrayList<>();
                List<String[]> links = new ArrayList<>();
                int pos = 0;
                for (SectionDef s : m.sectionsOrEmpty()) {
                    SectionKind kind = SectionKind.parse(s.kind()).orElseThrow();
                    String sectionKey = PackValidator.sectionKey(s, kindCount);
                    keys.add(sectionKey);
                    content.upsertSection(c, moduleId, sectionKey, pos++, kind.dbValue(), s.title().strip(),
                            Json.write(body(kind, s)), s.isRequired());
                    sectionCount[0]++;
                    if (kind == SectionKind.FLASHCARDS) {
                        for (var card : s.cards()) {
                            cards.add(new String[]{card.front().strip(), card.back().strip()});
                            cardSkills.add(card.skill() == null ? null : skillIds.get(card.skill()));
                        }
                    }
                    if (kind == SectionKind.RESOURCES) {
                        for (var l : s.links()) {
                            links.add(new String[]{l.kind() == null ? "link" : l.kind(), l.title().strip(), l.url(), l.licence()});
                        }
                    }
                }
                content.deleteSectionsNotIn(c, moduleId, keys);
                content.replaceFlashcards(c, moduleId, cards, cardSkills);
                content.replaceResources(c, moduleId, links);
            }
            content.archiveModulesNotIn(c, topicIds.values(), pack.modules().stream().map(ModuleDef::key).toList());

            Map<String, UUID> questionIds = new HashMap<>();
            for (var row : questions.byKeys(c, pack.questionsOrEmpty().stream().map(ContentPack.QuestionDef::key).toList())) {
                questionIds.put(row.key(), row.id());
            }
            for (ContentPack.AssessmentDef a : pack.assessmentsOrEmpty()) {
                UUID id = assessments.upsertPlatform(c, a.key(), subject, a.topic() == null ? null : topicIds.get(a.topic()),
                        a.kind(), a.title().strip(), a.instructions() == null ? null : a.instructions().strip(),
                        a.timeLimitMinutes() == null ? null : a.timeLimitMinutes() * 60,
                        a.maxAttempts() == null ? 1 : a.maxAttempts(), Boolean.TRUE.equals(a.shuffle()),
                        a.aiPolicy() == null ? "blocked" : a.aiPolicy(), a.feedback() == null ? "immediate" : a.feedback());
                // Once anyone has attempted it, the item set is frozen so past results stay comparable.
                if (!assessments.hasAttempts(c, id)) {
                    List<UUID> ids = a.questionsOrEmpty().stream().map(questionIds::get).toList();
                    assessments.replaceItems(c, id, ids, ids.stream().map(x -> 1.0).toList());
                }
            }
            assessments.archiveNotIn(c, subject, pack.assessmentsOrEmpty().stream().map(ContentPack.AssessmentDef::key).toList());
            // Games keep their config as JSON; attempts snapshot the generated puzzle, so edits never alter past plays.
            for (ContentPack.GameDef g : pack.gamesOrEmpty()) {
                games.upsertPlatform(c, g.key(), g.template(), subject, g.topic() == null ? null : topicIds.get(g.topic()),
                        g.title().strip(), g.description() == null ? null : g.description().strip(), Json.write(g.config()),
                        g.difficulty() == null ? 2 : g.difficulty(), g.estMinutes());
            }
            games.archiveNotIn(c, subject, pack.gamesOrEmpty().stream().map(ContentPack.GameDef::key).toList());
            content.recordPack(c, pack.key(), pack.version(), loaded.checksum(), importedBy);
            audit.record(c, importedBy, "content.pack_imported", "content_pack", pack.key(),
                    Json.write(Map.of("version", pack.version(), "modules", pack.modules().size())), null);
            events.publish(c, EVENT_PACK_IMPORTED, Map.of("pack", pack.key(), "version", pack.version()));
        });
        return new ImportResult(key, Outcome.IMPORTED, List.of(), pack.topics().size(), pack.modules().size(), sectionCount[0],
                pack.questionsOrEmpty().size());
    }

    /** The JSON stored in {@code module_sections.body} for each kind. Cards and links live in their own tables. */
    static Map<String, Object> body(SectionKind kind, SectionDef s) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (kind.isText() || kind == SectionKind.COMMON_DOUBTS) {
            body.put("markdown", s.markdown() == null ? "" : s.markdown().strip());
        }
        if (kind == SectionKind.VISUAL) {
            body.put("visual", s.visual());
            body.put("alt", s.alt().strip());
            if (s.markdown() != null) {
                body.put("markdown", s.markdown().strip());
            }
        }
        if (kind == SectionKind.CHECK) {
            body.put("questions", s.questionsOrEmpty());
        }
        if (kind == SectionKind.FLASHCARDS || kind == SectionKind.RESOURCES || kind == SectionKind.CHECK) {
            if (s.markdown() != null) {
                body.put("markdown", s.markdown().strip());
            }
        }
        return body;
    }

    private static List<TopicDef> parentsFirst(List<TopicDef> topics) {
        Map<String, TopicDef> byKey = new LinkedHashMap<>();
        topics.forEach(t -> byKey.put(t.key(), t));
        List<TopicDef> out = new ArrayList<>();
        Set<String> placed = new LinkedHashSet<>();
        for (TopicDef t : topics) {
            place(t, byKey, placed, out);
        }
        return out;
    }

    private static void place(TopicDef t, Map<String, TopicDef> byKey, Set<String> placed, List<TopicDef> out) {
        if (placed.contains(t.key())) {
            return;
        }
        if (t.parent() != null && byKey.containsKey(t.parent())) {
            place(byKey.get(t.parent()), byKey, placed, out);
        }
        placed.add(t.key());
        out.add(t);
    }
}
