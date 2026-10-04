package app.pathwise.web.content;

import app.pathwise.core.security.Actor;
import app.pathwise.core.security.NotAuthenticatedException;
import app.pathwise.data.Tx;
import app.pathwise.data.content.ContentDao;
import app.pathwise.data.content.ContentDao.FlashcardRow;
import app.pathwise.data.content.ContentDao.ModuleRow;
import app.pathwise.data.content.ContentDao.ResourceRow;
import app.pathwise.data.content.ContentDao.SectionRow;
import app.pathwise.data.content.ProgressDao;
import app.pathwise.data.content.ProgressDao.ModuleProgress;
import app.pathwise.data.content.ProgressDao.RecentModule;
import app.pathwise.web.platform.events.EventBus;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Modules, their sections and each learner's reading progress (coverage). */
public final class ContentService {
    public static final String EVENT_MODULE_COMPLETED = "content.module_completed";

    private final Tx tx;
    private final EventBus events;
    private final Clock clock;
    private final ContentDao content = new ContentDao();
    private final ProgressDao progress = new ProgressDao();

    public ContentService(Tx tx, EventBus events, Clock clock) {
        this.tx = tx;
        this.events = events;
        this.clock = clock;
    }

    /** A module with everything the player needs. */
    public record ModuleBundle(ModuleRow module, List<SectionRow> sections, List<FlashcardRow> flashcards,
                               List<ResourceRow> resources) {}

    public record SectionOutcome(boolean newlyCompleted, boolean moduleCompletedNow) {}

    public List<ModuleRow> modulesOfSubject(UUID subjectId) {
        return tx.read(c -> content.publishedModulesOfSubject(c, subjectId));
    }

    public Optional<ModuleBundle> module(String key) {
        return tx.read(c -> content.findPublishedModule(c, key).map(m -> {
            try {
                return new ModuleBundle(m, content.sectionsOf(c, m.id()), content.flashcardsOf(c, m.id()), content.resourcesOf(c, m.id()));
            } catch (java.sql.SQLException e) {
                throw new app.pathwise.data.DataAccessException(e);
            }
        }));
    }

    public Map<UUID, ModuleProgress> progressInSubject(Actor actor, UUID subjectId) {
        requireUser(actor);
        return tx.read(c -> progress.progressInSubject(c, actor.userId(), subjectId));
    }

    public Optional<ModuleProgress> progress(Actor actor, UUID moduleId) {
        requireUser(actor);
        return tx.read(c -> progress.progress(c, actor.userId(), moduleId));
    }

    public Set<UUID> completedSections(Actor actor, UUID moduleId) {
        requireUser(actor);
        return tx.read(c -> progress.completedSections(c, actor.userId(), moduleId));
    }

    public Optional<RecentModule> mostRecent(Actor actor) {
        requireUser(actor);
        return tx.read(c -> progress.mostRecent(c, actor.userId()));
    }

    /** Records that the learner opened a section (starts the module on first visit). */
    public void visit(Actor actor, UUID moduleId, UUID sectionId) {
        requireUser(actor);
        Instant now = clock.instant();
        tx.run(c -> progress.touch(c, actor.userId(), moduleId, sectionId, now));
    }

    /**
     * Marks a section done. When that completes the module, records completion and publishes
     * {@value #EVENT_MODULE_COMPLETED} exactly once (in the same transaction).
     */
    public SectionOutcome completeSection(Actor actor, ModuleRow module, SectionRow section) {
        requireUser(actor);
        if (!section.moduleId().equals(module.id())) {
            throw new IllegalArgumentException("Section does not belong to this module");
        }
        Instant now = clock.instant();
        return tx.call(c -> {
            progress.touch(c, actor.userId(), module.id(), section.id(), now);
            boolean fresh = progress.completeSection(c, actor.userId(), section.id(), now);
            boolean done = progress.completeModuleIfFinished(c, actor.userId(), module.id(), now);
            if (done) {
                events.publish(c, EVENT_MODULE_COMPLETED, Map.of(
                        "userId", actor.userId().toString(), "moduleId", module.id().toString(),
                        "moduleKey", module.key(), "topicId", module.topicId().toString()));
            }
            return new SectionOutcome(fresh, done);
        });
    }

    private static void requireUser(Actor actor) {
        if (actor == null || !actor.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }
    }

    public Optional<String> moduleKey(UUID moduleId) {
        return tx.read(c -> content.moduleKey(c, moduleId));
    }

    public Map<UUID, Integer> sectionCounts() {
        return tx.read(content::sectionCounts);
    }

    public List<UUID> skillIdsOfModule(UUID moduleId) {
        return tx.read(c -> content.skillIdsOfModule(c, moduleId));
    }

    public Instant latestActivityAt(Actor actor) {
        requireUser(actor);
        return tx.read(c -> progress.latestActivityAt(c, actor.userId()));
    }

    public Map<UUID, ModuleProgress> allProgress(Actor actor) {
        requireUser(actor);
        return tx.read(c -> progress.allProgress(c, actor.userId()));
    }

    public Map<UUID, Integer> completedSectionCounts(Actor actor) {
        requireUser(actor);
        return tx.read(c -> progress.completedSectionCounts(c, actor.userId()));
    }

    /** Lets other services check timestamps consistently. */
    public Instant now() {
        return clock.instant();
    }
}
