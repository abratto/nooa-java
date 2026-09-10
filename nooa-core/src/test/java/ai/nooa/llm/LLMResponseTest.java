package ai.nooa.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class LLMResponseTest {

    @Test
    void parsesReasoningContentWithoutReplacingEmptyContent() throws Exception {
        String payload = """
            {
              "model": "reasoning-model",
              "choices": [{
                "message": {
                  "content": null,
                  "reasoning_content": "thinking trace"
                },
                "finish_reason": "stop"
              }],
              "usage": {"prompt_tokens": 1, "completion_tokens": 2, "total_tokens": 3}
            }
            """;

        Method parser = UnifiedLLM.class.getDeclaredMethod("parseResponse", JsonNode.class);
        parser.setAccessible(true);
        var response = (LLMResponse) parser.invoke(
            UnifiedLLM.create(UnifiedLLM.openAI("fake", "reasoning-model").build()),
            new ObjectMapper().readTree(payload));

        assertThat(response.content()).isNull();
        assertThat(response.reasoning()).isEqualTo("thinking trace");
        assertThat(response.finishReason()).isEqualTo("stop");
    }
}