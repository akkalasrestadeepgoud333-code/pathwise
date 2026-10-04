package app.pathwise.web.questions;

import app.pathwise.core.questions.Grader;
import app.pathwise.data.Tx;
import app.pathwise.data.questions.QuestionDao;
import app.pathwise.data.questions.QuestionDao.OptionRow;
import app.pathwise.data.questions.QuestionDao.QuestionRow;
import app.pathwise.web.content.Markdown;
import app.pathwise.web.content.SafeHtml;
import app.pathwise.web.platform.Json;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The question bank. Student-facing views never contain answers, rationales or hints that haven't been
 * revealed. Grading happens here, on the server.
 */
public final class QuestionService {
    private final Tx tx;
    private final QuestionDao dao = new QuestionDao();

    public QuestionService(Tx tx) {
        this.tx = tx;
    }

    /** What a student sees before answering. */
    public record OptionView(UUID id, SafeHtml label) {}

    public record QuestionView(UUID id, String key, String type, SafeHtml stem, List<OptionView> options,
                               int hintsAvailable, int difficulty) {
        public boolean isChoice() {
            return type.endsWith("choice");
        }

        public boolean isMulti() {
            return "multi_choice".equals(type);
        }
    }

    /** What a student sees after answering: right/wrong, why, and the explanation. */
    public record OptionFeedback(UUID id, SafeHtml label, boolean correct, boolean chosen, SafeHtml rationale) {}

    public record Feedback(boolean correct, double partial, List<OptionFeedback> options, String given,
                           String expected, SafeHtml explanation) {}

    /** A student's response: chosen option ids (choice types) or text (numeric/short text). */
    public record Response(Set<UUID> options, String text) {
        public String toJson() {
            return Json.write(Map.of("options", options == null ? List.of() : options.stream().map(UUID::toString).sorted().toList(),
                    "text", text == null ? "" : text));
        }

        public static Response fromJson(String json) {
            if (json == null) {
                return new Response(Set.of(), "");
            }
            try {
                JsonNode n = Json.mapper().readTree(json);
                Set<UUID> opts = new HashSet<>();
                n.path("options").forEach(o -> opts.add(UUID.fromString(o.asText())));
                return new Response(opts, n.path("text").asText(""));
            } catch (IOException | IllegalArgumentException e) {
                return new Response(Set.of(), "");
            }
        }
    }

    public List<QuestionRow> byIds(Collection<UUID> ids) {
        return tx.read(c -> dao.byIds(c, ids));
    }

    public List<QuestionRow> byKeys(Collection<String> keys) {
        return tx.read(c -> dao.byKeys(c, keys));
    }

    public List<QuestionRow> forSkills(Collection<UUID> skillIds) {
        return tx.read(c -> dao.forSkills(c, skillIds));
    }

    public Map<UUID, QuestionDao.SkillSupply> supply() {
        return tx.read(dao::supply);
    }

    /**
     * Options in a per-student order. Authors often list the right answer first, so position must not
     * give it away. The order is stable for a given student and question, so reloads don't reshuffle it.
     */
    public static List<OptionRow> shuffled(QuestionRow q, UUID student) {
        long seed = student.getMostSignificantBits() ^ student.getLeastSignificantBits() ^ q.id().getLeastSignificantBits();
        return q.options().stream()
                .sorted(java.util.Comparator.comparingLong((OptionRow o) -> mix(seed ^ o.id().getLeastSignificantBits() ^ o.position())))
                .toList();
    }

    /** SplitMix64 finaliser: a cheap, well-distributed hash for ordering. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    public static QuestionView view(QuestionRow q, UUID student) {
        return new QuestionView(q.id(), q.key(), q.type(), Markdown.render(q.stem()),
                shuffled(q, student).stream().map(o -> new OptionView(o.id(), Markdown.inline(o.label()))).toList(),
                hints(q).size(), q.difficulty());
    }

    public static List<String> hints(QuestionRow q) {
        try {
            return Json.mapper().readValue(q.hintsJson(), new TypeReference<List<String>>() {});
        } catch (IOException e) {
            return List.of();
        }
    }

    public static Grader.Result grade(QuestionRow q, Response r) {
        return Grader.grade(answerKey(q), r.options(), r.text());
    }

    /** Every text that would give the answer away (for the tutor's hint guard): correct options, numeric answer, accepted text. */
    public static List<String> answerTexts(QuestionRow q) {
        List<String> out = new java.util.ArrayList<>();
        q.options().stream().filter(OptionRow::correct).forEach(o -> out.add(o.label()));
        Grader.AnswerKey key = answerKey(q);
        if (key.numericAnswer() != null) {
            double v = key.numericAnswer();
            out.add(v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v));
        }
        if (key.acceptedText() != null) {
            out.addAll(key.acceptedText());
        }
        return out;
    }

    static Grader.AnswerKey answerKey(QuestionRow q) {
        JsonNode spec;
        try {
            spec = Json.mapper().readTree(q.specJson());
        } catch (IOException e) {
            throw new IllegalStateException("Invalid stored question spec for " + q.key(), e);
        }
        Set<UUID> correct = q.options().stream().filter(OptionRow::correct).map(OptionRow::id).collect(Collectors.toSet());
        Set<UUID> all = q.options().stream().map(OptionRow::id).collect(Collectors.toSet());
        Double answer = spec.hasNonNull("answer") ? spec.get("answer").asDouble() : null;
        Double tolerance = spec.hasNonNull("tolerance") ? spec.get("tolerance").asDouble() : null;
        List<String> accepted = spec.has("accepted")
                ? Json.mapper().convertValue(spec.get("accepted"), new TypeReference<List<String>>() {})
                : null;
        return new Grader.AnswerKey(Grader.Type.fromDb(q.type()), correct, all, answer, tolerance, accepted);
    }

    public static Feedback feedback(QuestionRow q, Response r, Grader.Result result, UUID student) {
        List<OptionFeedback> options = shuffled(q, student).stream()
                .map(o -> new OptionFeedback(o.id(), Markdown.inline(o.label()), o.correct(),
                        r.options() != null && r.options().contains(o.id()),
                        o.rationale() == null ? null : Markdown.inline(o.rationale())))
                .toList();
        String expected = null;
        Grader.AnswerKey key = answerKey(q);
        if (key.type() == Grader.Type.NUMERIC) {
            double a = key.numericAnswer();
            expected = a == Math.rint(a) ? Long.toString((long) a) : Double.toString(a);
        } else if (key.type() == Grader.Type.SHORT_TEXT && key.acceptedText() != null && !key.acceptedText().isEmpty()) {
            expected = key.acceptedText().get(0);
        }
        return new Feedback(result.correct(), result.partial(), options, r.text(), expected,
                q.explanation() == null ? null : Markdown.render(q.explanation()));
    }
}
