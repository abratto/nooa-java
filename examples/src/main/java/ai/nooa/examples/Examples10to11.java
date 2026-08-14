package ai.nooa.examples;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class Examples10to11 {
    private static final String FILESYSTEM_SERVER = "filesystem";
    private static final String MCP_TEST_PATH = "nooa-mcp-test.txt";
    private static final Logger log = LoggerFactory.getLogger(Examples10to11.class);

    public static void main(String[] args) {
        if (args.length > 0) {
            log.info("Demo args provided: {}", args.length);
        }

        var llm = ExampleLLM.create();

        var memoryAgent = new MemoryDemoAgent(llm);
        memoryAgent.demonstrate();
        memoryAgent.close();

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
