package ai.nooa.mcp;

/**
 * Mock MCP server for testing. Reads JSON-RPC requests from stdin
 * and writes responses to stdout. Run as a subprocess.
 *
 * <p>Supports: initialize, tools/list, tools/call (echo only).
 */
public final class MockMcpServer {
    public static void main(String[] args) throws Exception {
        var reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(System.in));
        var writer = new java.io.BufferedWriter(
            new java.io.OutputStreamWriter(System.out));

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            try {
                var msg = JsonRpcMessage.deserialize(line);

                if ("initialize".equals(msg.method())) {
                    var result = JsonRpcMessage.JSON.createObjectNode();
                    result.put("protocolVersion", "2024-11-05");
                    var caps = result.putObject("capabilities");
                    caps.putObject("tools").put("listChanged", false);
                    var info = result.putObject("serverInfo");
                    info.put("name", "mock-server");
                    info.put("version", "1.0.0");

                    var resp = JsonRpcMessage.response(msg.id(), result);
                    writer.write(resp.serialize());
                    writer.newLine();
                    writer.flush();
                } else if ("tools/list".equals(msg.method())) {
                    var result = JsonRpcMessage.JSON.createObjectNode();
                    var tools = result.putArray("tools");

                    var tool1 = tools.addObject();
                    tool1.put("name", "echo");
                    tool1.put("description", "Echo back the input");
                    var schema1 = tool1.putObject("inputSchema");
                    schema1.put("type", "object");
                    var props1 = schema1.putObject("properties");
                    props1.putObject("message")
                        .put("type", "string")
                        .put("description", "Message to echo");

                    var tool2 = tools.addObject();
                    tool2.put("name", "add");
                    tool2.put("description", "Add two numbers");
                    var schema2 = tool2.putObject("inputSchema");
                    schema2.put("type", "object");
                    var props2 = schema2.putObject("properties");
                    props2.putObject("a").put("type", "number");
                    props2.putObject("b").put("type", "number");

                    var resp = JsonRpcMessage.response(msg.id(), result);
                    writer.write(resp.serialize());
                    writer.newLine();
                    writer.flush();
                } else if ("tools/call".equals(msg.method())) {
                    String toolName = msg.params().path("name").asText();
                    var result = JsonRpcMessage.JSON.createObjectNode();
                    var content = result.putArray("content");

                    if ("echo".equals(toolName)) {
                        String message = msg.params().path("arguments")
                            .path("message").asText("no message");
                        var block = content.addObject();
                        block.put("type", "text");
                        block.put("text", "ECHO: " + message);
                    } else if ("add".equals(toolName)) {
                        int a = msg.params().path("arguments").path("a").asInt(0);
                        int b = msg.params().path("arguments").path("b").asInt(0);
                        var block = content.addObject();
                        block.put("type", "text");
                        block.put("text", String.valueOf(a + b));
                    }

                    var resp = JsonRpcMessage.response(msg.id(), result);
                    writer.write(resp.serialize());
                    writer.newLine();
                    writer.flush();
                } else if ("notifications/initialized".equals(msg.method())) {
                    // Acknowledge silently
                }
            } catch (Exception e) {
                var errResp = JsonRpcMessage.error("unknown", -32603,
                    e.getMessage());
                writer.write(errResp.serialize());
                writer.newLine();
                writer.flush();
            }
        }
    }
}
