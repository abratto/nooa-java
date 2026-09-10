package ai.nooa.examples;

import ai.nooa.AgentFactory;
import ai.nooa.llm.UnifiedLLM;

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
