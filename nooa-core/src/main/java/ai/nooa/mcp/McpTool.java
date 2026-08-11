package ai.nooa.mcp;

import ai.nooa.llm.Tool;

import java.io.Closeable;
import java.util.Map;

/**
 * Agent-callable wrapper for an MCP tool. Each instance manages its own
 * MCP connection. Callable from generated agent code.
 */
public final class McpTool implements Closeable {

    private final String serverName;
    private final String toolName;
    private final McpToolSpec spec;
    private final McpClient client;

    McpTool(String serverName, String toolName, McpToolSpec spec, McpClient client) {
        this.serverName = serverName;
        this.toolName = toolName;
        this.spec = spec;
        this.client = client;
    }

    public String toolName() { return toolName; }
    public String serverName() { return serverName; }
    public McpToolSpec spec() { return spec; }

    /** Call the underlying MCP tool. */
    public Object call(Map<String, Object> arguments) {
        return client.callTool(toolName, arguments);
    }

    /** Convert to a NOOA Tool definition for LLM context. */
    @SuppressWarnings("unchecked")
    public Tool toTool() {
        Map<String, Object> schema = new com.fasterxml.jackson.databind.ObjectMapper()
            .convertValue(spec.inputSchema(), Map.class);
        return new Tool(
            serverName + "__" + toolName,
            spec.description(),
            schema
        );
    }

    @Override
    public void close() {
        client.close();
    }

    @Override
    public String toString() {
        return "McpTool[" + serverName + "/" + toolName + "]";
    }
}
