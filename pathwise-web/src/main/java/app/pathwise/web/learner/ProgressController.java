package app.pathwise.web.learner;

import app.pathwise.core.learner.MasteryState;
import app.pathwise.core.security.Actor;
import app.pathwise.core.security.PlatformAction;
import app.pathwise.data.learner.EvidenceDao.MasteryRow;
import app.pathwise.web.platform.http.PageServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /app/progress}: the learner model in plain language, per subject → topic → skill. Shows each
 * skill's state, how much evidence it rests on and how confident the estimate is. No raw percentages.
 */
public class ProgressController extends PageServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Actor actor = require(req, PlatformAction.USE_STUDENT_AREA);
        Map<UUID, MasteryRow> mastery = app().learner().mastery(actor);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MasteryState s : MasteryState.values()) {
            counts.put(s.cssClass(), 0);
        }
        List<Map<String, Object>> subjects = new ArrayList<>();
        int totalEvidence = 0;
        for (var s : app().catalog().subjects()) {
            var graph = app().catalog().graph(s.key()).orElse(null);
            if (graph == null) {
                continue;
            }
            List<Map<String, Object>> topics = new ArrayList<>();
            for (var t : graph.topics()) {
                List<Map<String, Object>> skills = new ArrayList<>();
                for (var k : graph.skillsOf(t.id())) {
                    MasteryRow m = mastery.get(k.id());
                    MasteryState st = m == null ? MasteryState.NOT_STARTED : MasteryState.fromDb(m.state());
                    counts.merge(st.cssClass(), 1, Integer::sum);
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("id", k.id().toString());
                    v.put("title", k.title());
                    v.put("state", st.cssClass());
                    v.put("stateLabel", st.label());
                    v.put("evidence", m == null ? 0 : m.evidenceCount());
                    v.put("confidence", m == null ? "none" : m.confidence() >= 0.6 ? "high" : m.confidence() >= 0.35 ? "medium" : "low");
                    v.put("level", switch (st) {
                        case NOT_STARTED -> 0;
                        case EXPLORING, NEEDS_SUPPORT -> 1;
                        case DEVELOPING -> 2;
                        case PROFICIENT -> 4;
                        case MASTERED -> 5;
                    });
                    skills.add(v);
                    totalEvidence += m == null ? 0 : m.evidenceCount();
                }
                Map<String, Object> tv = new LinkedHashMap<>();
                tv.put("key", t.key());
                tv.put("title", t.title());
                tv.put("skills", skills);
                tv.put("active", skills.stream().anyMatch(x -> (int) x.get("evidence") > 0));
                topics.add(tv);
            }
            Map<String, Object> sv = new LinkedHashMap<>();
            sv.put("key", s.key());
            sv.put("title", s.title());
            sv.put("topics", topics);
            subjects.add(sv);
        }
        req.setAttribute("subjects", subjects);
        req.setAttribute("counts", counts);
        req.setAttribute("totalEvidence", totalEvidence);
        req.setAttribute("nav", "progress");
        render(req, res, "progress/index");
    }
}
