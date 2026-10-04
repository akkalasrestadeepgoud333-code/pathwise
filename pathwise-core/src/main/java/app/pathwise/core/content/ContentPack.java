package app.pathwise.core.content;

import java.util.List;
import java.util.Map;

/**
 * A versioned bundle of curriculum and learning content, authored as YAML and imported idempotently.
 * Keys (not database ids) identify everything, so re-importing an edited pack updates rows in place.
 */
public record ContentPack(
        String key,
        int version,
        Catalog catalog,
        List<TopicDef> topics,
        List<ModuleDef> modules,
        List<QuestionDef> questions,
        List<AssessmentDef> assessments,
        List<GameDef> games) {

    public ContentPack(String key, int version, Catalog catalog, List<TopicDef> topics, List<ModuleDef> modules) {
        this(key, version, catalog, topics, modules, List.of(), List.of(), List.of());
    }

    public ContentPack(String key, int version, Catalog catalog, List<TopicDef> topics, List<ModuleDef> modules,
                       List<QuestionDef> questions) {
        this(key, version, catalog, topics, modules, questions, List.of(), List.of());
    }

    public ContentPack(String key, int version, Catalog catalog, List<TopicDef> topics, List<ModuleDef> modules,
                       List<QuestionDef> questions, List<AssessmentDef> assessments) {
        this(key, version, catalog, topics, modules, questions, assessments, List.of());
    }

    public List<GameDef> gamesOrEmpty() {
        return games == null ? List.of() : games;
    }

    /**
     * A playable game: one template plus its config. Exactly one config field is set, matching the template.
     * Game results are informal evidence and never count toward assessments.
     */
    public record GameDef(String key, String template, String title, String description, String topic,
                          Integer difficulty, Integer estMinutes,
                          app.pathwise.core.games.TraversalRace.Config traversalRace,
                          app.pathwise.core.games.MatchPairs.Config matchPairs,
                          app.pathwise.core.games.BugHunt.Config bugHunt) {
        /** The config for this game's template, or null if missing. */
        public Object config() {
            return switch (template == null ? "" : template) {
                case app.pathwise.core.games.TraversalRace.KEY -> traversalRace;
                case app.pathwise.core.games.MatchPairs.KEY -> matchPairs;
                case app.pathwise.core.games.BugHunt.KEY -> bugHunt;
                default -> null;
            };
        }
    }

    public List<AssessmentDef> assessmentsOrEmpty() {
        return assessments == null ? List.of() : assessments;
    }

    /** An official assessment built from bank questions. */
    public record AssessmentDef(String key, String kind, String title, String instructions, String topic,
                                Integer timeLimitMinutes, Integer maxAttempts, Boolean shuffle, String aiPolicy,
                                String feedback, List<String> questions) {
        public List<String> questionsOrEmpty() {
            return questions == null ? List.of() : questions;
        }
    }

    public List<QuestionDef> questionsOrEmpty() {
        return questions == null ? List.of() : questions;
    }

    public record OptionDef(String text, Boolean correct, String why) {
        public boolean isCorrect() {
            return Boolean.TRUE.equals(correct);
        }
    }

    /**
     * A bank question. Choice types use {@code options}; {@code numeric} uses {@code answer} and
     * {@code tolerance}; {@code short_text} uses {@code accepted}. Hints are revealed one at a time.
     */
    public record QuestionDef(String key, String type, List<String> skills, Integer difficulty, String stem,
                              List<OptionDef> options, Double answer, Double tolerance, List<String> accepted,
                              List<String> hints, String explanation, Boolean practice) {
        /** False for assessment-only items, which never appear in practice. */
        public boolean practiceEnabled() {
            return practice == null || practice;
        }

        public List<String> skillsOrEmpty() {
            return skills == null ? List.of() : skills;
        }

        public List<OptionDef> optionsOrEmpty() {
            return options == null ? List.of() : options;
        }

        public List<String> hintsOrEmpty() {
            return hints == null ? List.of() : hints;
        }
    }

    public record NodeDef(String key, String title, String description) {}

    public record Catalog(NodeDef level, NodeDef program, NodeDef course, NodeDef subject) {}

    public record SkillDef(String key, String title, String description, Boolean core) {
        public boolean isCore() {
            return core == null || core;
        }
    }

    public record PrereqDef(String topic, String strength) {
        public String strengthOrDefault() {
            return strength == null ? "required" : strength;
        }
    }

    public record TopicDef(
            String key,
            String title,
            String summary,
            String parent,
            Boolean optional,
            Boolean advanced,
            List<SkillDef> skills,
            List<PrereqDef> prerequisites) {

        public List<SkillDef> skillsOrEmpty() {
            return skills == null ? List.of() : skills;
        }

        public List<PrereqDef> prerequisitesOrEmpty() {
            return prerequisites == null ? List.of() : prerequisites;
        }
    }

    public record CardDef(String front, String back, String skill) {}

    public record LinkDef(String title, String url, String kind, String licence) {}

    /**
     * One section. Which fields apply depends on {@code kind}:
     * text kinds use {@code markdown}; {@code visual} uses {@code visual}+{@code alt};
     * {@code flashcards} uses {@code cards}; {@code resources} uses {@code links}; {@code check} uses {@code questions}.
     */
    public record SectionDef(
            String key,
            String kind,
            String title,
            Boolean required,
            String markdown,
            Map<String, Object> visual,
            String alt,
            List<CardDef> cards,
            List<LinkDef> links,
            List<String> questions) {

        public SectionDef(String key, String kind, String title, Boolean required, String markdown,
                          Map<String, Object> visual, String alt, List<CardDef> cards, List<LinkDef> links) {
            this(key, kind, title, required, markdown, visual, alt, cards, links, null);
        }

        public List<String> questionsOrEmpty() {
            return questions == null ? List.of() : questions;
        }

        public boolean isRequired() {
            return required == null || required;
        }
    }

    public record ModuleDef(
            String key,
            String topic,
            String title,
            String summary,
            List<String> objectives,
            Integer estMinutes,
            Integer difficulty,
            List<String> skills,
            List<SectionDef> sections) {

        public List<String> objectivesOrEmpty() {
            return objectives == null ? List.of() : objectives;
        }

        public List<String> skillsOrEmpty() {
            return skills == null ? List.of() : skills;
        }

        public List<SectionDef> sectionsOrEmpty() {
            return sections == null ? List.of() : sections;
        }
    }
}
