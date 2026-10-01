package ai.nooa.eval;

import java.util.List;

/**
 * Reliability metrics derived from repeated trials of a case (or aggregated
 * across cases).
 *
 * <ul>
 *   <li>{@code passAt1} — mean per-trial success probability (capability).</li>
 *   <li>{@code passAtK} — probability at least one of k trials succeeds
 *       (optimistic bound).</li>
 *   <li>{@code passHatK} — probability all k trials succeed (consistency /
 *       reliability; the primary metric for side-effecting or customer-facing
 *       agents).</li>
 *   <li>{@code flakiness} — fraction of cases whose outcome is mixed across
 *       trials.</li>
 * </ul>
 */
public record CompletionMetrics(
    double passAt1,
    double passAtK,
    double passHatK,
    double flakiness) {

    /** Metrics for one case's trial outcomes. */
    public static CompletionMetrics fromTrials(List<Boolean> trials) {
        int n = trials.size();
        if (n == 0) {
            return new CompletionMetrics(0, 0, 0, 0);
        }
        int c = 0;
        for (boolean ok : trials) {
            if (ok) {
                c++;
            }
        }
        double passAt1 = (double) c / n;
        double passAtK = c > 0 ? 1.0 : 0.0;
        double passHatK = c == n ? 1.0 : 0.0;
        double flakiness = (c > 0 && c < n) ? 1.0 : 0.0;
        return new CompletionMetrics(passAt1, passAtK, passHatK, flakiness);
    }

    /** Average a set of per-case metrics into a dataset-level summary. */
    public static CompletionMetrics aggregate(List<CompletionMetrics> perCase) {
        if (perCase.isEmpty()) {
            return new CompletionMetrics(0, 0, 0, 0);
        }
        double p1 = 0, pk = 0, phk = 0, fl = 0;
        for (CompletionMetrics m : perCase) {
            p1 += m.passAt1();
            pk += m.passAtK();
            phk += m.passHatK();
            fl += m.flakiness();
        }
        int n = perCase.size();
        return new CompletionMetrics(p1 / n, pk / n, phk / n, fl / n);
    }
}
