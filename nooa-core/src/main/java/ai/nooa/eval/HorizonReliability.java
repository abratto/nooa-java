package ai.nooa.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * Reliability decay view: groups case results into step-count buckets so that
 * {@code pass^k} can be read as a function of task length (the Reliability
 * Decay Curve used in long-horizon agent evaluation).
 */
public final class HorizonReliability {

    public record Bucket(String label, int minSteps, int maxSteps,
                         int cases, CompletionMetrics completion) {}

    private HorizonReliability() {}

    /** Buckets: 1 step, 2-3, 4-7, 8+. Empty buckets are omitted. */
    public static List<Bucket> of(List<EvalReport.CaseReport> cases) {
        int[][] ranges = {{1, 1}, {2, 3}, {4, 7}, {8, Integer.MAX_VALUE}};
        List<Bucket> buckets = new ArrayList<>();
        for (int[] range : ranges) {
            List<CompletionMetrics> metrics = new ArrayList<>();
            for (EvalReport.CaseReport report : cases) {
                if (report.steps() >= range[0] && report.steps() <= range[1]) {
                    metrics.add(report.completion());
                }
            }
            if (!metrics.isEmpty()) {
                String label = range[1] == Integer.MAX_VALUE
                    ? range[0] + "+"
                    : String.valueOf(range[0]);
                buckets.add(new Bucket(label, range[0], range[1],
                    metrics.size(), CompletionMetrics.aggregate(metrics)));
            }
        }
        return buckets;
    }
}
