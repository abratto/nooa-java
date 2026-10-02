package ai.nooa.strategy;

import ai.nooa.GenerationError;
import ai.nooa.config.PredictConfig;
import ai.nooa.llm.LLMResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Type;

/**
 * Single-shot structured output strategy.
 */
public final class PredictStrategy implements GenerationStrategy {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PredictConfig config;

    public PredictStrategy(PredictConfig config) { this.config = config; }
    public PredictStrategy() { this(PredictConfig.defaults()); }

    @Override
    public Object execute(RuntimeServices runtime, CurrentCall call) {
        int attempts = 0;
        Exception lastError = null;
        String retryFeedback = null;

        while (attempts < config.maxRetries()) {
            attempts++;
            try {
                LLMResponse response = runtime.generate(
                    List.of(), call.genericReturnType(), buildSamplingParams(runtime), retryFeedback);

                String content = response.content();
                if (content != null && !content.isBlank()) {
                    return parseResponse(content, call.genericReturnType());
                }
                throw new GenerationError("Empty response");
            } catch (Exception e) {
                lastError = e;
                retryFeedback = retryDiagnostic(e, call.genericReturnType());
                if (attempts < config.maxRetries()) {
                    runtime.eventManager().add(new ai.nooa.context.Event.Retry(
                        call.method().getName(), attempts, "structured-output",
                        e.getClass().getSimpleName()));
                }
            }
        }
        throw new GenerationError("PredictStrategy failed after " + config.maxRetries()
            + " attempts: " + describeError(lastError), lastError);
    }

    private static String describeError(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        return (message == null || message.isBlank())
            ? error.getClass().getSimpleName()
            : message;
    }

    private String retryDiagnostic(Exception error, Type returnType) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        int limit = 800;
        if (message.length() > limit) {
            message = message.substring(0, limit);
        }
        String target = returnType == null ? "the declared return type"
            : "`" + displayType(returnType) + "` (fields: " + describeFields(returnType) + ")";
        return "Previous attempt failed structured-output validation. Correct only this issue, keep the raw content otherwise unchanged, and return the same schema:\n"
            + "  Expected shape: " + target + "\n"
            + "  Failure: " + message;
    }

    private String describeFields(Type type) {
        if (!(type instanceof Class<?> clazz) || !clazz.isRecord()) {
            return "JSON array";
        }
        return String.join(", ", java.util.Arrays.stream(clazz.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .toList());
    }

    private String displayType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz.getSimpleName();
        }
        return type.getTypeName();
    }

    private Map<String, Object> buildSamplingParams(RuntimeServices runtime) {
        Map<String, Object> params = new java.util.HashMap<>();
        // Per-agent overrides (e.g. per-stage reasoning_effort) win over defaults.
        params.putAll(runtime.agent().samplingOverrides());

        if (config.temperature() != null) {
            params.putIfAbsent("temperature", config.temperature().doubleValue());
        }
        if (config.maxTokens() != null) {
            params.putIfAbsent("max_tokens", config.maxTokens());
        } else {
            String raw = System.getProperty("nooa.predict.maxTokens");
            if (raw != null) {
                try {
                    params.putIfAbsent("max_tokens", Integer.parseInt(raw.trim()));
                } catch (NumberFormatException _) {
                    // An invalid optional override falls back to the provider default.
                }
            }
        }
        if (config.reasoningEffort() != null && !config.reasoningEffort().isBlank()) {
            params.putIfAbsent("reasoning_effort", config.reasoningEffort());
        } else {
            String raw = System.getProperty("nooa.predict.reasoningEffort");
            if (raw != null && !raw.isBlank()) {
                params.putIfAbsent("reasoning_effort", raw.trim());
            }
        }
        return params;
    }

    @SuppressWarnings("java:S1166")
    private Object parseResponse(String content, Type returnType) throws JsonProcessingException {
        if (returnType == String.class || returnType == CharSequence.class) {
            return content.strip();
        }
        Object value = JSON.readValue(extractJson(content), JSON.constructType(returnType));
        validateRecordComponents(value, returnType);
        return value;
    }

    /**
     * Rejects parsed records with null components. A null field almost always
     * means the model omitted or misnamed a key; failing here triggers the
     * retry loop with a corrective diagnostic instead of handing the caller a
     * half-populated result.
     */
    private void validateRecordComponents(Object value, Type returnType) {
        if (!(value instanceof Record parsedRecord)) {
            return;
        }
        for (var component : parsedRecord.getClass().getRecordComponents()) {
            try {
                if (component.getAccessor().invoke(parsedRecord) == null) {
                    throw new GenerationError("Field '" + component.getName()
                        + "' is missing or null in the response for "
                        + displayType(returnType));
                }
            } catch (ReflectiveOperationException _) {
                // Cannot inspect; accept the value rather than fail closed.
            }
        }
    }

    @SuppressWarnings("java:S3776")
    private String extractJson(String content) {
        if (content == null) {
            return "";
        }
        String text = content.strip();

        if (text.startsWith("```")) {
            int nl = text.indexOf('\n');
            if (nl >= 0) {
                text = text.substring(nl + 1);
            }
            int fence = text.lastIndexOf("```");
            if (fence >= 0) {
                text = text.substring(0, fence);
            }
            text = text.strip();
        }

        if (text.startsWith("{") || text.startsWith("[")) {
            return text;
        }

        if (text.length() >= 2 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"') {
            String inner = text.substring(1, text.length() - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
            if (inner.startsWith("{") || inner.startsWith("[")) {
                return inner;
            }
        }

        int start = text.indexOf('{');
        if (start < 0) {
            return text;
        }
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return text.substring(start);
    }
}
