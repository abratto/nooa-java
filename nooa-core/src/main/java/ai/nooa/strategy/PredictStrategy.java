package ai.nooa.strategy;

import ai.nooa.GenerationError;
import ai.nooa.config.PredictConfig;
import ai.nooa.llm.LLMResponse;
import ai.nooa.llm.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Single-shot structured output strategy.
 */
public final class PredictStrategy implements GenerationStrategy {

    private final PredictConfig config;

    public PredictStrategy(PredictConfig config) { this.config = config; }
    public PredictStrategy() { this(PredictConfig.defaults()); }

    @Override
    public Object execute(RuntimeServices runtime, CurrentCall call) {
        int attempts = 0;
        Exception lastError = null;

        while (attempts < config.maxRetries()) {
            attempts++;
            try {
                List<Message> messages = new ArrayList<>();
                messages.add(Message.system(buildSystemPrompt(runtime)));
                messages.add(Message.user(call.docstring()));

                LLMResponse response = runtime.generate(
                    List.of(), call.returnType(), buildSamplingParams(runtime));

                String content = response.content();
                if (content != null && !content.isBlank()) {
                    return parseResponse(content, call.returnType());
                }
                throw new GenerationError("Empty response");
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw new GenerationError("PredictStrategy failed after " + config.maxRetries() + " attempts", lastError);
    }

    private String buildSystemPrompt(RuntimeServices runtime) {
        return "You are a structured output generator.\n\n"
            + runtime.agent().contextManager().render(runtime.agent());
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
                } catch (NumberFormatException ignored) {
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

    private Object parseResponse(String content, Class<?> returnType) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return mapper.readValue(extractJson(content), returnType);
    }

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
