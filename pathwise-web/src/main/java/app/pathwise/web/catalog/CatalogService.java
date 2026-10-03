package app.pathwise.web.catalog;

import app.pathwise.data.Tx;
import app.pathwise.data.catalog.CatalogDao;
import app.pathwise.data.catalog.CatalogDao.PrereqRow;
import app.pathwise.data.catalog.CatalogDao.SkillRow;
import app.pathwise.data.catalog.CatalogDao.SubjectRow;
import app.pathwise.data.catalog.CatalogDao.TopicRow;

import java.util.List;
import java.util.Optional;

/** Read access to the published catalogue: subjects, their topic graph and skills. */
public final class CatalogService {
    private final Tx tx;
    private final CatalogDao dao = new CatalogDao();

    public CatalogService(Tx tx) {
        this.tx = tx;
    }

    /** One subject's complete topic graph, loaded in a single round of queries. */
    public record SubjectGraph(SubjectRow subject, List<TopicRow> topics, List<SkillRow> skills, List<PrereqRow> prerequisites) {
        public Optional<TopicRow> topic(String key) {
            return topics.stream().filter(t -> t.key().equals(key)).findFirst();
        }

        public Optional<TopicRow> topicById(java.util.UUID id) {
            return topics.stream().filter(t -> t.id().equals(id)).findFirst();
        }

        public List<SkillRow> skillsOf(java.util.UUID topicId) {
            return skills.stream().filter(s -> s.topicId().equals(topicId)).toList();
        }
    }

    public List<SubjectRow> subjects() {
        return tx.read(dao::publishedSubjects);
    }

    public Optional<SubjectGraph> graph(String subjectKey) {
        return tx.read(c -> {
            Optional<SubjectRow> subject = dao.findSubject(c, subjectKey);
            if (subject.isEmpty()) {
                return Optional.<SubjectGraph>empty();
            }
            var id = subject.get().id();
            return Optional.of(new SubjectGraph(subject.get(), dao.publishedTopics(c, id), dao.skillsOfSubject(c, id),
                    dao.prerequisitesOfSubject(c, id)));
        });
    }
}
