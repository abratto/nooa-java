package ai.nooa.strategy;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores runtime method prompts captured at instrumentation time by
 * AgentFactory. Java Javadoc is not available through standard reflection,
 * so Generate.prompt is the source of truth.
 */
public final class MethodDocStore {

    private static final Map<Method, String> DOCS = new ConcurrentHashMap<>();

    public static void put(Method method, String javadoc) {
        DOCS.put(method, javadoc);
    }

    public static String get(Method method) {
        return DOCS.get(method);
    }
}
