package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Hidden;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;

import java.util.Map;

/**
 * Java owns the facts, the model owns the wording.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>Deterministic helper as source of truth — {@link #getStock(String)}
 *       is a public method, so the runtime documents it in {@code AgentDoc}
 *       and the CodeAct strategy exposes it to generated code via
 *       {@code __agent__.getStock("widget")}; the inventory numbers never
 *       have to be trusted to the model's imagination. Visibility rule:
 *       only public methods are model-callable, so helpers intended for the
 *       model must be public (use {@code @Hidden} to exclude one).</li>
 *   <li>{@code @Hidden} on the {@code inventory} field — the map is excluded
 *       from the model-visible API documentation ({@code AgentDoc}) and from
 *       context state rendering, so the model interacts through the helper
 *       method instead of reading raw state. {@code @Hidden} is a visibility
 *       control, not a security boundary.</li>
 *   <li>Constructor injection through {@code AgentFactory.create(..., extraArgs)}
 *       — the instrumented subclass is built with the same constructor
 *       signature, letting applications supply real dependencies.</li>
 *   <li>Pass-by-reference — the model calls the live agent object; no data is
 *       serialized into the prompt beyond the task description.</li>
 *   <li>Reasoning effort — lookup plus a short grounded answer: {@code "low"}
 *       with a modest output bound.</li>
 * </ul>
 *
 * <p><b>Run</b> via {@link QuickstartExamples} (Example 3):
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.QuickstartExamples
 * }</pre>
 */
@SystemPrompt("You are a support agent. Use the Java helper facts as the source of truth. Never invent stock levels or pricing. If the requested quantity exceeds available stock, state the shortfall clearly and suggest a next step. Keep the answer brief, actionable, and grounded in the provided inventory.")
public class SupportAgent extends Agent {
    @Hidden private final Map<String, Integer> inventory;

    public SupportAgent(UnifiedLLM llm, Map<String, Integer> inventory) {
        super(llm);
        this.inventory = Map.copyOf(inventory);
        // The model must follow the CodeAct protocol and actually call the
        // getStock helper rather than confabulating inventory data, which
        // needs more than minimal effort on local models.
        ExampleLLM.tune(this, "medium", 4096);
    }

    public int getStock(String item) { return inventory.getOrDefault(item.toLowerCase(), 0); }

    @Generate(prompt = "Check the requested order against the helper-provided inventory. State any shortfall clearly and suggest a next step. Keep the answer brief and actionable.")
    public String checkOrder(String item, int quantity) { throw new UnsupportedOperationException(); }
}
