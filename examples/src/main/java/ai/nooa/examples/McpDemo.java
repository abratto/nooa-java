package ai.nooa.examples;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Discovers and calls the MCP filesystem server over stdio. */
public final class McpDemo {
    private McpDemo() {}

    public static void main(String[] args) {
        try (var mcp = new ai.nooa.mcp.McpManager()) {
            mcp.connectStdio("filesystem", List.of(
                "npx", "-y", "@modelcontextprotocol/server-filesystem",
                System.getProperty("user.home")));
            System.out.println("Connected MCP servers: " + mcp.serverNames());
            System.out.println("Discovered tools: " + mcp.toolsFor("filesystem").size());
            var path = Path.of(System.getProperty("user.home"), "nooa-mcp-test.txt").toString();
            System.out.println(mcp.callTool("filesystem", "read_file", Map.of("path", path)));
        } catch (Exception exception) {
            System.err.println("MCP demo skipped: install Node.js and @modelcontextprotocol/server-filesystem");
        }
    }
}