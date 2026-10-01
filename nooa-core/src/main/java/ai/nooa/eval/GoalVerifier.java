package ai.nooa.eval;

import java.util.List;

/**
 * Verifies whether an agent reached the goal for a case. Goal verification is
 * the foundation of completion reliability ({@code pass@1}/{@code pass^k}):
 * it inspects the post-run state, not just the returned value.
 */
public interface GoalVerifier {

    /**
     * @param evalCase the case under test
     * @param trace    the captured run
     * @param state    post-run state accessor (agent fields, context, env)
     */
    Completion verify(EvalCase evalCase, RunTrace trace, PostState state);

    /**
     * @param achieved whether the goal was fully met
     * @param fraction partial completion in {@code [0, 1]}
     * @param unmet    names of goal fields that were not satisfied
     */
    record Completion(boolean achieved, double fraction, List<String> unmet) {
        public Completion {
            unmet = unmet == null ? List.of() : List.copyOf(unmet);
        }

        /** Used when a case does not define a goal; treated as neutral success. */
        public static Completion unscored() {
            return new Completion(true, 1.0, List.of());
        }

        public static Completion none(String... unmet) {
            return new Completion(false, 0.0, List.of(unmet));
        }
    }
}
