package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.memory.MemorySkill;
import ai.nooa.memory.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// =========================================================================
// 10 — Memory: Write, recall, reflect, relationships
// =========================================================================

class MemoryDemoAgent extends Agent {
    private static final String PREFERENCE_TAG = "preference";
    private static final Logger log = LoggerFactory.getLogger(MemoryDemoAgent.class);
    private final MemorySkill memory;

    public MemoryDemoAgent(UnifiedLLM llm) {
        super(llm);
        var store = new MemoryStore(".nooa-demo-memory.db");
        store.scheduleReflection(300); // merge/prune every 5 min
        this.memory = new MemorySkill(this, store);
    }

    // Memory operations called from orchestrator (not @Generate)
    void seedMemories() {
        memory.write(PREFERENCE_TAG, "User prefers dark mode in editor", 0.8,
            List.of(PREFERENCE_TAG, "ui", "editor"));
        memory.write("fact", "Project uses Java 21 with virtual threads", 0.9,
            List.of("tech", "java", "project"));
        memory.write("episode", "Fixed NullPointerException in AuthService.login()", 0.7,
            List.of("bugfix", "auth", "java"));
        memory.write("insight", "Virtual threads eliminated 90% of CompletableFuture usage", 0.85,
            List.of("tech", "java", "performance"));
        memory.write(PREFERENCE_TAG, "User wants error messages in plain English, not stacktraces", 0.6,
            List.of(PREFERENCE_TAG, "ui", "error"));
    }

    void demonstrate() {
        log.info("=== 10: Memory ===");

        // Write records with typed importance and tags
        seedMemories();

        // Recall relevant records by tag intersection
        var techMemories = memory.recall(List.of("tech", "java"), 5);
        log.info("\nTech memories ({}):", techMemories.size());
        for (var m : techMemories) {
            var importance = String.format(Locale.ROOT, "%.2f", m.importance());
            log.info("  [{}] {} (importance: {})", m.type(), m.content(), importance);
        }

        // Query by type
        var preferences = memory.query(PREFERENCE_TAG, null, 10);
        log.info("\nPreferences ({}):", preferences.size());
        preferences.forEach(p -> log.info("  - {}", p.content()));

        // Create relationships between records
        if (techMemories.size() >= 2) {
            var r1 = techMemories.getFirst();
            var r2 = techMemories.get(1);
            memory.relate(r1.id().toString(), "supports", r2.id().toString());
            log.info("\nLinked: {} → supports → {}", r1.type(), r2.type());
        }

        // Forget a record
        if (!preferences.isEmpty()) {
            var toForget = preferences.getFirst();
            memory.forget(toForget.id().toString());
            var stillActive = memory.recall(List.of(PREFERENCE_TAG), 5);
            log.info("\nAfter forget: {} active preferences (was {})", stillActive.size(), preferences.size());
        }

        // Run reflection — merges duplicates, prunes stale
        memory.reflect();

        // Show what remains
        var allActive = memory.query(null, null, 20);
        log.info("\nActive records after reflection: {}", allActive.size());
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
    private static final String FILESYSTEM_SERVER = "filesystem";
    private static final String MCP_TEST_PATH = "nooa-mcp-test.txt";
    private static final Logger log = LoggerFactory.getLogger(Examples10to11.class);

    public static void main(String[] args) {
        if (args.length > 0) {
            log.info("Demo args provided: {}", args.length);
        }

        var llm = UnifiedLLM.create(
            UnifiedLLM.openAI("sk-demo", "https://api.openai.com/v1")
                .model("gpt-4o").build());

        var memoryAgent = new MemoryDemoAgent(llm);
        memoryAgent.demonstrate();
        memoryAgent.close();

        // ---- 11: MCP Integration ----
        log.info("\n=== 11: MCP Integration ===");

        try (var mcp = new ai.nooa.mcp.McpManager()) {
            mcp.connectStdio(FILESYSTEM_SERVER, List.of(
                "npx", "-y",
                "@modelcontextprotocol/server-filesystem",
                System.getProperty("user.home")));

            log.info("Connected to MCP server: {}", mcp.serverNames());

            var tools = mcp.toolsFor(FILESYSTEM_SERVER);
            log.info("Discovered tools: {}", tools.size());
            for (var tool : tools) {
                log.info("  - {}: {}", tool.name(), tool.description());
            }

            var result = mcp.callTool(FILESYSTEM_SERVER, "read_file",
                Map.of("path", Path.of(System.getProperty("user.home"), MCP_TEST_PATH).toString()));
            log.info("Tool result: {}", result);
        } catch (Exception _) {
            log.info("MCP demo skipped (install @modelcontextprotocol/server-filesystem)");
        }

        log.info("\nAll examples demonstrated.");
    }
}
