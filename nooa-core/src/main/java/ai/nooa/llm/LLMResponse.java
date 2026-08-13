package ai.nooa.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LLMResponse(
    String content,
    List<ToolCall> toolCalls,
    Usage usage,
    String model,
    String finishReason
) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolCall(
        String id,
        String name,
        Map<String, Object> arguments
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Usage(
        @JsonProperty("prompt_tokens") int promptTokens,
        @JsonProperty("completion_tokens") int completionTokens,
        @JsonProperty("total_tokens") int totalTokens
    ) {}
}
