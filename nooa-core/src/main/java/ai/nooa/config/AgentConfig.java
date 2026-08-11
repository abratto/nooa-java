package ai.nooa.config;

import ai.nooa.strategy.CodeActStrategy;
import ai.nooa.strategy.GenerationStrategy;

/**
 * Immutable agent configuration. Create with {@link #defaults()} and
 * customize via the builder.
 *
 * <pre>{@code
 * var config = AgentConfig.defaults()
 *     .withDefaultStrategy(new CodeActStrategy(CodeActConfig.builder()
 *         .maxIterations(15)
 *         .build()));
 * }</pre>
 */
public record AgentConfig(
    GenerationStrategy defaultStrategy,
    int maxNestingDepth,
    boolean enableTracing
) {
    public static AgentConfig defaults() {
        return new AgentConfig(
            new CodeActStrategy(CodeActConfig.defaults()),
            50,
            true
        );
    }

    public AgentConfig withDefaultStrategy(GenerationStrategy strategy) {
        return new AgentConfig(strategy, maxNestingDepth, enableTracing);
    }

    public AgentConfig withMaxNestingDepth(int depth) {
        return new AgentConfig(defaultStrategy, depth, enableTracing);
    }

    public AgentConfig withTracing(boolean tracing) {
        return new AgentConfig(defaultStrategy, maxNestingDepth, tracing);
    }
}
