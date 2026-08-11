package ai.nooa.mcp;

import java.io.Closeable;

/**
 * Transport abstraction for MCP client-server communication.
 */
public interface McpTransport extends Closeable {

    /** Send a JSON-RPC message to the server. */
    void send(JsonRpcMessage message);

    /** Receive the next JSON-RPC message from the server (blocking). */
    JsonRpcMessage receive();

    /** Check if the transport is still connected. */
    boolean isConnected();
}
