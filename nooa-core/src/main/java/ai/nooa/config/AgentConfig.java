package ai.nooa.config;

import ai.nooa.strategy.CodeActStrategy;
import ai.nooa.strategy.GenerationStrategy;
import ai.nooa.strategy.MethodConditions;
import ai.nooa.runtime.CallMiddleware;
import ai.nooa.runtime.sandbox.JShellSandbox;
import ai.nooa.runtime.sandbox.SandboxExecutor;
import java.util.List;
import java.lang.reflect.Method;
import java.util.Map;

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
    boolean enableTracing,
    Map<String, GenerationStrategy> methodStrategies,
    Map<String, MethodConditions> methodConditions,
    List<CallMiddleware> middleware,
    SandboxExecutor.Factory sandboxExecutorFactory
) {
    public AgentConfig(GenerationStrategy defaultStrategy,
                       int maxNestingDepth,
                       boolean enableTracing) {
        this(defaultStrategy, maxNestingDepth, enableTracing, Map.of(), Map.of(), List.of(),
            JShellSandbox::new);
    }

    public AgentConfig(GenerationStrategy defaultStrategy,
                       int maxNestingDepth,
                       boolean enableTracing,
                       Map<String, GenerationStrategy> methodStrategies) {
        this(defaultStrategy, maxNestingDepth, enableTracing, methodStrategies, Map.of(), List.of(),
            JShellSandbox::new);
    }

    public AgentConfig {
        methodStrategies = methodStrategies == null ? Map.of() : Map.copyOf(methodStrategies);
        methodConditions = methodConditions == null ? Map.of() : Map.copyOf(methodConditions);
        middleware = middleware == null ? List.of() : List.copyOf(middleware);
        sandboxExecutorFactory = sandboxExecutorFactory == null ? JShellSandbox::new : sandboxExecutorFactory;
    }

    public static AgentConfig defaults() {
        return new AgentConfig(
            new CodeActStrategy(CodeActConfig.defaults()),
            50,
            true,
            Map.of(),
            Map.of(),
            List.of(),
            JShellSandbox::new
        );
    }

    /**
     * Return a copy using the supplied strategy for methods without a method-specific override.
     * The strategy instance is retained; this method does not clone or reset it.
     */
    public AgentConfig withDefaultStrategy(GenerationStrategy strategy) {
        return new AgentConfig(strategy, maxNestingDepth, enableTracing, methodStrategies, methodConditions,
            middleware, sandboxExecutorFactory);
    }

    /**
     * Return a copy with the maximum allowed nested generation depth.
     * A value of zero prevents nested generation; negative values are accepted
     * and behave as an already-exhausted limit.
     */
    public AgentConfig withMaxNestingDepth(int depth) {
        return new AgentConfig(defaultStrategy, depth, enableTracing, methodStrategies, methodConditions,
            middleware, sandboxExecutorFactory);
    }

    /** Return a copy with tracing enabled or disabled for agents using this configuration. */
    public AgentConfig withTracing(boolean tracing) {
        return new AgentConfig(defaultStrategy, maxNestingDepth, tracing, methodStrategies, methodConditions,
            middleware, sandboxExecutorFactory);
    }

    /** Configure a stateful strategy instance for a generated method name. */
    public AgentConfig withStrategy(String methodName, GenerationStrategy strategy) {
        var configured = new java.util.HashMap<>(methodStrategies);
        if (strategy == null) {
            configured.remove(methodName);
        } else {
            configured.put(methodName, strategy);
        }
        return new AgentConfig(defaultStrategy, maxNestingDepth, enableTracing, configured, methodConditions,
            middleware, sandboxExecutorFactory);
    }

    /**
     * Return the strategy override for a generated method, or {@code null} when
     * the default strategy should be used.
     */
    public GenerationStrategy strategyFor(Method method) {
        return methodStrategies.get(method.getName());
    }

    /** Configure pre/post conditions for a generated method name. */
    public AgentConfig withConditions(String methodName, MethodConditions conditions) {
        var configured = new java.util.HashMap<>(methodConditions);
        if (conditions == null) {
            configured.remove(methodName);
        } else {
            configured.put(methodName, conditions);
        }
        return new AgentConfig(defaultStrategy, maxNestingDepth, enableTracing,
            methodStrategies, configured, middleware, sandboxExecutorFactory);
    }

    /**
     * Return the pre/post conditions configured for a generated method, or
     * {@code null} when no conditions override exists.
     */
    public MethodConditions conditionsFor(Method method) {
        return methodConditions.get(method.getName());
    }

    /**
     * Return a copy with middleware invoked in the supplied order.
     * The varargs array is defensively copied; passing {@code null} clears all hooks.
     */
    public AgentConfig withMiddleware(CallMiddleware... hooks) {
        return new AgentConfig(defaultStrategy, maxNestingDepth, enableTracing,
            methodStrategies, methodConditions, hooks == null ? List.of() : List.of(hooks),
            sandboxExecutorFactory);
    }

    /**
     * Return a copy using the supplied sandbox factory. The factory is called
     * when an agent creates its execution sandbox, and may be used per agent.
     */
    public AgentConfig withSandboxExecutor(SandboxExecutor.Factory factory) {
        return new AgentConfig(defaultStrategy, maxNestingDepth, enableTracing,
            methodStrategies, methodConditions, middleware, factory);
    }
}
