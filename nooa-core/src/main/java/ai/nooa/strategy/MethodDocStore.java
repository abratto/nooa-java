package ai.nooa.strategy;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores method Javadoc captured at instrumentation time by AgentFactory.
 * Javadoc is not available at runtime via reflection, so we capture it
 * during class loading and store it here.
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
