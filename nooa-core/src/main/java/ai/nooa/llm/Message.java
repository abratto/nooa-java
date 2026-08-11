package ai.nooa.llm;

import java.util.Map;

/**
 * A message in the LLM conversation. Used internally to build the prompt.
 */
public record Message(
    String role,
    String content,
    Map<String, Object> toolCalls,
    String toolCallId,
    String name
) {
    public static Message system(String content) {
        return new Message("system", content, null, null, null);
    }

    public static Message user(String content) {
        return new Message("user", content, null, null, null);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content, null, null, null);
    }

    public static Message toolResult(String toolCallId, String name, String content) {
        return new Message("tool", content, null, toolCallId, name);
    }
}
