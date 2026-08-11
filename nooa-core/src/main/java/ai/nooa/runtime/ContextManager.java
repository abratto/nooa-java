package ai.nooa.runtime;

import ai.nooa.Agent;
import ai.nooa.context.ContextBlock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages context block state. Blocks are keyed and rendered into
 * the system prompt before each LLM call.
 *
 * <p>Three kinds of blocks:</p>
 * <ul>
 *   <li><b>Protected</b> — framework-managed (system_prompt, self, state).
 *       Cannot be removed by the user.</li>
 *   <li><b>Static</b> — evaluated once, cached forever.</li>
 *   <li><b>Dynamic</b> — expression re-evaluated each LLM turn.</li>
 * </ul>
 */
public final class ContextManager {

    private final Map<String, ContextBlock> blocks = new LinkedHashMap<>();
    private final Map<String, ContextBlock> protectedBlocks = new LinkedHashMap<>();

    public ContextManager() {
        // Subclasses register protected blocks via registerProtected()
    }

    public void registerProtected(String key, ContextBlock block) {
        protectedBlocks.put(key, block);
    }

    /**
     * Set a user-controlled block. Overrides any existing block with the same key
     * unless it's protected.
     */
    public void put(String key, String value) {
        if (protectedBlocks.containsKey(key)) {
            throw new IllegalArgumentException("Cannot override protected block: " + key);
        }
        blocks.put(key, ContextBlock.staticBlock(key, value));
    }

    /**
     * Set a dynamic block (re-evaluated each LLM turn).
     */
    public void putDynamic(String key, String expression) {
        if (protectedBlocks.containsKey(key)) {
            throw new IllegalArgumentException("Cannot override protected block: " + key);
        }
        blocks.put(key, ContextBlock.dynamicBlock(key, expression));
    }

    /**
     * Remove a user-controlled block. Protected blocks cannot be removed.
     */
    public void remove(String key) {
        if (protectedBlocks.containsKey(key)) {
            throw new IllegalArgumentException("Cannot remove protected block: " + key);
        }
        blocks.remove(key);
    }

    /**
     * Render all blocks (protected + user) into the system prompt.
     * Dynamic blocks are evaluated against the given agent instance.
     */
    public String render(Agent agent) {
        StringBuilder sb = new StringBuilder();

        // Protected blocks first
        for (var entry : protectedBlocks.entrySet()) {
            String rendered = renderBlock(entry.getValue(), agent);
            if (!rendered.isEmpty()) {
                sb.append("<").append(entry.getKey()).append(">\n");
                sb.append(rendered).append("\n");
                sb.append("</").append(entry.getKey()).append(">\n\n");
            }
        }

        // User blocks
        for (var entry : blocks.entrySet()) {
            String rendered = renderBlock(entry.getValue(), agent);
            if (!rendered.isEmpty()) {
                sb.append("<").append(entry.getKey()).append(">\n");
                sb.append(rendered).append("\n");
                sb.append("</").append(entry.getKey()).append(">\n\n");
            }
        }

        return sb.toString().stripTrailing();
    }

    private String renderBlock(ContextBlock block, Agent agent) {
        return switch (block) {
            case ContextBlock.Static s -> s.value();
            case ContextBlock.Dynamic d -> agent.runtime().evaluateExpression(d.expression());
        };
    }

    public Map<String, ContextBlock> allBlocks() {
        Map<String, ContextBlock> all = new LinkedHashMap<>();
        all.putAll(protectedBlocks);
        all.putAll(blocks);
        return Map.copyOf(all);
    }
}
