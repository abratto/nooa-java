package ai.nooa.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default goal-verification policy:
 *
 * <ol>
 *   <li>If the case declares {@code expectedGoal}, every field must match the
 *       post-run state; the completion fraction is the matched proportion.</li>
 *   <li>Otherwise, if the case declares an {@code expected} return value, the
 *       run's output must equal it.</li>
 *   <li>Otherwise the goal is unscored (neutral success).</li>
 * </ol>
 *
 * <p>String comparison is case-sensitive after trimming, so it stays
 * deterministic and free of locale surprises.</p>
 */
public final class DefaultGoalVerifier implements GoalVerifier {

    @Override
    public Completion verify(EvalCase evalCase, RunTrace trace, PostState state) {
        if (evalCase.expectedGoal() != null && !evalCase.expectedGoal().isEmpty()) {
            return verifyFields(evalCase.expectedGoal(), state.fields());
        }
        if (evalCase.expected() != null) {
            boolean match = valuesEqual(trace.output(), evalCase.expected());
            return match
                ? new Completion(true, 1.0, List.of())
                : new Completion(false, 0.0, List.of("output"));
        }
        return Completion.unscored();
    }

    private static Completion verifyFields(Map<String, Object> expected, Map<String, Object> actual) {
        if (actual == null) {
            return new Completion(false, 0.0, new ArrayList<>(expected.keySet()));
        }
        int matched = 0;
        List<String> unmet = new ArrayList<>();
        for (var entry : expected.entrySet()) {
            if (valuesEqual(actual.get(entry.getKey()), entry.getValue())) {
                matched++;
            } else {
                unmet.add(entry.getKey());
            }
        }
        double fraction = expected.isEmpty() ? 1.0 : (double) matched / expected.size();
        return new Completion(unmet.isEmpty(), fraction, unmet);
    }

    private static boolean valuesEqual(Object actual, Object expected) {
        if (actual instanceof String a && expected instanceof String e) {
            return a.strip().equals(e.strip());
        }
        if (actual instanceof Number a && expected instanceof Number e) {
            return Double.compare(a.doubleValue(), e.doubleValue()) == 0;
        }
        return Objects.equals(actual, expected);
    }
}
