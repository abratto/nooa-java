package ai.nooa.eval;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A single evaluation case: which method to call, with what inputs, and what
 * counts as success.
 *
 * @param id           stable case identifier
 * @param targetMethod agent method to invoke
 * @param input        argument values in parameter order
 * @param expected     expected return value, or {@code null} when only
 *                     {@code expectedGoal} (or no goal at all) applies
 * @param expectedGoal post-run field/value goal state for goal verification
 * @param milestones   optional weighted subgoals for partial credit
 * @param tags         dataset facets, e.g. {@code canonical}, {@code paraphrase},
 *                     {@code edge}, {@code ambiguous}
 * @param metadata     free-form annotations carried through to reports
 */
public record EvalCase(
    String id,
    String targetMethod,
    Map<String, Object> input,
    Object expected,
    Map<String, Object> expectedGoal,
    List<Milestone> milestones,
    Set<String> tags,
    Map<String, Object> metadata) {

    /** A weighted subgoal used for partial credit. */
    public record Milestone(String name, double weight, Map<String, Object> expected) {
        public Milestone {
            expected = expected == null ? Map.of() : Map.copyOf(expected);
        }
    }

    public EvalCase {
        input = input == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(input));
        expectedGoal = expectedGoal == null ? Map.of() : Map.copyOf(expectedGoal);
        milestones = milestones == null ? List.of() : List.copyOf(milestones);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Convenience for the common case of a single expected return value. */
    public static EvalCase of(String id, String targetMethod,
                              Map<String, Object> input, Object expected) {
        return new EvalCase(id, targetMethod, input, expected,
            Map.of(), List.of(), Set.of(), Map.of());
    }
}
