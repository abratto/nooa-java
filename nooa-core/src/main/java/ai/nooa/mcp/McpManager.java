package ai.nooa.mcp;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiple MCP servers and their tools. Attach to an agent
 * to give it access to MCP capabilities.
 *
 * <pre>{@code
 * var mcp = new McpManager()
 *     .connectStdio("filesystem", List.of("npx", "-y",
 *         "@modelcontextprotocol/server-filesystem", "/tmp"))
 *     .connectStdio("github", List.of("npx", "-y",
 *         "@modelcontextprotocol/server-github"));
 *
 * // In generated code:
 * var tools = mcp.allTools();
 * var result = mcp.callTool("filesystem", "read_file", Map.of("path", "/tmp/x.txt"));
 * }</pre>
 */
public final class McpManager implements Closeable {

    private final Map<String, ServerEntry> servers = new ConcurrentHashMap<>();

    record ServerEntry(McpClient client, List<McpToolSpec> tools) {}

    /** Connect to a stdio MCP server. */
    public McpManager connectStdio(String serverName, List<String> command) {
        var client = McpClient.stdio(command);
        var tools = client.discoverTools();
        servers.put(serverName, new ServerEntry(client, tools));
        return this;
    }

    /** Connect to a remote SSE MCP server. */
    public McpManager connectSse(String serverName, String url) {
        var client = McpClient.sse(url);
        var tools = client.discoverTools();
        servers.put(serverName, new ServerEntry(client, tools));
        return this;
    }

    /** Get all tools across all servers as NOOA Tool definitions. */
    public List<ai.nooa.llm.Tool> allTools() {
        List<ai.nooa.llm.Tool> result = new ArrayList<>();
        for (var entry : servers.entrySet()) {
            for (var spec : entry.getValue().tools()) {
                var tool = new McpTool(entry.getKey(), spec.name(), spec,
                    entry.getValue().client());
                result.add(tool.toTool());
            }
        }
        return result;
    }

    /** Get all McpTool instances for direct calling. */
    public List<McpTool> allMcpTools() {
        List<McpTool> result = new ArrayList<>();
        for (var entry : servers.entrySet()) {
            for (var spec : entry.getValue().tools()) {
                result.add(new McpTool(entry.getKey(), spec.name(), spec,
                    entry.getValue().client()));
            }
        }
        return result;
    }

    /** Call a tool on a specific server. */
    public Object callTool(String serverName, String toolName, Map<String, Object> args) {
        var entry = servers.get(serverName);
        if (entry == null) throw new NoSuchElementException(
            "MCP server not found: " + serverName);
        return entry.client().callTool(toolName, args);
    }

    /** List all server names. */
    public Set<String> serverNames() {
        return Collections.unmodifiableSet(servers.keySet());
    }

    /** List tools for a specific server. */
    public List<McpToolSpec> toolsFor(String serverName) {
        var entry = servers.get(serverName);
        return entry != null ? List.copyOf(entry.tools()) : List.of();
    }

    @Override
    public void close() {
        for (var entry : servers.values()) {
            try { entry.client().close(); } catch (Exception ignored) {}
        }
        servers.clear();
    }
}
