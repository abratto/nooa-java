package ai.nooa.eval;

import java.util.Map;

/**
 * Result of applying a single {@link Scorer} to one run.
 *
 * @param scorer the scorer name (see {@link Scorer#name()})
 * @param value  normalized score in {@code [0, 1]}
 * @param passed whether the scorer's pass condition held
 * @param detail human-readable explanation, typically the failure reason
 * @param data   optional structured payload for reporting/analysis
 */
public record Score(String scorer, double value, boolean passed,
                    String detail, Map<String, Object> data) {

    public Score {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static Score of(String scorer, boolean passed, String detail) {
        return new Score(scorer, passed ? 1.0 : 0.0, passed, detail, Map.of());
    }

    public static Score of(String scorer, double value, boolean passed, String detail) {
        return new Score(scorer, value, passed, detail, Map.of());
    }

    /**
     * A score that does not apply to the case (e.g. a tool-use scorer on a case
     * that declares no required tools). Excluded from rubric aggregation and
     * gate evaluation.
     */
    public static Score notApplicable(String scorer, String reason) {
        return new Score(scorer, 1.0, true, reason, Map.of("applicable", false));
    }

    /** Whether this score contributes to aggregates. */
    public boolean applicable() {
        return !Boolean.FALSE.equals(data.get("applicable"));
    }
}
