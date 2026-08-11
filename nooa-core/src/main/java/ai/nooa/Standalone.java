package ai.nooa;

import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.strategy.CodeActStrategy;
import ai.nooa.strategy.CurrentCall;
import ai.nooa.strategy.GenerationStrategy;

import java.lang.reflect.Method;
import java.util.concurrent.Executors;

/**
 * Executes @Generate methods as standalone functions without an Agent class.
 * Each call creates a transient agent — no shared state, no history.
 *
 * <pre>{@code
 * &#64;Generate
 * public static String summarize(String text) { throw ... }
 *
 * var llm = UnifiedLLM.create(UnifiedLLM.openAI(key, "gpt-4o").build());
 * String result = Standalone.call(llm, MyClass.class, "summarize", "long text...");
 * }</pre>
 */
public final class Standalone {

    private Standalone() {}

    /** Call a static @Generate method as a standalone function. */
    @SuppressWarnings("unchecked")
    public static <T> T call(UnifiedLLM llm, Class<?> declaringClass,
                              String methodName, Object... args) {
        try {
            Method method = findMethod(declaringClass, methodName, args);
            if (!method.isAnnotationPresent(Generate.class)) {
                throw new IllegalArgumentException(
                    methodName + " is not annotated with @Generate");
            }

            var agent = new TransientAgent(llm);
            var strategy = resolveStrategy(method);
            var call = CurrentCall.fromMethod(method, args);

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                T result = (T) executor.submit(() ->
                    agent.runtime().callPlan(strategy, call)
                ).get();
                return result;
            } finally {
                agent.close();
            }
        } catch (Exception e) {
            throw new NooaException("Standalone call failed: " + methodName, e);
        }
    }

    private static Method findMethod(Class<?> cls, String name, Object[] args) {
        for (var m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                return m;
            }
        }
        throw new NooaException("Method not found: " + cls.getName() + "." + name);
    }

    private static GenerationStrategy resolveStrategy(Method method) {
        Strategy ann = method.getAnnotation(Strategy.class);
        if (ann != null) {
            try {
                return ann.value().getDeclaredConstructor().newInstance();
            } catch (Exception ignored) {}
        }
        return new CodeActStrategy(ai.nooa.config.CodeActConfig.defaults());
    }

    /** Minimal agent for standalone execution. */
    private static class TransientAgent extends Agent {
        TransientAgent(UnifiedLLM llm) { super(llm); }
    }
}
