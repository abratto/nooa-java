package ai.nooa.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A small provider-agnostic helper for extracting typed JSON from a model response.
 * It keeps provider-specific logic outside this class and validates the result before
 * returning it, which makes it usable across OpenAI-compatible endpoints and local
 * Ollama deployments without hardcoding any one provider.
 */
public final class StructuredOutputHelper {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final int maxAttempts;

    public StructuredOutputHelper() {
        this(3);
    }

    public StructuredOutputHelper(int maxAttempts) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be > 0");
        }
        this.maxAttempts = maxAttempts;
    }

    public <T> T extract(List<Message> messages, Class<T> targetType, UnifiedLLM llm, Map<String, Object> samplingParams) {
        if (targetType == null) {
            throw new IllegalArgumentException("targetType is required");
        }

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            LLMResponse response = llm.chat(messages, List.of(), targetType, samplingParams);
            String content = response == null ? null : response.content();
            if (content == null || content.isBlank()) {
                continue;
            }

            try {
                T value = parseStructuredResponse(content, targetType);
                if (isValidStructuredValue(value)) {
                    return value;
                }
            } catch (Exception ignored) {
                // Retry with a stricter prompt on the next attempt.
            }
        }

        throw new IllegalArgumentException("Structured output could not be produced and validated for " + targetType.getSimpleName());
    }

    private <T> T parseStructuredResponse(String content, Class<T> targetType) throws JsonProcessingException {
        String json = stripMarkdownFence(content);
        ObjectReader reader = JSON.readerFor(targetType);
        return reader.readValue(json);
    }

    private String stripMarkdownFence(String content) {
        String value = content.strip();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return value.substring(firstNewline + 1, lastFence).strip();
            }
        }
        return value;
    }

    private boolean isValidStructuredValue(Object value) {
        if (value == null) {
            return false;
        }

        if (value.getClass().isRecord()) {
            for (RecordComponent component : value.getClass().getRecordComponents()) {
                Object fieldValue = readRecordComponent(value, component);
                if (fieldValue == null || (fieldValue instanceof String s && s.isBlank())) {
                    return false;
                }
            }
            return true;
        }

        for (var method : value.getClass().getMethods()) {
            if (method.getName().startsWith("get") && method.getParameterCount() == 0 && !method.getDeclaringClass().equals(Object.class)) {
                try {
                    Object fieldValue = method.invoke(value);
                    if (fieldValue == null || (fieldValue instanceof String s && s.isBlank())) {
                        return false;
                    }
                } catch (ReflectiveOperationException ignored) {
                    return false;
                }
            }
        }
        return true;
    }

    private Object readRecordComponent(Object recordInstance, RecordComponent component) {
        try {
            var accessor = component.getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(recordInstance);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
