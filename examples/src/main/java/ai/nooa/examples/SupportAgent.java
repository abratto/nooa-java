package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Hidden;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;

import java.util.Map;

@SystemPrompt("You are a support agent. Use the Java helper facts as the source of truth. Never invent stock levels or pricing. If the requested quantity exceeds available stock, state the shortfall clearly and suggest a next step. Keep the answer brief, actionable, and grounded in the provided inventory.")
public class SupportAgent extends Agent {
    @Hidden private final Map<String, Integer> inventory;

    public SupportAgent(UnifiedLLM llm, Map<String, Integer> inventory) {
        super(llm);
        this.inventory = Map.copyOf(inventory);
    }

    int getStock(String item) { return inventory.getOrDefault(item.toLowerCase(), 0); }

    @Generate
    public String checkOrder(String item, int quantity) { throw new UnsupportedOperationException(); }
}
