package app.pathwise.core.questions;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side auto-grading. Pure: the caller supplies the answer key, so correct answers never need to
 * leave the server before a response is submitted.
 */
public final class Grader {
    private Grader() {}

    public enum Type {
        SINGLE_CHOICE, MULTI_CHOICE, NUMERIC, SHORT_TEXT;

        public String dbValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Type fromDb(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    /** What the grader needs to know about a question. */
    public record AnswerKey(Type type, Set<UUID> correctOptions, Set<UUID> allOptions, Double numericAnswer,
                            Double tolerance, List<String> acceptedText) {}

    /** {@code partial} is the credit in [0, 1]; {@code correct} means full credit. */
    public record Result(boolean correct, double partial) {
        static Result of(double partial) {
            double p = Math.max(0, Math.min(1, partial));
            return new Result(p >= 0.999, p);
        }
    }

    /**
     * @param chosenOptions option ids for choice questions (ignored otherwise)
     * @param text          free-text or numeric answer (ignored for choice questions)
     */
    public static Result grade(AnswerKey key, Set<UUID> chosenOptions, String text) {
        return switch (key.type()) {
            case SINGLE_CHOICE -> {
                if (chosenOptions == null || chosenOptions.size() != 1) {
                    yield Result.of(0);
                }
                yield Result.of(key.correctOptions().containsAll(chosenOptions) ? 1 : 0);
            }
            case MULTI_CHOICE -> {
                if (chosenOptions == null || chosenOptions.isEmpty() || !key.allOptions().containsAll(chosenOptions)) {
                    yield Result.of(0);
                }
                // Credit for each correct pick, minus each wrong pick, so "select everything" earns nothing.
                Set<UUID> right = new HashSet<>(chosenOptions);
                right.retainAll(key.correctOptions());
                int wrong = chosenOptions.size() - right.size();
                yield Result.of((double) (right.size() - wrong) / key.correctOptions().size());
            }
            case NUMERIC -> {
                Double value = parseNumber(text);
                if (value == null || key.numericAnswer() == null) {
                    yield Result.of(0);
                }
                double tol = key.tolerance() == null ? 1e-9 : key.tolerance();
                yield Result.of(Math.abs(value - key.numericAnswer()) <= tol ? 1 : 0);
            }
            case SHORT_TEXT -> {
                String given = normalise(text);
                if (given.isEmpty() || key.acceptedText() == null) {
                    yield Result.of(0);
                }
                yield Result.of(key.acceptedText().stream().map(Grader::normalise).anyMatch(given::equals) ? 1 : 0);
            }
        };
    }

    /** Case-, accent- and whitespace-insensitive comparison form; separators like ", " and "," are equal. */
    public static String normalise(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        n = n.toLowerCase(Locale.ROOT).trim();
        n = n.replaceAll("\\s*([,;→>-])\\s*", "$1");
        return n.replaceAll("\\s+", " ");
    }

    static Double parseNumber(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim().replace(",", "");
        if (t.isEmpty() || t.length() > 40) {
            return null;
        }
        try {
            double d = Double.parseDouble(t);
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
