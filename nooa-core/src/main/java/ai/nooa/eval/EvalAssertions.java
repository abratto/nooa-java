package ai.nooa.eval;

import java.util.List;

/**
 * Dependency-free assertions for gating a build on eval results. Each method
 * throws {@link AssertionError}, so it works in JUnit, TestNG, a {@code main},
 * or a Gradle task without coupling to a test framework.
 */
public final class EvalAssertions {

    private EvalAssertions() {}

    /** Fail unless the dataset-level weighted score is at least {@code minWeighted}. */
    public static void assertPass(EvalReport report, double minWeighted) {
        double actual = report.aggregate().weightedMean();
        if (actual < minWeighted) {
            throw new AssertionError("Eval weighted score " + fmt(actual)
                + " is below required " + fmt(minWeighted)
                + " for dataset '" + report.datasetId() + "'");
        }
    }

    /**
     * Fail unless the mean value of {@code scorerName} across all case results
     * is at least {@code min}. Intended for hard gates that must never be
     * masked by the weighted aggregate.
     */
    public static void assertGate(EvalReport report, String scorerName, double min) {
        double sum = 0;
        int count = 0;
        for (EvalReport.CaseReport caseReport : report.cases()) {
            for (Score score : caseReport.scores()) {
                if (score.scorer().equals(scorerName) && score.applicable()) {
                    sum += score.value();
                    count++;
                }
            }
        }
        if (count == 0) {
            throw new AssertionError("Gate scorer '" + scorerName + "' produced no scores");
        }
        double mean = sum / count;
        if (mean < min) {
            throw new AssertionError("Gate '" + scorerName + "' mean " + fmt(mean)
                + " is below required " + fmt(min));
        }
    }

    /** Fail unless every case passed on every trial ({@code pass^k == 1}). */
    public static void assertFullyReliable(EvalReport report) {
        if (report.aggregate().completion().passHatK() < 1.0) {
            throw new AssertionError("Not all cases succeeded on every trial (pass^k < 1)");
        }
    }

    /**
     * Fail when any tracked metric regresses beyond {@code tolerance} relative
     * to {@code baseline}.
     */
    public static void assertNoRegression(EvalReport current, EvalBaseline baseline,
                                          double tolerance) {
        if (baseline.regressed(current, tolerance)) {
            var deltas = baseline.deltas(current);
            var worst = deltas.entrySet().stream()
                .min(java.util.Map.Entry.comparingByValue())
                .orElseThrow();
            throw new AssertionError("Regression detected: " + worst.getKey()
                + " = " + fmt(worst.getValue()) + " (tolerance " + fmt(tolerance) + ")");
        }
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }
}
