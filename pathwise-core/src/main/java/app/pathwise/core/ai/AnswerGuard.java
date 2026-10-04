package app.pathwise.core.ai;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Hint-mode safety net: does a model reply give away the answer? Compares the reply with the correct option
 * texts and accepted answers. Short answers (numbers, single tokens) must match as whole words, so "1" doesn't
 * flag "10". A flagged reply is regenerated once, then replaced by the authored hint.
 */
public final class AnswerGuard {
    private AnswerGuard() {}

    public static boolean leaks(String reply, List<String> answers) {
        if (reply == null || reply.isBlank()) {
            return false;
        }
        String r = normalise(reply);
        for (String a : answers) {
            if (a == null) {
                continue;
            }
            String n = normalise(a);
            if (n.isEmpty()) {
                continue;
            }
            if (n.length() <= 4 || !n.contains(" ")) {
                if (Pattern.compile("(?<![\\p{L}\\p{N}.])" + Pattern.quote(n) + "(?![\\p{L}\\p{N}])").matcher(r).find()) {
                    return true;
                }
            } else if (r.contains(n)) {
                return true;
            }
        }
        return false;
    }

    static String normalise(String s) {
        String t = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[`*_]", "").replaceAll("\\s+", " ").trim();
        return t.endsWith(".") ? t.substring(0, t.length() - 1) : t;
    }
}
