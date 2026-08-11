package ai.nooa.runtime;

import ai.nooa.Agent;

/**
 * LLM-facing context API. The agent's {@code context()} getter returns
 * this, and the LLM can use it in generated code to manage context blocks.
 *
 * <pre>{@code
 * // In generated code:
 * context().put("focus", "security");
 * context().putDynamic("project_state", "self.formatProjectState()");
 * }</pre>
 */
public final class ContextApi {

    private final Agent agent;

    public ContextApi(Agent agent) {
        this.agent = agent;
    }

    public void put(String key, String value) {
        agent.contextManager().put(key, value);
    }

    public void putDynamic(String key, String expression) {
        agent.contextManager().putDynamic(key, expression);
    }

    public void remove(String key) {
        agent.contextManager().remove(key);
    }

    @Override
    public String toString() {
        return "ContextApi[agent=" + agent.getClass().getSimpleName() + "]";
    }
}
