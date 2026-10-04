package app.pathwise.core.assessment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Classical item analysis over graded attempts (standard test statistics, nothing proprietary):
 * <ul>
 *   <li><b>Difficulty index p</b>: mean credit on the item (1 = everyone right).</li>
 *   <li><b>Discrimination index D</b>: mean credit of the top 27% of attempts by total score, minus that
 *       of the bottom 27%. Items students who do well get right, and who do poorly get wrong, score high.</li>
 *   <li><b>Distractor counts</b>: how often each option was chosen.</li>
 * </ul>
 * With fewer than 4 attempts D is not meaningful and is reported as null.
 */
public final class ItemAnalysis {
    private ItemAnalysis() {}

    /** One student's graded attempt: total score and credit + chosen options per item id. */
    public record Attempt(double total, Map<String, Double> credit, Map<String, List<String>> chosen) {}

    public record ItemStats(String itemId, int responses, double difficulty, Double discrimination,
                            Map<String, Integer> optionCounts, String flag) {}

    public static List<ItemStats> analyse(List<String> itemIds, List<Attempt> attempts) {
        List<Attempt> sorted = new ArrayList<>(attempts);
        sorted.sort(Comparator.comparingDouble(Attempt::total).reversed());
        int group = (int) Math.round(sorted.size() * 0.27);
        List<Attempt> upper = group == 0 ? List.of() : sorted.subList(0, group);
        List<Attempt> lower = group == 0 ? List.of() : sorted.subList(sorted.size() - group, sorted.size());

        List<ItemStats> out = new ArrayList<>();
        for (String item : itemIds) {
            int n = 0;
            double sum = 0;
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Attempt a : attempts) {
                Double c = a.credit().get(item);
                if (c == null) {
                    continue;
                }
                n++;
                sum += c;
                for (String o : a.chosen().getOrDefault(item, List.of())) {
                    counts.merge(o, 1, Integer::sum);
                }
            }
            double p = n == 0 ? 0 : round(sum / n);
            Double d = attempts.size() < 4 || group == 0 ? null : round(mean(upper, item) - mean(lower, item));
            out.add(new ItemStats(item, n, p, d, counts, flag(n, p, d)));
        }
        return out;
    }

    /** A plain-language hint for the teacher. */
    static String flag(int n, double p, Double d) {
        if (n == 0) {
            return "No responses yet";
        }
        if (d != null && d < 0) {
            return "Stronger students did worse on this item: check the answer key and wording";
        }
        if (p >= 0.9) {
            return "Very easy";
        }
        if (p <= 0.3) {
            return "Very hard: consider reteaching";
        }
        if (d != null && d < 0.2) {
            return "Doesn't separate strong from weak responses well";
        }
        return "OK";
    }

    private static double mean(List<Attempt> group, String item) {
        return group.stream().mapToDouble(a -> a.credit().getOrDefault(item, 0.0)).average().orElse(0);
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
