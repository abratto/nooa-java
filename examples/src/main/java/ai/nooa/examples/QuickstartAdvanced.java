package ai.nooa.examples;

import ai.nooa.AgentFactory;
import ai.nooa.llm.UnifiedLLM;

/**
 * Context and visibility: how the model sees agent state.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ol>
 *   <li>{@link ResearchAgent} (Example 05: Progressive Disclosure) — helper
 *       methods as model-callable tools; the model pulls facts on demand
 *       instead of receiving everything up front.</li>
 *   <li>{@link ProjectAgent} (Example 07: Dynamic Context Blocks) — business
 *       state in Java, rendered into the prompt as a compact summary that
 *       re-evaluates before every call.</li>
 *   <li>{@link DebugAgent} (Example 08: Context Blocks) — a static
 *       {@code focus} block steering the model, plus inspection of the
 *       registered block names via {@code contextManager().allBlocks()}.</li>
 * </ol>
 *
 * <p><b>Run</b>:
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.QuickstartAdvanced
 * }</pre>
 * or {@code examples/run.sh QuickstartAdvanced}. Requires a model endpoint
 * (local Ollama by default, see {@link ExampleLLM}).</p>
 */
public final class QuickstartAdvanced {
    public static void main(String[] args) {
        var llm = ExampleLLM.create();

        System.out.println("=== Example 05: Progressive Disclosure ===");
        var researcher = AgentFactory.create(ResearchAgent.class, llm);
        System.out.println("Pattern: helper methods = tools, @Generate method = model capability");
        System.out.println("The model can use search() and getCurrentTime() while producing the answer.");
        System.out.println(researcher.research("What should I know about virtual threads?"));
        researcher.close();

        System.out.println("\n=== Example 07: Dynamic Context Blocks ===");
        var pm = AgentFactory.create(ProjectAgent.class, llm);
        pm.addTask("Write docs");
        pm.addTask("Fix bugs");
        pm.completeTask("Write docs");
        System.out.println("Status: " + pm.formatProjectStatus());
        System.out.println("Pattern: business state is kept in Java; the model sees a compact status summary.");
        System.out.println(pm.planDay());
        pm.close();

        System.out.println("\n=== Example 08: Context Blocks ===");
        var debugger = AgentFactory.create(DebugAgent.class, llm);
        debugger.setFocus("memory leak in auth module");
        System.out.println("Context blocks: " + debugger.contextManager().allBlocks().keySet());
        System.out.println("Pattern: runtime context guides the model without making all state visible as raw fields.");
        System.out.println(debugger.analyze("Requests become slower after several hours."));
        debugger.close();

        System.out.println("\nAll advanced examples instantiated successfully.");
    }
}
