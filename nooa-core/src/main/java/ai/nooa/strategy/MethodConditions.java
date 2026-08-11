package ai.nooa.strategy;

import ai.nooa.NooaException;

/**
 * Pre/post-condition validation for generation methods.
 *
 * <pre>{@code
 * var validate = new MethodConditions()
 *     .precondition("Input must not be empty", (agent, args) -> !args[0].toString().isEmpty())
 *     .postcondition("Result must be non-null", (agent, result) -> result != null);
 * }</pre>
 */
public final class MethodConditions {

    private final java.util.List<Condition> preconditions = new java.util.ArrayList<>();
    private final java.util.List<Condition> postconditions = new java.util.ArrayList<>();

    record Condition(String description, java.util.function.BiPredicate<Object, Object> check) {}

    public MethodConditions precondition(String description,
                                          java.util.function.BiPredicate<Object, Object> check) {
        preconditions.add(new Condition(description, check));
        return this;
    }

    public MethodConditions postcondition(String description,
                                           java.util.function.BiPredicate<Object, Object> check) {
        postconditions.add(new Condition(description, check));
        return this;
    }

    /** Run preconditions before generation. Fails fast — no retry. */
    public void checkPreconditions(Object agent, Object[] args) {
        for (var cond : preconditions) {
            if (!cond.check().test(agent, args)) {
                throw new PreconditionError(cond.description());
            }
        }
    }

    /** Run postconditions after generation. Throws InvariantError for retry. */
    public void checkPostconditions(Object agent, Object result) {
        for (var cond : postconditions) {
            if (!cond.check().test(agent, result)) {
                throw new InvariantError(cond.description());
            }
        }
    }

    public static final class PreconditionError extends NooaException {
        public PreconditionError(String msg) { super("Precondition failed: " + msg); }
    }

    /**
     * Special error caught by strategies to trigger a validation retry.
     * Different from PreconditionError — this means "try again to fix it."
     */
    public static final class InvariantError extends NooaException {
        public InvariantError(String msg) { super("Invariant failed: " + msg); }
    }
}
