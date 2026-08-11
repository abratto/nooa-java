package ai.nooa.mcp;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Specification for an MCP tool discovered from a server.
 */
public record McpToolSpec(String name, String description, JsonNode inputSchema) {

    @Override
    public String toString() {
        return "MCPTool[" + name + "]: " + description;
    }
}
