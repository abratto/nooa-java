package ai.nooa.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ToolTest {

    @Test
    @DisplayName("Tool builder creates valid tool schema")
    void builderCreatesSchema() {
        var tool = Tool.builder()
            .name("executeJava")
            .description("Execute Java code")
            .parameter("code", "string", "Code to execute")
            .parameter("timeout", "integer", "Timeout in ms", false)
            .build();

        assertThat(tool.name()).isEqualTo("executeJava");
        assertThat(tool.description()).isEqualTo("Execute Java code");
        assertThat(tool.inputSchema()).containsKey("properties");
        assertThat(tool.inputSchema()).containsKey("required");

        @SuppressWarnings("unchecked")
        var required = (java.util.List<String>) tool.inputSchema().get("required");
        assertThat(required).contains("code");
        assertThat(required).doesNotContain("timeout");
    }
}
