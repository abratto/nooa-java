package ai.nooa.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP client with connection lifecycle, tool discovery, and tool calling.
 *
 * <pre>{@code
 * var client = McpClient.stdio(List.of("npx", "-y", "@modelcontextprotocol/server-filesystem", "/tmp"));
 * List<McpToolSpec> tools = client.discoverTools();
 * Object result = client.callTool("read_file", Map.of("path", "/tmp/test.txt"));
 * client.close();
 * }</pre>
 */
public final class McpClient implements Closeable {

    private final McpTransport transport;
    private boolean initialized;

    private McpClient(McpTransport transport) {
        this.transport = transport;
    }

    /** Connect to a local MCP server via stdio. */
    public static McpClient stdio(List<String> command) {
        var client = new McpClient(new StdioTransport(command));
        client.initialize();
        return client;
    }

    /** Connect to a remote MCP server via SSE. */
    public static McpClient sse(String url) {
        var client = new McpClient(new SseTransport(url));
        client.initialize();
        return client;
    }

    private void initialize() {
        transport.send(JsonRpcMessage.initializeRequest());
        JsonRpcMessage response = transport.receive();
        if (response == null || response.error() != null) {
            throw new RuntimeException("MCP initialize failed: "
                + (response != null ? response.error() : "timeout"));
        }
        transport.send(JsonRpcMessage.initializedNotification());
        initialized = true;
    }

    /** Discover tools available on this server. */
    public List<McpToolSpec> discoverTools() {
        assertInitialized();
        transport.send(JsonRpcMessage.toolsListRequest());
        JsonRpcMessage response = transport.receive();
        if (response == null || response.error() != null) {
            return List.of();
        }

        List<McpToolSpec> tools = new ArrayList<>();
        JsonNode toolsNode = response.result().path("tools");
        if (toolsNode.isArray()) {
            for (JsonNode toolNode : toolsNode) {
                tools.add(new McpToolSpec(
                    toolNode.path("name").asText(),
                    toolNode.path("description").asText(""),
                    toolNode.path("inputSchema")
                ));
            }
        }
        return tools;
    }

    /** Call a tool by name with arguments. */
    public Object callTool(String toolName, Map<String, Object> arguments) {
        assertInitialized();
        transport.send(JsonRpcMessage.toolsCallRequest(toolName, arguments));
        JsonRpcMessage response = transport.receive();
        if (response == null) {
            throw new RuntimeException("MCP tool call timeout: " + toolName);
        }
        if (response.error() != null) {
            throw new RuntimeException("MCP tool error: " + response.error());
        }

        // Try to extract text content from the result
        JsonNode content = response.result().path("content");
        if (content.isArray() && !content.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : content) {
                String type = block.path("type").asText();
                String text = block.path("text").asText();
                if (!text.isEmpty()) {
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(text);
                }
            }
            return sb.toString();
        }
        return response.result().toString();
    }

    public boolean isConnected() { return transport.isConnected() && initialized; }

    private void assertInitialized() {
        if (!initialized) throw new IllegalStateException("MCP client not initialized");
    }

    @Override
    public void close() {
        try { transport.close(); } catch (java.io.IOException ignored) {}
        initialized = false;
    }
}
