package ai.nooa.strategy;

import ai.nooa.Agent;
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
                    List.of(), call.returnType(), buildSamplingParams());

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

    private Map<String, Object> buildSamplingParams() {
        Map<String, Object> params = new java.util.HashMap<>();
        if (config.temperature() != null) params.put("temperature", config.temperature().doubleValue());
        if (config.maxTokens() != null) params.put("max_tokens", config.maxTokens());
        return params;
    }

    private Object parseResponse(String content, Class<?> returnType) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = content.strip();
        if (json.startsWith("```")) {
            int start = json.indexOf('\n') + 1;
            int end = json.lastIndexOf("```");
            if (end > start) json = json.substring(start, end).strip();
        }
        return mapper.readValue(json, returnType);
    }
}
