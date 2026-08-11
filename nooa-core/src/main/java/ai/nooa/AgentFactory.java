package ai.nooa;

import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
import ai.nooa.config.CodeActConfig;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.strategy.CodeActStrategy;
import ai.nooa.strategy.CurrentCall;
import ai.nooa.strategy.GenerationStrategy;
import ai.nooa.strategy.MethodDocStore;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Creates instrumented agent instances where {@code @Generate} methods
 * are intercepted and routed through the LLM runtime.
 *
 * <pre>{@code
 * MyAgent agent = AgentFactory.create(MyAgent.class, llm);
 * String result = agent.analyze("topic").get();
 * }</pre>
 */
public final class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    private static final ByteBuddy BYTE_BUDDY = new ByteBuddy()
        .with(net.bytebuddy.dynamic.scaffold.TypeValidation.DISABLED);

    // Cache instrumented subclasses keyed by original class
    private static final Map<Class<?>, Class<?>> INSTRUMENTED_CACHE = new ConcurrentHashMap<>();

    private AgentFactory() {}

    /**
     * Create an instrumented agent instance.
     *
     * @param agentClass  the agent class (must extend Agent, not be abstract)
     * @param llm         the LLM client
     * @param extraArgs   additional constructor args after the LLM
     * @return            instrumented agent instance
     */
    @SuppressWarnings("unchecked")
    public static <T extends Agent> T create(Class<T> agentClass, UnifiedLLM llm, Object... extraArgs) {
        validate(agentClass);
        Class<? extends Agent> instrumented = getOrCreateInstrumented(agentClass);
        return (T) instantiate(instrumented, llm, extraArgs);
    }

    private static void validate(Class<?> agentClass) {
        if (!Agent.class.isAssignableFrom(agentClass)) {
            throw new IllegalArgumentException(
                agentClass.getName() + " must extend Agent");
        }
        if (Modifier.isAbstract(agentClass.getModifiers())) {
            throw new IllegalArgumentException(
                agentClass.getName() + " must not be abstract");
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Agent> getOrCreateInstrumented(Class<?> agentClass) {
        return (Class<? extends Agent>) INSTRUMENTED_CACHE.computeIfAbsent(
            agentClass, AgentFactory::createInstrumented);
    }

    private static Class<?> createInstrumented(Class<?> agentClass) {
        log.info("Instrumenting agent class: {}", agentClass.getName());

        // Capture Javadoc for @Generate methods (not available at runtime via reflection)
        for (Method m : agentClass.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Generate.class)) {
                MethodDocStore.put(m, extractJavadoc(m));
            }
        }

        // Create ByteBuddy subclass with method interceptors for @Generate methods
        var builder = BYTE_BUDDY
            .subclass(agentClass)
            .name(agentClass.getName() + "$Nooa")
            .method(ElementMatchers.isAnnotatedWith(Generate.class))
            .intercept(MethodDelegation.to(GenerateInterceptor.class));

        try {
            return builder.make()
                .load(agentClass.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();
        } catch (Exception e) {
            throw new NooaException(
                "Failed to instrument " + agentClass.getName(), e);
        }
    }

    private static Agent instantiate(Class<? extends Agent> instrumentedClass,
                                      UnifiedLLM llm, Object[] extraArgs) {
        try {
            // Build constructor arg list: UnifiedLLM + extra args
            Object[] allArgs = new Object[1 + extraArgs.length];
            allArgs[0] = llm;
            System.arraycopy(extraArgs, 0, allArgs, 1, extraArgs.length);

            // Find matching constructor
            for (Constructor<?> ctor : instrumentedClass.getDeclaredConstructors()) {
                Class<?>[] paramTypes = ctor.getParameterTypes();
                if (paramTypes.length == allArgs.length) {
                    boolean match = true;
                    for (int i = 0; i < paramTypes.length; i++) {
                        if (allArgs[i] != null
                            && !paramTypes[i].isAssignableFrom(allArgs[i].getClass())) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        ctor.setAccessible(true);
                        return (Agent) ctor.newInstance(allArgs);
                    }
                }
            }
            throw new NooaException(
                "No matching constructor found for " + instrumentedClass.getName());
        } catch (NooaException e) {
            throw e;
        } catch (Exception e) {
            throw new NooaException(
                "Failed to instantiate " + instrumentedClass.getName(), e);
        }
    }

    private static String extractJavadoc(Method method) {
        // Javadoc is not available via reflection at runtime.
        // This method captures whatever comment is accessible at build time.
        // In practice, users run with -parameters and we parse source, or
        // they use a @Prompt annotation as a fallback.
        //
        // For now, return the method name + parameter names as a minimal prompt.
        String name = method.getName();
        String params = Arrays.stream(method.getParameters())
            .map(p -> p.getType().getSimpleName() + " " + p.getName())
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
        return name + "(" + params + ")";
    }

    /**
     * Interceptor bound to @Generate methods. Routes calls through
     * the runtime's generation pipeline.
     */
    public static class GenerateInterceptor {

        /**
         * Intercept @Generate method calls. Submits work to a virtual thread
         * so blocking LLM calls don't occupy platform threads.
         */
        @RuntimeType
        public static Object intercept(
            @This Agent agent,
            @Origin Method method,
            @AllArguments Object[] args,
            @SuperCall Callable<?> superCall
        ) throws Exception {

            GenerationStrategy strategy = resolveStrategy(method, agent);
            CurrentCall call = CurrentCall.fromMethod(method, args);

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                return executor.submit(() -> {
                    if (agent.runtime().isInGenerationSession()) {
                        return agent.runtime().executeTask(strategy, call);
                    } else {
                        return agent.runtime().callPlan(strategy, call);
                    }
                }).get();
            }
        }

        private static GenerationStrategy resolveStrategy(Method method, Agent agent) {
            Strategy strategyAnn = method.getAnnotation(Strategy.class);
            if (strategyAnn != null) {
                try {
                    return strategyAnn.value().getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    log.warn("Failed to instantiate strategy {}: {}",
                        strategyAnn.value().getName(), e.getMessage());
                }
            }
            return agent.config().defaultStrategy();
        }
    }
}
