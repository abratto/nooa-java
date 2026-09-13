package ai.nooa.examples;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Discovers and calls the MCP filesystem server over stdio.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>{@link ai.nooa.mcp.McpManager#connectStdio} — launches a local MCP
 *       server as a child process and discovers its tools over the stdio
 *       transport.</li>
 *   <li>Tool discovery — {@code serverNames()} and {@code toolsFor(...)}
 *       report what the server offers; {@code allTools()} converts the
 *       discovered tools into NOOA {@code Tool} definitions.</li>
 *   <li>Direct invocation — {@code callTool(server, tool, args)} executes a
 *       tool without a model in the loop, which is useful for testing MCP
 *       wiring before connecting it to an agent.</li>
 *   <li>{@link AutoCloseable} lifecycle — child processes are terminated on
 *       close.</li>
 * </ul>
 *
 * <p><b>Run</b> (requires Node.js/npx; no model call):
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.McpDemo
 * }</pre>
 * or {@code examples/run.sh McpDemo}. The demo writes and reads
 * {@code ~/nooa-mcp-test.txt} through the server's sandbox rooted at the home
 * directory. To wire discovered tools into a model loop, pass
 * {@code mcp.allTools()} into your strategy configuration — see
 * {@link McpDemoAgent} for the agent-side counterpart.</p>
 */
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
            mcp.callTool("filesystem", "write_file",
                Map.of("path", path, "content", "Written by the NOOA MCP demo.\n"));
            System.out.println(mcp.callTool("filesystem", "read_file", Map.of("path", path)));
        } catch (Exception exception) {
            System.err.println("MCP demo skipped: install Node.js and @modelcontextprotocol/server-filesystem");
        }
    }
}
