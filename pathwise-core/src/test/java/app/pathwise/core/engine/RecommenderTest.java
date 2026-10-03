package app.pathwise.core.engine;

import app.pathwise.core.engine.Recommender.Recommendation;
import app.pathwise.core.engine.Recommender.Slot;
import app.pathwise.core.engine.Recommender.TargetKind;
import app.pathwise.core.engine.Snapshot.*;
import app.pathwise.core.learner.MasteryState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static app.pathwise.core.learner.MasteryState.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden persona fixtures for the rule-based engine. Each test describes a realistic student and the
 * suggestions (and reasons) Pathwise must give.
 */
class RecommenderTest {
    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");

    // arrays → lists → trees ← recursion ; trees → traversals ← recursion (both required)
    private static final List<Topic> TOPICS = List.of(
            new Topic("arrays", "Arrays", "ds", 0, false, false),
            new Topic("lists", "Linked Lists", "ds", 1, false, false),
            new Topic("recursion", "Recursion", "ds", 2, false, false),
            new Topic("trees", "Trees", "ds", 3, false, false),
            new Topic("traversals", "Tree Traversals", "ds", 4, false, false));
    private static final List<Prerequisite> PREREQS = List.of(
            new Prerequisite("lists", "arrays", true),
            new Prerequisite("trees", "lists", true),
            new Prerequisite("trees", "recursion", true),
            new Prerequisite("traversals", "trees", true),
            new Prerequisite("traversals", "recursion", true));

    private static Skill skill(String id, String topic, MasteryState state, double estimate, int evidence,
                               int attempts, int errors) {
        return new Skill(id, title(id), topic, true, state, estimate, evidence == 0 ? 0 : 0.5, evidence,
                evidence == 0 ? null : NOW.plus(Duration.ofDays(3)), attempts, errors, 6, 2);
    }

    private static String title(String id) {
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    private static List<Skill> freshSkills() {
        return new ArrayList<>(List.of(
                skill("indexing", "arrays", NOT_STARTED, 0.4, 0, 0, 0),
                skill("pointers", "lists", NOT_STARTED, 0.4, 0, 0, 0),
                skill("base-case", "recursion", NOT_STARTED, 0.4, 0, 0, 0),
                skill("tree-terms", "trees", NOT_STARTED, 0.4, 0, 0, 0),
                skill("preorder", "traversals", NOT_STARTED, 0.4, 0, 0, 0)));
    }

    private static List<ModuleInfo> modules(String... completedTopics) {
        List<ModuleInfo> out = new ArrayList<>();
        for (Topic t : TOPICS) {
            boolean done = List.of(completedTopics).contains(t.id());
            out.add(new ModuleInfo("m-" + t.id(), t.title() + " module", t.id(), 0, done, done, done ? 100 : 0,
                    done ? NOW.minus(Duration.ofDays(1)) : null));
        }
        return out;
    }

    private static Snapshot snapshot(List<Skill> skills, List<ModuleInfo> modules, List<Goal> goals, List<Suppression> sup) {
        return new Snapshot(NOW, TOPICS, skills, PREREQS, modules, goals, sup, List.of());
    }

    private static Optional<Recommendation> top(List<Recommendation> recs, Slot slot) {
        return recs.stream().filter(r -> r.slot() == slot && r.rank() == 1).findFirst();
    }

    private static void replace(List<Skill> skills, Skill s) {
        skills.removeIf(x -> x.id().equals(s.id()));
        skills.add(s);
    }

    @Test
    void newStudentIsOfferedTheTopicsWithNoPrerequisites() {
        List<Recommendation> recs = Recommender.recommend(snapshot(freshSkills(), modules(), List.of(), List.of()));
        assertThat(recs).allMatch(r -> r.slot() == Slot.EXPLORE);
        assertThat(recs).extracting(Recommendation::topicId).containsExactlyInAnyOrder("arrays", "recursion");
        assertThat(recs).allMatch(r -> r.reasonCode().equals("START_HERE") && r.reasonText().contains("no prerequisites"));
    }

    @Test
    void strugglingWithTraversalsBecauseOfWeakRecursionPointsToTheRootCause() {
        List<Skill> skills = freshSkills();
        replace(skills, skill("base-case", "recursion", NEEDS_SUPPORT, 0.35, 6, 6, 4));
        replace(skills, skill("preorder", "traversals", NEEDS_SUPPORT, 0.30, 5, 5, 3));
        List<Recommendation> recs = Recommender.recommend(snapshot(skills, modules("arrays", "lists", "recursion", "trees"), List.of(), List.of()));
        Recommendation strengthen = top(recs, Slot.STRENGTHEN).orElseThrow();
        assertThat(strengthen.topicId()).isEqualTo("recursion");
        assertThat(strengthen.reasonCode()).isEqualTo("PREREQ_GAP");
        assertThat(strengthen.reasonText()).contains("Tree Traversals builds on Recursion")
                .contains("3 of your last 5 answers on Preorder were incorrect");
        assertThat(strengthen.factors()).anyMatch(f -> f.name().equals("root_cause"));
    }

    @Test
    void strugglingWithASoundFoundationGetsTargetedPractice() {
        List<Skill> skills = freshSkills();
        replace(skills, skill("base-case", "recursion", PROFICIENT, 0.8, 8, 2, 0));
        replace(skills, skill("tree-terms", "trees", PROFICIENT, 0.8, 8, 2, 0));
        replace(skills, skill("preorder", "traversals", DEVELOPING, 0.5, 5, 5, 3));
        List<Recommendation> recs = Recommender.recommend(snapshot(skills, modules("arrays", "lists", "recursion", "trees", "traversals"), List.of(), List.of()));
        Recommendation r = top(recs, Slot.STRENGTHEN).orElseThrow();
        assertThat(r.targetKind()).isEqualTo(TargetKind.PRACTICE);
        assertThat(r.targetId()).isEqualTo("preorder");
        assertThat(r.reasonText()).startsWith("Recommended because 3 of your last 5 answers on Preorder were incorrect.");
    }

    @Test
    void proficientSkillsAreOfferedAChallengeAndDueSkillsAReview() {
        List<Skill> skills = freshSkills();
        replace(skills, skill("indexing", "arrays", PROFICIENT, 0.78, 9, 0, 0));
        Skill due = new Skill("pointers", "Pointers", "lists", true, DEVELOPING, 0.6, 0.5, 6,
                NOW.minus(Duration.ofDays(2)), 0, 0, 6, 0);
        replace(skills, due);
        List<Recommendation> recs = Recommender.recommend(snapshot(skills, modules("arrays", "lists"), List.of(), List.of()));
        assertThat(top(recs, Slot.CHALLENGE)).hasValueSatisfying(r -> {
            assertThat(r.targetId()).isEqualTo("indexing");
            assertThat(r.reasonText()).contains("based on 9 answers");
        });
        assertThat(top(recs, Slot.REVISE)).hasValueSatisfying(r -> assertThat(r.targetId()).isEqualTo("pointers"));
    }

    @Test
    void anUnfinishedModuleIsOfferedToContinue() {
        List<ModuleInfo> ms = new ArrayList<>(modules("arrays"));
        ms.removeIf(m -> m.topicId().equals("lists"));
        ms.add(new ModuleInfo("m-lists", "Linked Lists module", "lists", 0, true, false, 40, NOW.minus(Duration.ofDays(2))));
        List<Recommendation> recs = Recommender.recommend(snapshot(freshSkills(), ms, List.of(), List.of()));
        Recommendation c = top(recs, Slot.CONTINUE).orElseThrow();
        assertThat(c.targetId()).isEqualTo("m-lists");
        assertThat(c.reasonText()).isEqualTo("You're 40% through it. You last opened it 2 days ago.");
        assertThat(recs.get(0).slot()).as("continuing ranks highest").isEqualTo(Slot.CONTINUE);
    }

    @Test
    void lockedTopicsAreNotExploredUntilTheirFoundationsAreReady() {
        List<Recommendation> recs = Recommender.recommend(snapshot(freshSkills(), modules("arrays", "lists"), List.of(), List.of()));
        assertThat(recs).extracting(Recommendation::topicId).doesNotContain("trees", "traversals");
        assertThat(recs).filteredOn(r -> r.slot() == Slot.EXPLORE).extracting(Recommendation::topicId).containsExactly("recursion");
    }

    @Test
    void goalsRaiseTheScoreOfTopicsOnTheWay() {
        List<Recommendation> without = Recommender.recommend(snapshot(freshSkills(), modules(), List.of(), List.of()));
        List<Recommendation> with = Recommender.recommend(snapshot(freshSkills(), modules(),
                List.of(new Goal("traversals", "Tree Traversals", null)), List.of()));
        double before = without.stream().filter(r -> r.topicId().equals("recursion")).findFirst().orElseThrow().score();
        double after = with.stream().filter(r -> r.topicId().equals("recursion")).findFirst().orElseThrow().score();
        assertThat(after).isGreaterThan(before);
        assertThat(with.stream().filter(r -> r.topicId().equals("recursion")).findFirst().orElseThrow().factors())
                .anyMatch(f -> f.name().equals("goal_fit") && f.explanation().contains("master Tree Traversals"));
    }

    @Test
    void suppressedTargetsDisappearUntilTheSuppressionEnds() {
        Suppression sup = new Suppression(Set.of("m-arrays"), NOW.plus(Duration.ofDays(1)));
        List<Recommendation> recs = Recommender.recommend(snapshot(freshSkills(), modules(), List.of(), List.of(sup)));
        assertThat(recs).extracting(Recommendation::targetId).doesNotContain("m-arrays");
        Suppression expired = new Suppression(Set.of("m-arrays"), NOW.minus(Duration.ofMinutes(1)));
        assertThat(Recommender.recommend(snapshot(freshSkills(), modules(), List.of(), List.of(expired))))
                .extracting(Recommendation::targetId).contains("m-arrays");
    }

    @Test
    void outputIsDeterministicAndEveryFactorIsExplained() {
        Snapshot s = snapshot(freshSkills(), modules("arrays"), List.of(), List.of());
        assertThat(Recommender.recommend(s)).isEqualTo(Recommender.recommend(s));
        for (Recommendation r : Recommender.recommend(s)) {
            assertThat(r.factors()).allSatisfy(f -> assertThat(f.explanation()).isNotBlank());
            assertThat(r.reasonText()).isNotBlank();
            assertThat(r.rank()).isBetween(1, 3);
        }
    }
}
