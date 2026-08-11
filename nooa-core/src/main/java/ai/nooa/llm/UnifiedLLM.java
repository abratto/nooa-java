package ai.nooa.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified LLM client. Supports OpenAI-compatible APIs.
 * Uses {@code java.net.http.HttpClient} (JDK built-in, zero dependencies).
 *
 * <pre>{@code
 * var llm = UnifiedLLM.create(cfg -> cfg
 *     .provider("openai")
 *     .apiKey(System.getenv("OPENAI_API_KEY"))
 *     .model("gpt-4o"));
 *
 * var response = llm.chat(messages, tools, MyRecord.class, Map.of()).get();
 * }</pre>
 */
public class UnifiedLLM {

    private static final Logger log = LoggerFactory.getLogger(UnifiedLLM.class);
    private static final ObjectMapper JSON = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private static final Set<Integer> RETRIABLE_STATUSES = Set.of(
        429, 500, 502, 503, 504
    );

    private record RetryConfig(int maxRetries, long baseDelayMs, long maxDelayMs) {
        static RetryConfig defaults() {
            return new RetryConfig(3, 1000, 30000);
        }
    }

    public enum Provider { OPENAI, ANTHROPIC }

    private final RetryConfig retryConfig;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Provider provider;
    private final HttpClient http;

    protected UnifiedLLM(String apiKey, String baseUrl, String model) {
        this(apiKey, baseUrl, model, Provider.OPENAI);
    }

    private UnifiedLLM(String apiKey, String baseUrl, String model, Provider provider) {
        this(apiKey, baseUrl, model, provider, RetryConfig.defaults());
    }

    private UnifiedLLM(String apiKey, String baseUrl, String model, RetryConfig retryConfig) {
        this(apiKey, baseUrl, model, Provider.OPENAI, retryConfig);
    }

    private UnifiedLLM(String apiKey, String baseUrl, String model, Provider provider, RetryConfig retryConfig) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.provider = provider;
        this.retryConfig = retryConfig;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    public static UnifiedLLM create(ProviderConfig config) {
        return new UnifiedLLM(
            config.apiKey(), config.baseUrl(), config.model(),
            config.provider(), new RetryConfig(config.maxRetries(), 1000, 30000));
    }

    public static ProviderConfig.Builder openAI(String apiKey, String model) {
        return new ProviderConfig.Builder()
            .apiKey(apiKey)
            .baseUrl("https://api.openai.com/v1")
            .model(model)
            .provider(Provider.OPENAI);
    }

    public static ProviderConfig.Builder anthropic(String apiKey, String model) {
        return new ProviderConfig.Builder()
            .apiKey(apiKey)
            .baseUrl("https://api.anthropic.com")
            .model(model)
            .provider(Provider.ANTHROPIC);
    }

    /** OpenRouter — unified API for many models. */
    public static ProviderConfig.Builder openRouter(String apiKey, String model) {
        return new ProviderConfig.Builder()
            .apiKey(apiKey)
            .baseUrl("https://openrouter.ai/api/v1")
            .model(model)
            .provider(Provider.OPENAI);
    }

    /** DeepInfra — hosted open-source models. */
    public static ProviderConfig.Builder deepInfra(String apiKey, String model) {
        return new ProviderConfig.Builder()
            .apiKey(apiKey)
            .baseUrl("https://api.deepinfra.com/v1/openai")
            .model(model)
            .provider(Provider.OPENAI);
    }

    /** Groq — fast inference. */
    public static ProviderConfig.Builder groq(String apiKey, String model) {
        return new ProviderConfig.Builder()
            .apiKey(apiKey)
            .baseUrl("https://api.groq.com/openai/v1")
            .model(model)
            .provider(Provider.OPENAI);
    }

    /** Local Ollama instance. No API key needed — pass empty string. */
    public static ProviderConfig.Builder ollama(String model) {
        return new ProviderConfig.Builder()
            .apiKey("ollama")
            .baseUrl("http://localhost:11434/v1")
            .model(model)
            .provider(Provider.OPENAI);
    }

    /** Any OpenAI-compatible endpoint. */
    public static ProviderConfig.Builder custom(String baseUrl, String apiKey, String model) {
        return new ProviderConfig.Builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .model(model)
            .provider(Provider.OPENAI);
    }

    public String model() { return model; }

    public LLMResponse chat(
        List<Message> messages,
        List<Tool> tools,
        Class<?> outputModel,
        Map<String, Object> samplingParams
    ) {
        try {
            return doChat(messages, tools, outputModel, samplingParams);
        } catch (Exception e) {
            if (e instanceof LLMException le) throw le;
            throw new LLMException("LLM call failed: " + e.getMessage(), 0);
        }
    }

    private LLMResponse doChat(
        List<Message> messages,
        List<Tool> tools,
        Class<?> outputModel,
        Map<String, Object> samplingParams
    ) throws Exception {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);

        if (provider == Provider.ANTHROPIC) {
            body.put("max_tokens", samplingParams != null && samplingParams.containsKey("max_tokens")
                ? ((Number) samplingParams.get("max_tokens")).intValue() : 4096);
            // Anthropic: system is a top-level field, not a message
            var systemMsg = messages.stream().filter(m -> "system".equals(m.role())).findFirst();
            systemMsg.ifPresent(m -> body.put("system", m.content()));
            body.set("messages", messagesToJsonAnthropic(messages));
            if (tools != null && !tools.isEmpty()) {
                body.set("tools", JSON.valueToTree(tools));
            }
        } else {
            body.set("messages", messagesToJson(messages));
            if (tools != null && !tools.isEmpty()) {
                body.set("tools", JSON.valueToTree(tools));
                body.put("tool_choice", "auto");
            }
            if (outputModel != null) {
                body.set("response_format", buildStructuredOutputSchema(outputModel));
            }
            applySamplingParams(body, samplingParams);
        }

        String endpoint = provider == Provider.ANTHROPIC ? "/messages" : "/chat/completions";
        String authHeader = provider == Provider.ANTHROPIC ? "x-api-key" : "Authorization";
        String authValue = provider == Provider.ANTHROPIC ? apiKey : "Bearer " + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + endpoint))
            .header(authHeader, authValue)
            .header("Content-Type", "application/json")
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
            .timeout(Duration.ofMinutes(5))
            .build();

        return executeWithRetry(request);
    }

    private LLMResponse executeWithRetry(HttpRequest request) throws Exception {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < retryConfig.maxRetries()) {
            attempt++;
            try {
                HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return parseResponse(JSON.readTree(response.body()));
                }

                if (RETRIABLE_STATUSES.contains(response.statusCode())) {
                    JsonNode root = JSON.readTree(response.body());
                    String error = root.path("error").path("message").asText(
                        "HTTP " + response.statusCode());
                    lastException = new LLMException(error, response.statusCode());

                    if (attempt < retryConfig.maxRetries()) {
                        long delay = Math.min(
                            retryConfig.baseDelayMs() * (1L << (attempt - 1)),
                            retryConfig.maxDelayMs());
                        log.debug("Retry {}/{} after {}ms: {}",
                            attempt, retryConfig.maxRetries(), delay, error);
                        Thread.sleep(delay);
                        continue;
                    }
                    throw lastException;
                }

                JsonNode root = JSON.readTree(response.body());
                String error = root.path("error").path("message").asText(
                    "HTTP " + response.statusCode());
                throw new LLMException(error, response.statusCode());
            } catch (LLMException e) {
                if (!RETRIABLE_STATUSES.contains(e.statusCode())) {
                    throw e;
                }
                lastException = e;
                long delay = Math.min(
                    retryConfig.baseDelayMs() * (1L << (attempt - 1)),
                    retryConfig.maxDelayMs());
                log.debug("Retry {}/{} after {}ms", attempt,
                    retryConfig.maxRetries(), delay);
                Thread.sleep(delay);
            }
        }

        throw new LLMException("Max retries exceeded",
            lastException instanceof LLMException le ? le.statusCode() : 0);
    }

    private LLMResponse parseResponse(JsonNode root) {
        if (provider == Provider.ANTHROPIC) {
            return parseAnthropicResponse(root);
        }
        JsonNode choice = root.path("choices").get(0);
        JsonNode msg = choice.path("message");

        String content = msg.path("content").asText(null);
        List<LLMResponse.ToolCall> toolCalls = parseToolCalls(msg.path("tool_calls"));
        LLMResponse.Usage usage = new LLMResponse.Usage(
            root.path("usage").path("prompt_tokens").asInt(),
            root.path("usage").path("completion_tokens").asInt(),
            root.path("usage").path("total_tokens").asInt()
        );

        return new LLMResponse(content, toolCalls, usage,
            root.path("model").asText(), choice.path("finish_reason").asText());
    }

    private LLMResponse parseAnthropicResponse(JsonNode root) {
        StringBuilder textContent = new StringBuilder();
        List<LLMResponse.ToolCall> toolCalls = new ArrayList<>();

        JsonNode content = root.path("content");
        if (content.isArray()) {
            for (JsonNode block : content) {
                String type = block.path("type").asText();
                if ("text".equals(type)) {
                    if (!textContent.isEmpty()) textContent.append("\n");
                    textContent.append(block.path("text").asText());
                } else if ("tool_use".equals(type)) {
                    JsonNode input = block.path("input");
                    Map<String, Object> args = jsonToMap(input.toString());
                    toolCalls.add(new LLMResponse.ToolCall(
                        block.path("id").asText(), block.path("name").asText(), args));
                }
            }
        }

        JsonNode usage = root.path("usage");
        return new LLMResponse(
            !textContent.isEmpty() ? textContent.toString() : null,
            toolCalls,
            new LLMResponse.Usage(
                usage.path("input_tokens").asInt(),
                usage.path("output_tokens").asInt(),
                usage.path("input_tokens").asInt() + usage.path("output_tokens").asInt()),
            root.path("model").asText(),
            root.path("stop_reason").asText()
        );
    }

    private List<LLMResponse.ToolCall> parseToolCalls(JsonNode toolCallsNode) {
        if (toolCallsNode == null || toolCallsNode.isNull() || !toolCallsNode.isArray()) {
            return List.of();
        }
        List<LLMResponse.ToolCall> result = new ArrayList<>();
        for (JsonNode tc : toolCallsNode) {
            String fnName = tc.path("function").path("name").asText();
            Map<String, Object> fnArgs = jsonToMap(tc.path("function").path("arguments").asText());
            result.add(new LLMResponse.ToolCall(
                tc.path("id").asText(),
                fnName,
                fnArgs
            ));
        }
        return result;
    }

    private ArrayNode messagesToJson(List<Message> messages) {
        ArrayNode arr = JSON.createArrayNode();
        for (Message m : messages) {
            ObjectNode node = JSON.createObjectNode();
            node.put("role", m.role());
            if (m.content() != null) {
                node.put("content", m.content());
            }
            if (m.toolCallId() != null) {
                node.put("tool_call_id", m.toolCallId());
            }
            if (m.name() != null) {
                node.put("name", m.name());
            }
            if (m.toolCalls() != null) {
                node.set("tool_calls", JSON.valueToTree(m.toolCalls()));
            }
            arr.add(node);
        }
        return arr;
    }

    @SuppressWarnings("deprecation")
    private ObjectNode buildStructuredOutputSchema(Class<?> outputModel) {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "json_schema");
        ObjectNode jsonSchema = schema.putObject("json_schema");
        jsonSchema.put("name", outputModel.getSimpleName());
        jsonSchema.put("strict", true);

        try {
            var schemaGen = JSON.generateJsonSchema(outputModel);
            jsonSchema.set("schema", JSON.valueToTree(schemaGen));
        } catch (JsonProcessingException e) {
            log.warn("Could not generate JSON schema for {}", outputModel.getName(), e);
            jsonSchema.put("schema", "{}");
        }
        return schema;
    }

    private ArrayNode messagesToJsonAnthropic(List<Message> messages) {
        ArrayNode arr = JSON.createArrayNode();
        for (Message m : messages) {
            if ("system".equals(m.role())) continue; // handled separately
            ObjectNode node = JSON.createObjectNode();
            node.put("role", m.role());
            if (m.content() != null) {
                node.put("content", m.content());
            }
            arr.add(node);
        }
        return arr;
    }

    private void applySamplingParams(ObjectNode body, Map<String, Object> params) {
        if (params == null) { return; }
        if (params.containsKey("temperature")) {
            body.put("temperature", ((Number) params.get("temperature")).doubleValue());
        }
        if (params.containsKey("max_tokens")) {
            body.put("max_tokens", ((Number) params.get("max_tokens")).intValue());
        }
        if (params.containsKey("top_p")) {
            body.put("top_p", ((Number) params.get("top_p")).doubleValue());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonToMap(String json) {
        if (json == null || json.isBlank()) { return Map.of(); }
        try {
            return JSON.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse tool call arguments: {}", json, e);
            return Map.of();
        }
    }

    /**
     * Provider configuration record.
     */
    public record ProviderConfig(String apiKey, String baseUrl, String model,
                                  int maxRetries, Provider provider) {
        public static final class Builder {
            private String apiKey;
            private String baseUrl;
            private String model;
            private int maxRetries = 3;
            private Provider provider = Provider.OPENAI;

            public Builder apiKey(String v) { this.apiKey = v; return this; }
            public Builder baseUrl(String v) { this.baseUrl = v; return this; }
            public Builder model(String v) { this.model = v; return this; }
            public Builder maxRetries(int v) { this.maxRetries = v; return this; }
            public Builder provider(Provider v) { this.provider = v; return this; }

            public ProviderConfig build() {
                if (apiKey == null) throw new IllegalArgumentException("apiKey required");
                if (baseUrl == null) throw new IllegalArgumentException("baseUrl required");
                if (model == null) throw new IllegalArgumentException("model required");
                return new ProviderConfig(apiKey, baseUrl, model, maxRetries, provider);
            }
        }
    }

    public static final class LLMException extends RuntimeException {
        private final int statusCode;

        public LLMException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int statusCode() { return statusCode; }
    }
}
