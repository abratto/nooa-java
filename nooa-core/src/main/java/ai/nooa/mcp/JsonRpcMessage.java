package ai.nooa.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.UUID;

/**
 * JSON-RPC 2.0 message for MCP protocol communication.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcMessage(
    String jsonrpc,
    String id,
    String method,
    JsonNode params,
    JsonNode result,
    JsonNode error
) {
    static final ObjectMapper JSON = new ObjectMapper();

    static JsonRpcMessage request(String method, JsonNode params) {
        return new JsonRpcMessage("2.0", UUID.randomUUID().toString().substring(0, 8),
            method, params, null, null);
    }

    static JsonRpcMessage response(String id, JsonNode result) {
        return new JsonRpcMessage("2.0", id, null, null, result, null);
    }

    static JsonRpcMessage error(String id, int code, String message) {
        ObjectNode err = JSON.createObjectNode();
        err.put("code", code);
        err.put("message", message);
        return new JsonRpcMessage("2.0", id, null, null, null, err);
    }

    static JsonRpcMessage notification(String method, JsonNode params) {
        return new JsonRpcMessage("2.0", null, method, params, null, null);
    }

    boolean isResponse() { return id != null && method == null; }
    boolean isRequest() { return method != null; }
    boolean isNotification() { return method != null && id == null; }

    String serialize() {
        try {
            return JSON.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JSON-RPC message", e);
        }
    }

    static JsonRpcMessage deserialize(String raw) {
        try {
            return JSON.readValue(raw, JsonRpcMessage.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSON-RPC message: " + raw, e);
        }
    }

    static JsonRpcMessage initializeRequest() {
        ObjectNode caps = JSON.createObjectNode();
        ObjectNode clientInfo = JSON.createObjectNode();
        clientInfo.put("name", "nooa-java");
        clientInfo.put("version", "0.1.0");
        ObjectNode params = JSON.createObjectNode();
        params.put("protocolVersion", "2024-11-05");
        params.set("capabilities", caps);
        params.set("clientInfo", clientInfo);
        return request("initialize", params);
    }

    static JsonRpcMessage toolsListRequest() {
        return request("tools/list", JSON.createObjectNode());
    }

    static JsonRpcMessage toolsCallRequest(String toolName, Map<String, Object> arguments) {
        ObjectNode params = JSON.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", JSON.valueToTree(arguments));
        return request("tools/call", params);
    }

    static JsonRpcMessage initializedNotification() {
        return notification("notifications/initialized", JSON.createObjectNode());
    }
}
