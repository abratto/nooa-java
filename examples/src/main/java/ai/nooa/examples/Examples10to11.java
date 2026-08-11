package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.memory.MemorySkill;
import ai.nooa.memory.MemoryStore;
import ai.nooa.memory.MemoryRecord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

// =========================================================================
// 10 — Memory: Write, recall, reflect, relationships
// =========================================================================

class MemoryDemoAgent extends Agent {
    private final MemorySkill memory;

    public MemoryDemoAgent(UnifiedLLM llm) {
        super(llm);
        var store = new MemoryStore(".nooa-demo-memory.db");
        store.scheduleReflection(300); // merge/prune every 5 min
        this.memory = new MemorySkill(this, store);
    }

    // Memory operations called from orchestrator (not @Generate)
    void seedMemories() {
        memory.write("preference", "User prefers dark mode in editor", 0.8,
            List.of("preference", "ui", "editor"));
        memory.write("fact", "Project uses Java 21 with virtual threads", 0.9,
            List.of("tech", "java", "project"));
        memory.write("episode", "Fixed NullPointerException in AuthService.login()", 0.7,
            List.of("bugfix", "auth", "java"));
        memory.write("insight", "Virtual threads eliminated 90% of CompletableFuture usage", 0.85,
            List.of("tech", "java", "performance"));
        memory.write("preference", "User wants error messages in plain English, not stacktraces", 0.6,
            List.of("preference", "ui", "error"));
    }

    void demonstrate() {
        System.out.println("=== 10: Memory ===");

        // Write records with typed importance and tags
        seedMemories();

        // Recall relevant records by tag intersection
        var techMemories = memory.recall(List.of("tech", "java"), 5);
        System.out.println("\nTech memories (" + techMemories.size() + "):");
        for (var m : techMemories) {
            System.out.println("  [" + m.type() + "] " + m.content()
                + " (importance: " + String.format("%.2f", m.importance()) + ")");
        }

        // Query by type
        var preferences = memory.query("preference", null, 10);
        System.out.println("\nPreferences (" + preferences.size() + "):");
        preferences.forEach(p -> System.out.println("  - " + p.content()));

        // Create relationships between records
        if (techMemories.size() >= 2) {
            var r1 = techMemories.get(0);
            var r2 = techMemories.get(1);
            memory.relate(r1.id().toString(), "supports", r2.id().toString());
            System.out.println("\nLinked: " + r1.type() + " → supports → " + r2.type());
        }

        // Forget a record
        if (!preferences.isEmpty()) {
            var toForget = preferences.get(0);
            memory.forget(toForget.id().toString());
            var stillActive = memory.recall(List.of("preference"), 5);
            System.out.println("\nAfter forget: " + stillActive.size()
                + " active preferences (was " + preferences.size() + ")");
        }

        // Run reflection — merges duplicates, prunes stale
        memory.reflect();

        // Show what remains
        var allActive = memory.query(null, null, 20);
        System.out.println("\nActive records after reflection: " + allActive.size());
    }

    public MemorySkill memory() { return memory; }

    @Override public void close() { memory.close(); super.close(); }
}

// =========================================================================
// 11 — MCP Integration: Connect to MCP server, discover and call tools
// =========================================================================

class McpDemoAgent extends Agent {
    public McpDemoAgent(UnifiedLLM llm) { super(llm); }
    @Generate
    public String analyze(String input) { throw new UnsupportedOperationException(); }
}

public final class Examples10to11 {

    public static void main(String[] args) throws Exception {
        var llm = UnifiedLLM.create(
            UnifiedLLM.openAI("sk-demo", "https://api.openai.com/v1")
                .model("gpt-4o").build());

        var memoryAgent = new MemoryDemoAgent(llm);
        memoryAgent.demonstrate();
        memoryAgent.close();

        // ---- 11: MCP Integration ----
        System.out.println("\n=== 11: MCP Integration ===");

        try (var mcp = new ai.nooa.mcp.McpManager()) {
            mcp.connectStdio("filesystem", List.of(
                "npx", "-y",
                "@modelcontextprotocol/server-filesystem",
                "/tmp"));

            System.out.println("Connected to MCP server: " + mcp.serverNames());

            var tools = mcp.toolsFor("filesystem");
            System.out.println("Discovered tools: " + tools.size());
            for (var tool : tools) {
                System.out.println("  - " + tool.name() + ": " + tool.description());
            }

            var result = mcp.callTool("filesystem", "read_file",
                Map.of("path", "/tmp/nooa-mcp-test.txt"));
            System.out.println("Tool result: " + result);
        } catch (Exception e) {
            System.out.println("MCP demo skipped (install @modelcontextprotocol/server-filesystem)");
        }

        System.out.println("\nAll examples demonstrated.");
    }
}
