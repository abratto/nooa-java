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

    /** Store a plain text block, replacing an existing user block with the same key. */
    public void put(String key, String value) {
        agent.contextManager().put(key, value);
    }

    /** Store a structured block whose value is rendered when context is built. */
    public void put(String key, Object value) {
        agent.contextManager().put(key, value);
    }

    /** Store an expression-backed block that is evaluated before each LLM turn. */
    public void putDynamic(String key, String expression) {
        agent.contextManager().putDynamic(key, expression);
    }

    /** Remove a user block; removing a protected framework block throws an exception. */
    public void remove(String key) {
        agent.contextManager().remove(key);
    }

    @Override
    public String toString() {
        return "ContextApi[agent=" + agent.getClass().getSimpleName() + "]";
    }
}
