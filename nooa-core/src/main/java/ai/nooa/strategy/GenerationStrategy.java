package ai.nooa.strategy;

/**
 * Core contract for generation strategies.
 */
public interface GenerationStrategy {

    default String name() { return getClass().getSimpleName(); }

    Object execute(RuntimeServices runtime, CurrentCall call);
}
