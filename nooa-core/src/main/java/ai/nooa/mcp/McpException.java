package ai.nooa.mcp;

/** Failure raised while connecting to or communicating with an MCP server. */
public class McpException extends RuntimeException {
    public McpException(String message) {
        super(message);
    }
}