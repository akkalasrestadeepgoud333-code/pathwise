package app.pathwise.core.engine;

import app.pathwise.core.catalog.Graphs;
import app.pathwise.core.engine.Snapshot.ModuleInfo;
import app.pathwise.core.engine.Snapshot.Skill;
import app.pathwise.core.engine.Snapshot.Topic;
import app.pathwise.core.learner.MasteryState;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Pathwise recommendation engine (blueprint §13): a pure, deterministic, rule-based function.
 * <ol>
 *   <li>Generate candidates per slot (continue, strengthen, challenge, explore, revise).</li>
 *   <li>Drop anything the student suppressed.</li>
 *   <li>Score each with named, weighted factors.</li>
 *   <li>Keep the best three per slot, each with an evidence-based reason.</li>
 * </ol>
 * There is no machine learning here, and the reasons quote only facts present in the snapshot.
 */
public final class Recommender {
    public static final String VERSION = "rules-v1";

    public enum Slot { CONTINUE, STRENGTHEN, CHALLENGE, EXPLORE, REVISE }

    public enum TargetKind { MODULE, PRACTICE, CHALLENGE, REVIEW }

    /** One factor's contribution, with a plain-language explanation for the "Why this?" panel. */
    public record Factor(String name, double value, double weight, String explanation) {
        public double contribution() {
            return value * weight;
        }
    }

    public record Recommendation(Slot slot, int rank, TargetKind targetKind, String targetId, String topicId,
                                 String title, String reasonCode, String reasonText, List<Factor> factors,
                                 double score) {}

    public record Weights(double need, double leverage, double goalFit, double curriculumFit, double recency,
                          double variety, double base) {
        public static Weights defaults() {
            return new Weights(0.30, 0.15, 0.20, 0.10, 0.15, 0.05, 0.10);
        }
    }

    private static final int PER_SLOT = 3;

    private Recommender() {}

    public static List<Recommendation> recommend(Snapshot s) {
        return recommend(s, Weights.defaults());
    }

    public static List<Recommendation> recommend(Snapshot s, Weights w) {
        Context ctx = new Context(s, w);
        List<Recommendation> all = new ArrayList<>();
        all.addAll(ctx.continueSlot());
        all.addAll(ctx.strengthenSlot());
        all.addAll(ctx.challengeSlot());
        all.addAll(ctx.exploreSlot());
        all.addAll(ctx.reviseSlot());

        Map<Slot, List<Recommendation>> bySlot = new LinkedHashMap<>();
        for (Recommendation r : all) {
            if (!ctx.suppressed(r.targetId(), r.topicId())) {
                bySlot.computeIfAbsent(r.slot(), k -> new ArrayList<>()).add(r);
            }
        }
        List<Recommendation> out = new ArrayList<>();
        bySlot.forEach((slot, list) -> {
            list.sort(Comparator.comparingDouble(Recommendation::score).reversed().thenComparing(Recommendation::title));
            // Same target never appears twice in a slot.
            List<String> seen = new ArrayList<>();
            int rank = 1;
            for (Recommendation r : list) {
                if (seen.contains(r.targetId()) || rank > PER_SLOT) {
                    continue;
                }
                seen.add(r.targetId());
                out.add(new Recommendation(r.slot(), rank++, r.targetKind(), r.targetId(), r.topicId(), r.title(),
                        r.reasonCode(), r.reasonText(), r.factors(), r.score()));
            }
        });
        out.sort(Comparator.comparingDouble(Recommendation::score).reversed());
        return out;
    }

    /** Pre-computed indexes over the snapshot. */
    private static final class Context {
        final Snapshot s;
        final Weights w;
        final Map<String, Topic> topics = new HashMap<>();
        final Map<String, List<Skill>> skillsByTopic = new HashMap<>();
        final Map<String, List<ModuleInfo>> modulesByTopic = new HashMap<>();
        final Map<String, Integer> leverage = new HashMap<>();
        final Map<String, Double> curriculumRank = new HashMap<>();
        final Set<String> goalTopics;

        Context(Snapshot s, Weights w) {
            this.s = s;
            this.w = w;
            s.topics().forEach(t -> topics.put(t.id(), t));
            s.skills().forEach(k -> skillsByTopic.computeIfAbsent(k.topicId(), x -> new ArrayList<>()).add(k));
            s.modules().stream().sorted(Comparator.comparingInt(ModuleInfo::position))
                    .forEach(m -> modulesByTopic.computeIfAbsent(m.topicId(), x -> new ArrayList<>()).add(m));
            List<Graphs.Edge<String>> edges = s.prerequisites().stream()
                    .map(p -> new Graphs.Edge<>(p.prerequisiteTopicId(), p.topicId())).toList();
            int maxLeverage = 1;
            for (Topic t : s.topics()) {
                int n = Graphs.descendants(t.id(), edges).size();
                leverage.put(t.id(), n);
                maxLeverage = Math.max(maxLeverage, n);
            }
            this.maxLeverage = maxLeverage;
            // Earlier in the authored order and in the prerequisite layering = earlier in the curriculum.
            Map<String, Integer> layers = Graphs.layers(s.topics().stream().map(Topic::id).toList(), edges);
            List<Topic> ordered = new ArrayList<>(s.topics());
            ordered.sort(Comparator.comparingInt((Topic t) -> layers.get(t.id())).thenComparingInt(Topic::position));
            for (int i = 0; i < ordered.size(); i++) {
                curriculumRank.put(ordered.get(i).id(), ordered.size() == 1 ? 1.0 : 1.0 - (double) i / (ordered.size() - 1));
            }
            Set<String> goals = s.goals().stream().map(Snapshot.Goal::topicId).collect(Collectors.toSet());
            // A goal also pulls in the topics it builds on.
            Set<String> withPrereqs = new java.util.HashSet<>(goals);
            for (String g : goals) {
                ancestors(g, withPrereqs);
            }
            this.goalTopics = withPrereqs;
        }

        final int maxLeverage;

        private void ancestors(String topicId, Set<String> acc) {
            for (Snapshot.Prerequisite p : s.prerequisites()) {
                if (p.topicId().equals(topicId) && acc.add(p.prerequisiteTopicId())) {
                    ancestors(p.prerequisiteTopicId(), acc);
                }
            }
        }

        boolean suppressed(String targetId, String topicId) {
            return s.suppressions().stream().anyMatch(x -> x.until().isAfter(s.now())
                    && (x.targetIds().contains(targetId) || x.targetIds().contains(topicId)));
        }

        // ------------------------------------------------------------ topic-level facts

        boolean hasEvidence(String topicId) {
            return skillsByTopic.getOrDefault(topicId, List.of()).stream().anyMatch(k -> k.evidenceCount() > 0);
        }

        double topicEstimate(String topicId) {
            List<Skill> core = skillsByTopic.getOrDefault(topicId, List.of()).stream().filter(Skill::core).toList();
            return core.isEmpty() ? 0 : core.stream().mapToDouble(Skill::estimate).average().orElse(0);
        }

        boolean modulesComplete(String topicId) {
            List<ModuleInfo> ms = modulesByTopic.getOrDefault(topicId, List.of());
            return !ms.isEmpty() && ms.stream().allMatch(ModuleInfo::completed);
        }

        boolean topicStarted(String topicId) {
            return hasEvidence(topicId) || modulesByTopic.getOrDefault(topicId, List.of()).stream().anyMatch(ModuleInfo::started);
        }

        /** Ready = studied (coverage) and not shown to be weak, or demonstrated proficient. */
        boolean topicReady(String topicId) {
            List<Skill> core = skillsByTopic.getOrDefault(topicId, List.of()).stream().filter(Skill::core).toList();
            boolean demonstrated = !core.isEmpty() && core.stream().allMatch(k -> k.state().atLeast(MasteryState.PROFICIENT));
            boolean weak = core.stream().anyMatch(k -> k.state() == MasteryState.NEEDS_SUPPORT);
            return demonstrated || (modulesComplete(topicId) && !weak);
        }

        List<Topic> requiredPrereqs(String topicId) {
            return s.prerequisites().stream().filter(p -> p.required() && p.topicId().equals(topicId))
                    .map(p -> topics.get(p.prerequisiteTopicId())).filter(java.util.Objects::nonNull).toList();
        }

        // ------------------------------------------------------------ factors

        List<Factor> factors(String topicId, double need, String needText, String kind) {
            List<Factor> f = new ArrayList<>();
            f.add(new Factor("need", need, w.need(), needText));
            int lev = leverage.getOrDefault(topicId, 0);
            f.add(new Factor("leverage", (double) lev / maxLeverage, w.leverage(),
                    lev == 0 ? "Nothing else depends on this topic yet." :
                            topics.get(topicId).title() + " leads to " + lev + " later topic" + (lev == 1 ? "." : "s.")));
            boolean goal = goalTopics.contains(topicId);
            f.add(new Factor("goal_fit", goal ? 1 : 0, w.goalFit(), goal
                    ? "Part of your goal" + goalNames() + "."
                    : s.goals().isEmpty() ? "You haven't set a goal." : "Not part of your current goal."));
            double cr = curriculumRank.getOrDefault(topicId, 0.5);
            f.add(new Factor("curriculum_fit", cr, w.curriculumFit(), cr >= 0.66 ? "Early in the subject's order."
                    : cr >= 0.33 ? "In the middle of the subject's order." : "Later in the subject's order."));
            List<String> recent = s.recentActivityKinds();
            boolean repetitive = recent.size() >= 3 && recent.subList(0, 3).stream().allMatch(kind::equals);
            f.add(new Factor("variety", repetitive ? 0 : 1, w.variety(), repetitive
                    ? "You've done this kind of activity several times in a row."
                    : "Adds some variety to what you've been doing."));
            f.add(new Factor("base", 1, w.base(), "Every suggestion starts with the same base score."));
            return f;
        }

        String goalNames() {
            List<String> names = s.goals().stream().map(Snapshot.Goal::title).toList();
            return names.isEmpty() ? "" : ": master " + String.join(", ", names);
        }

        static double score(List<Factor> f) {
            return Math.round(f.stream().mapToDouble(Factor::contribution).sum() * 1000) / 1000.0;
        }

        static Factor recency(double value, double weight, String text) {
            return new Factor("recency", value, weight, text);
        }

        // ------------------------------------------------------------ slots

        List<Recommendation> continueSlot() {
            List<Recommendation> out = new ArrayList<>();
            for (ModuleInfo m : s.modules()) {
                if (!m.started() || m.completed() || m.lastSeen() == null
                        || Duration.between(m.lastSeen(), s.now()).toDays() > 14) {
                    continue;
                }
                long days = Duration.between(m.lastSeen(), s.now()).toDays();
                List<Factor> f = factors(m.topicId(), 1 - m.percent() / 100.0,
                        "You're " + m.percent() + "% through this module.", "module");
                f.add(recency(Math.max(0, 1 - days / 14.0), w.recency(), "You last opened it " + ago(days) + "."));
                String reason = "You're " + m.percent() + "% through it. You last opened it " + ago(days) + ".";
                out.add(new Recommendation(Slot.CONTINUE, 0, TargetKind.MODULE, m.id(), m.topicId(), m.title(),
                        "CONTINUE", reason, f, score(f) + 0.25));
            }
            return out;
        }

        List<Recommendation> strengthenSlot() {
            List<Recommendation> out = new ArrayList<>();
            for (Skill k : s.skills()) {
                boolean struggling = k.state() == MasteryState.NEEDS_SUPPORT
                        || (k.state() == MasteryState.DEVELOPING && k.recentErrors() >= 2)
                        || (k.state() == MasteryState.EXPLORING && k.recentErrors() >= 2);
                if (!struggling) {
                    continue;
                }
                // Root cause first: a weak required prerequisite topic.
                Topic gap = requiredPrereqs(k.topicId()).stream()
                        .filter(p -> !topicReady(p.id()))
                        .filter(p -> !hasEvidence(p.id()) || topicEstimate(p.id()) < 0.6)
                        .findFirst().orElse(null);
                Topic here = topics.get(k.topicId());
                if (gap != null) {
                    Skill weakest = skillsByTopic.getOrDefault(gap.id(), List.of()).stream()
                            .filter(x -> x.practiceQuestions() > 0)
                            .min(Comparator.comparingDouble(Skill::estimate)).orElse(null);
                    ModuleInfo firstModule = modulesByTopic.getOrDefault(gap.id(), List.of()).stream()
                            .filter(m -> !m.completed()).findFirst().orElse(null);
                    String reason = here.title() + " builds on " + gap.title() + ". "
                            + errorsSentence(k) + " Strengthening " + gap.title() + " first should make it easier.";
                    List<Factor> f = factors(gap.id(), 1 - k.estimate(), errorsSentence(k), "practice");
                    f.add(new Factor("root_cause", 1, 0.15, gap.title() + " is a required foundation for " + here.title() + "."));
                    if (firstModule != null && !hasEvidence(gap.id())) {
                        out.add(new Recommendation(Slot.STRENGTHEN, 0, TargetKind.MODULE, firstModule.id(), gap.id(),
                                firstModule.title(), "PREREQ_GAP", reason, f, score(f)));
                    } else if (weakest != null) {
                        out.add(new Recommendation(Slot.STRENGTHEN, 0, TargetKind.PRACTICE, weakest.id(), gap.id(),
                                "Practice: " + weakest.title(), "PREREQ_GAP", reason, f, score(f)));
                    }
                    continue;
                }
                if (k.practiceQuestions() == 0) {
                    continue;
                }
                List<Factor> f = factors(k.topicId(), 1 - k.estimate(), errorsSentence(k), "practice");
                String reason = "Recommended because " + lower(errorsSentence(k)) + " A short practice set at the right level can help.";
                out.add(new Recommendation(Slot.STRENGTHEN, 0, TargetKind.PRACTICE, k.id(), k.topicId(),
                        "Practice: " + k.title(), "RECENT_ERRORS", reason, f, score(f) + 0.1));
            }
            return out;
        }

        List<Recommendation> challengeSlot() {
            List<Recommendation> out = new ArrayList<>();
            for (Skill k : s.skills()) {
                if (k.state() != MasteryState.PROFICIENT || k.challengeQuestions() == 0) {
                    continue;
                }
                List<Factor> f = factors(k.topicId(), 0.5,
                        "Your level for " + k.title() + " is Proficient (from " + k.evidenceCount() + " answers).", "challenge");
                String reason = "You're Proficient in " + k.title() + " (based on " + k.evidenceCount()
                        + " answers). Harder questions can show whether you've mastered it.";
                out.add(new Recommendation(Slot.CHALLENGE, 0, TargetKind.CHALLENGE, k.id(), k.topicId(),
                        "Challenge: " + k.title(), "CONFIRM_MASTERY", reason, f, score(f)));
            }
            return out;
        }

        List<Recommendation> exploreSlot() {
            List<Recommendation> out = new ArrayList<>();
            for (Topic t : s.topics()) {
                // Answers alone (say, from the starting-point check) don't make a topic "studied": until its module
                // is opened or the topic is shown Proficient, it stays a candidate. Otherwise a student who has just
                // taken the diagnostic would get no suggestions at all.
                boolean moduleStarted = modulesByTopic.getOrDefault(t.id(), List.of()).stream().anyMatch(ModuleInfo::started);
                List<Skill> core = skillsByTopic.getOrDefault(t.id(), List.of()).stream().filter(Skill::core).toList();
                boolean demonstrated = !core.isEmpty() && core.stream().allMatch(k -> k.state().atLeast(MasteryState.PROFICIENT));
                if (moduleStarted || demonstrated || modulesComplete(t.id())) {
                    continue;
                }
                List<Topic> required = requiredPrereqs(t.id());
                if (!required.stream().allMatch(p -> topicReady(p.id()))) {
                    continue;
                }
                ModuleInfo first = modulesByTopic.getOrDefault(t.id(), List.of()).stream().findFirst().orElse(null);
                if (first == null) {
                    continue;
                }
                String reason = hasEvidence(t.id())
                        ? "You've answered a few questions on " + t.title() + "; its module covers it properly."
                        : required.isEmpty()
                        ? t.title() + " has no prerequisites, so it's a natural place to begin."
                        : "You've studied " + titles(required) + ", which " + t.title() + " builds on.";
                List<Factor> f = factors(t.id(), 1, "You haven't started " + t.title() + " yet.", "module");
                double optionalPenalty = t.optional() ? 0.15 : 0;
                out.add(new Recommendation(Slot.EXPLORE, 0, TargetKind.MODULE, first.id(), t.id(), first.title(),
                        required.isEmpty() ? "START_HERE" : "READY_NEXT", reason, f, score(f) - optionalPenalty));
            }
            return out;
        }

        List<Recommendation> reviseSlot() {
            List<Recommendation> out = new ArrayList<>();
            for (Skill k : s.skills()) {
                if (k.reviewDueAt() == null || k.reviewDueAt().isAfter(s.now()) || k.practiceQuestions() == 0
                        || !k.state().atLeast(MasteryState.DEVELOPING)) {
                    continue;
                }
                long overdue = Duration.between(k.reviewDueAt(), s.now()).toDays();
                List<Factor> f = factors(k.topicId(), 1 - k.estimate(),
                        k.title() + " is " + k.state().label().toLowerCase(Locale.ROOT) + " but due for review.", "review");
                f.add(recency(Math.min(1, (overdue + 1) / 14.0), w.recency(), "Review was due " + ago(overdue) + "."));
                String reason = "You reached " + k.state().label() + " in " + k.title()
                        + ". A three-question review now helps you keep it.";
                out.add(new Recommendation(Slot.REVISE, 0, TargetKind.REVIEW, k.id(), k.topicId(),
                        "Review: " + k.title(), "REVIEW_DUE", reason, f, score(f)));
            }
            return out;
        }

        static String errorsSentence(Skill k) {
            if (k.recentAttempts() > 0) {
                return k.recentErrors() + " of your last " + k.recentAttempts() + " answers on " + k.title()
                        + " were incorrect.";
            }
            return "Your current level for " + k.title() + " is " + k.state().label() + ".";
        }

        static String titles(List<Topic> ts) {
            List<String> names = ts.stream().map(Topic::title).toList();
            if (names.size() == 1) {
                return names.get(0);
            }
            return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
        }

        static String ago(long days) {
            return days <= 0 ? "today" : days == 1 ? "yesterday" : days + " days ago";
        }

        static String lower(String s) {
            return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
        }
    }
}
