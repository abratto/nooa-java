package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Hidden;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;

import java.util.ArrayList;
import java.util.List;

@SystemPrompt("You are a helpful assistant. Use available methods.")
class ResearchAgent extends Agent {
    record SearchResult(String title, String url, String snippet) {}
    public ResearchAgent(UnifiedLLM llm) { super(llm); }
    SearchResult search(String query) {
        return new SearchResult(query, "https://example.com/" + query, "Result for: " + query);
    }
    String getCurrentTime() { return java.time.LocalDateTime.now().toString(); }
    @Generate
    public String research(String question) { throw new UnsupportedOperationException(); }
}

@SystemPrompt("You are a project manager.")
class ProjectAgent extends Agent {
    record Task(String name, boolean complete) {}
    @Hidden private final List<Task> tasks = new ArrayList<>();
    public ProjectAgent(UnifiedLLM llm) {
        super(llm);
        context().putDynamic("project_status", "self.formatProjectStatus()");
    }
    public String formatProjectStatus() {
        long done = tasks.stream().filter(Task::complete).count();
        return "Tasks: " + done + "/" + tasks.size() + " complete";
    }
    void addTask(String name) { tasks.add(new Task(name, false)); }
    void completeTask(String name) {
        tasks.stream().filter(t -> t.name().equals(name)).findFirst()
            .ifPresent(t -> tasks.set(tasks.indexOf(t), new Task(name, true)));
    }
    @Generate
    public String planDay() { throw new UnsupportedOperationException(); }
}

@SystemPrompt("You are an AI assistant.")
class DebugAgent extends Agent {
    public DebugAgent(UnifiedLLM llm) { super(llm); }
    void setFocus(String topic) { context().put("focus", "Priority: " + topic); }
    void clearFocus() { context().remove("focus"); }
    @Generate
    public String analyze(String issue) { throw new UnsupportedOperationException(); }
}

public final class QuickstartAdvanced {
    public static void main(String[] args) {
        var llm = UnifiedLLM.create(
            UnifiedLLM.openAI(System.getenv("OPENAI_API_KEY"), "gpt-4o").build());

        System.out.println("=== Example 05: Progressive Disclosure ===");
        var researcher = AgentFactory.create(ResearchAgent.class, llm);
        System.out.println("Agent created. Methods: search, getCurrentTime, research");
        researcher.close();

        System.out.println("\n=== Example 07: Dynamic Context Blocks ===");
        var pm = AgentFactory.create(ProjectAgent.class, llm);
        pm.addTask("Write docs");
        pm.addTask("Fix bugs");
        pm.completeTask("Write docs");
        System.out.println("Status: " + pm.formatProjectStatus());
        pm.close();

        System.out.println("\n=== Example 08: Context Blocks ===");
        var debugger = AgentFactory.create(DebugAgent.class, llm);
        debugger.setFocus("memory leak in auth module");
        System.out.println("Context blocks: " + debugger.contextManager().allBlocks().keySet());
        debugger.close();

        System.out.println("\nAll examples instantiated successfully.");
    }
}
