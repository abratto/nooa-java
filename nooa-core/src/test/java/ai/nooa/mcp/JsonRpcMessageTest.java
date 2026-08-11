package ai.nooa.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JSON-RPC Messages")
class JsonRpcMessageTest {

    @Test
    @DisplayName("serialize and deserialize round-trip")
    void roundTrip() {
        var req = JsonRpcMessage.toolsListRequest();
        String raw = req.serialize();
        var parsed = JsonRpcMessage.deserialize(raw);

        assertThat(parsed.method()).isEqualTo("tools/list");
        assertThat(parsed.isRequest()).isTrue();
    }

    @Test
    @DisplayName("initialize request has protocol version")
    void initializeRequest() {
        var req = JsonRpcMessage.initializeRequest();
        String raw = req.serialize();
        assertThat(raw).contains("initialize").contains("2024-11-05");
    }

    @Test
    @DisplayName("response round-trip")
    void responseRoundTrip() {
        var req = JsonRpcMessage.toolsListRequest();
        var resp = JsonRpcMessage.response(req.id(),
            JsonRpcMessage.JSON.createObjectNode().put("status", "ok"));
        String raw = resp.serialize();
        var parsed = JsonRpcMessage.deserialize(raw);
        assertThat(parsed.isResponse()).isTrue();
        assertThat(parsed.result().path("status").asText()).isEqualTo("ok");
    }
}
