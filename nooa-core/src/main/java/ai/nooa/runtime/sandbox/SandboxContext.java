package ai.nooa.runtime.sandbox;

import ai.nooa.Agent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared context for JShell sandbox execution. The JShell execution engine
 * runs generated code on a separate thread, so the agent reference, return
 * value, and bound variables must cross threads — plain volatile fields and
 * a concurrent map, not {@link ThreadLocal}.
 */
public final class SandboxContext {

    private static volatile Agent AGENT;
    private static volatile Object RETURN_VALUE;
    private static final Map<String, Object> VARIABLES = new ConcurrentHashMap<>();

    public static void setAgent(Agent agent) {
        AGENT = agent;
    }

    public static Agent getAgent() {
        return AGENT;
    }

    public static void setReturnValue(Object value) {
        RETURN_VALUE = value;
    }

    public static Object consumeReturnValue() {
        Object v = RETURN_VALUE;
        RETURN_VALUE = null;
        return v;
    }

    public static void setVariable(String name, Object value) {
        VARIABLES.put(name, value);
    }

    public static Object getVariable(String name) {
        return VARIABLES.get(name);
    }

    public static void clear() {
        AGENT = null;
        RETURN_VALUE = null;
        VARIABLES.clear();
    }
}
