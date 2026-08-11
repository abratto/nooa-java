package ai.nooa.runtime.sandbox;

import ai.nooa.Agent;

/**
 * Thread-local context for JShell sandbox. Provides generated code
 * with access to the agent instance and return value mechanism.
 */
public final class SandboxContext {

    private static final ThreadLocal<Agent> AGENT = new ThreadLocal<>();
    private static final ThreadLocal<Object> RETURN_VALUE = new ThreadLocal<>();

    public static void setAgent(Agent agent) {
        AGENT.set(agent);
    }

    public static Agent getAgent() {
        return AGENT.get();
    }

    public static void setReturnValue(Object value) {
        RETURN_VALUE.set(value);
    }

    public static Object consumeReturnValue() {
        Object v = RETURN_VALUE.get();
        RETURN_VALUE.remove();
        return v;
    }

    public static void clear() {
        AGENT.remove();
        RETURN_VALUE.remove();
    }
}
