package ai.nooa.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
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

    private static final String MODEL_FIELD = "model";
    private static final String MAX_TOKENS_FIELD = "max_tokens";
    private static final String SYSTEM_ROLE = "system";
    private static final String CONTENT_FIELD = "content";
    private static final String REASONING_FIELD = "reasoning";
    private static final String REASONING_CONTENT_FIELD = "reasoning_content";
    private static final String USAGE_FIELD = "usage";
    private static final String MESSAGE_FIELD = "message";
    private static final String MESSAGES_FIELD = "messages";
    private static final String ERROR_FIELD = "error";
    private static final String TEMPERATURE_FIELD = "temperature";
    private static final String TOP_P_FIELD = "top_p";
    private static final String REASONING_EFFORT_FIELD = "reasoning_effort";
    private static final String THINK_FIELD = "think";
    private static final String NUM_PREDICT_FIELD = "num_predict";
    private static final String JSON_SCHEMA_FORMAT = "json_schema";
    private static final String STRUCTURED_FORMAT_PROPERTY = "nooa.structured.format";
    private static final String TOOL_CALLS_FIELD = "tool_calls";
    private static final String ROLE_FIELD = "role";
    private static final String TOOL_CALL_ID_FIELD = "tool_call_id";
    private static final String NAME_FIELD = "name";
    private static final String TYPE_FIELD = "type";
    private static final Logger log = LoggerFactory.getLogger(UnifiedLLM.class);
    private static final ObjectMapper JSON = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private static final Set<Integer> RETRIABLE_STATUSES = Set.of(
        429, 500, 502, 503, 504
    );

    /** Default output budget for Ollama when no explicit max_tokens is set. */
    private static final int DEFAULT_OLLAMA_NUM_PREDICT = 8192;

    private record RetryConfig(int maxRetries, long baseDelayMs, long maxDelayMs) {
        static RetryConfig defaults() {
            return new RetryConfig(3, 1000, 30000);
        }
    }

    public enum Provider { OPENAI, ANTHROPIC, OLLAMA }

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

    /** Create a client from provider settings using the configured retry count. */
    public static UnifiedLLM create(ProviderConfig config) {
        return new UnifiedLLM(
            config.apiKey(), config.baseUrl(), config.model(),
            config.provider(), new RetryConfig(config.maxRetries(), 1000, 30000));
    }

    /** Build configuration for the OpenAI API using its default v1 endpoint. */
    public static ProviderConfig.Builder openAI(String apiKey, String model) {
        return providerConfig(apiKey, "https://api.openai.com/v1", model, Provider.OPENAI);
    }

    /** Build configuration for Anthropic's API; the API key is sent as provider authentication. */
    public static ProviderConfig.Builder anthropic(String apiKey, String model) {
        return providerConfig(apiKey, "https://api.anthropic.com", model, Provider.ANTHROPIC);
    }

    /** OpenRouter — unified API for many models. */
    public static ProviderConfig.Builder openRouter(String apiKey, String model) {
        return providerConfig(apiKey, "https://openrouter.ai/api/v1", model, Provider.OPENAI);
    }

    /** DeepInfra — hosted open-source models. */
    public static ProviderConfig.Builder deepInfra(String apiKey, String model) {
        return providerConfig(apiKey, "https://api.deepinfra.com/v1/openai", model, Provider.OPENAI);
    }

    /** Groq — fast inference. */
    public static ProviderConfig.Builder groq(String apiKey, String model) {
        return providerConfig(apiKey, "https://api.groq.com/openai/v1", model, Provider.OPENAI);
    }

    /** Local Ollama instance. No API key needed — pass empty string. */
    public static ProviderConfig.Builder ollama(String model) {
        return providerConfig("ollama", "http://localhost:11434", model, Provider.OLLAMA);
    }

    /** Build configuration for any OpenAI-compatible endpoint. */
    public static ProviderConfig.Builder custom(String baseUrl, String apiKey, String model) {
        return providerConfig(apiKey, baseUrl, model, Provider.OPENAI);
    }

    private static ProviderConfig.Builder providerConfig(
        String apiKey, String baseUrl, String model, Provider provider) {
        return new ProviderConfig.Builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .model(model)
            .provider(provider);
    }

    public String model() { return model; }

    /**
     * Send a chat request, deserialize structured output when requested, and
     * retry transient provider responses according to this client's policy.
     */
    public LLMResponse chat(
        List<Message> messages,
        List<Tool> tools,
        Class<?> outputModel,
        Map<String, Object> samplingParams
    ) {
        return chat(messages, tools, (Type) outputModel, samplingParams);
    }

    /** Send a chat request using a reflective output type, including parameterized types when supported. */
    public LLMResponse chat(
        List<Message> messages,
        List<Tool> tools,
        Type outputModel,
        Map<String, Object> samplingParams
    ) {
        try {
            return doChat(messages, tools, outputModel, samplingParams);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new LLMException("LLM call interrupted", 0);
        } catch (Exception e) {
            if (e instanceof LLMException le) throw le;
            throw new LLMException("LLM call failed: " + e.getMessage(), 0);
        }
    }

    private LLMResponse doChat(
        List<Message> messages,
        List<Tool> tools,
        Type outputModel,
        Map<String, Object> samplingParams
    ) throws IOException, InterruptedException {
        ObjectNode body = JSON.createObjectNode();
        body.put(MODEL_FIELD, model);
        configureRequestBody(body, messages, tools, outputModel, samplingParams);

        HttpRequest request = buildRequest(body);
        return executeWithRetry(request);
    }

    private void configureRequestBody(ObjectNode body,
        List<Message> messages,
        List<Tool> tools,
        Type outputModel,
        Map<String, Object> samplingParams) {
        if (provider == Provider.ANTHROPIC) {
            body.put(MAX_TOKENS_FIELD, samplingParams != null && samplingParams.containsKey(MAX_TOKENS_FIELD)
                ? ((Number) samplingParams.get(MAX_TOKENS_FIELD)).intValue() : 4096);
            var systemMsg = messages.stream().filter(m -> SYSTEM_ROLE.equals(m.role())).findFirst();
            systemMsg.ifPresent(m -> body.put(SYSTEM_ROLE, m.content()));
            body.set(MESSAGES_FIELD, messagesToJsonAnthropic(messages));
            if (tools != null && !tools.isEmpty()) {
                body.set("tools", JSON.valueToTree(tools));
            }
            return;
        }
        if (provider == Provider.OLLAMA) {
            configureOllamaBody(body, messages, outputModel, samplingParams);
            return;
        }

        body.set(MESSAGES_FIELD, messagesToJson(messages));
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", JSON.valueToTree(tools));
            body.put("tool_choice", "auto");
        }
        if (wantsStructuredOutput(outputModel, tools)) {
            body.set("response_format", buildStructuredOutputSchema(outputModel));
        }
        applySamplingParams(body, samplingParams);
    }

    /**
     * Whether the request should constrain output to JSON.
     *
     * <p>Structured output only makes sense when the caller wants JSON-shaped
     * content: a record or POJO target. It must not be forced when the target
     * is a plain {@code String} or primitive (the model would wrap ordinary
     * prose in JSON), nor when tools are offered — tool-calling strategies
     * such as CodeAct communicate their result through the tool protocol, and
     * a JSON content constraint can suppress or corrupt tool calls.</p>
     */
    private static boolean wantsStructuredOutput(Type outputModel, List<Tool> tools) {
        if (outputModel == null || (tools != null && !tools.isEmpty())) {
            return false;
        }
        Class<?> raw = outputModel instanceof Class<?> clazz ? clazz : Object.class;
        return !(raw == String.class || raw == CharSequence.class || raw == Character.class
            || raw == char.class || raw == boolean.class || raw == Boolean.class
            || Number.class.isAssignableFrom(raw) || raw.isPrimitive());
    }

    /**
     * Builds Ollama's native {@code /api/chat} body. Ollama ignores the
     * OpenAI-compatible {@code response_format} field on its {@code /v1/...}
     * endpoint, so structured output must use the native {@code format:"json"}
     * flag instead; thinking level is conveyed through {@code options}
     * ({@code reasoning_effort} / {@code num_predict}) and {@code think} at the
     * root, per the Ollama API.
     */
    private void configureOllamaBody(ObjectNode body, List<Message> messages,
                                     Type outputModel, Map<String, Object> samplingParams) {
        body.set(MESSAGES_FIELD, messagesToJson(messages));
        body.put("stream", false);
        if (wantsStructuredOutput(outputModel, null)) {
            body.put("format", "json");
        }
        ObjectNode options = body.putObject("options");
        if (samplingParams != null) {
            copySampling(samplingParams, TEMPERATURE_FIELD, options, TEMPERATURE_FIELD);
            copySampling(samplingParams, MAX_TOKENS_FIELD, options, NUM_PREDICT_FIELD);
            copySampling(samplingParams, TOP_P_FIELD, options, TOP_P_FIELD);
            if (samplingParams.containsKey(REASONING_EFFORT_FIELD)
                && samplingParams.get(REASONING_EFFORT_FIELD) != null) {
                options.put(REASONING_EFFORT_FIELD,
                    String.valueOf(samplingParams.get(REASONING_EFFORT_FIELD)));
            }
        }
        // Ollama's default num_predict is tiny (128) — far too small for CLAD
        // artefact generation. Default to a generous budget unless the caller
        // supplied an explicit max_tokens/num_predict.
        if (!options.has(NUM_PREDICT_FIELD)) {
            options.put(NUM_PREDICT_FIELD, DEFAULT_OLLAMA_NUM_PREDICT);
        }
        if (samplingParams != null && samplingParams.containsKey(THINK_FIELD)
            && samplingParams.get(THINK_FIELD) != null) {
            Object think = samplingParams.get(THINK_FIELD);
            body.set(THINK_FIELD, JSON.valueToTree(think));
        }
        if (options.isEmpty()) {
            body.remove("options");
        }
    }

    private static void copySampling(Map<String, Object> params, String key,
                                     ObjectNode target, String targetKey) {
        if (!params.containsKey(key) || params.get(key) == null) {
            return;
        }
        Object v = params.get(key);
        if (v instanceof Number n) {
            target.put(targetKey, key.equals(MAX_TOKENS_FIELD) ? n.intValue() : n.doubleValue());
        } else {
            target.put(targetKey, String.valueOf(v));
        }
    }

    private HttpRequest buildRequest(ObjectNode body) throws JsonProcessingException {
        String endpoint = switch (provider) {
            case ANTHROPIC -> "/messages";
            case OLLAMA -> "/api/chat";
            default -> "/chat/completions";
        };
        String authHeader = provider == Provider.ANTHROPIC ? "x-api-key" : "Authorization";
        String authValue = provider == Provider.ANTHROPIC ? apiKey : "Bearer " + apiKey;

        return HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + endpoint))
            .header(authHeader, authValue)
            .header("Content-Type", "application/json")
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
            .timeout(Duration.ofMinutes(5))
            .build();
    }

    private LLMResponse executeWithRetry(HttpRequest request) throws IOException, InterruptedException {
        int attempt = 0;
        LLMException lastException = null;

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
                    String error = root.path(ERROR_FIELD).path(MESSAGE_FIELD).asText(
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
                String error = root.path(ERROR_FIELD).path(MESSAGE_FIELD).asText(
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
            lastException != null ? lastException.statusCode() : 0);
    }

    private LLMResponse parseResponse(JsonNode root) {
        if (provider == Provider.ANTHROPIC) {
            return parseAnthropicResponse(root);
        }
        if (provider == Provider.OLLAMA) {
            return parseOllamaResponse(root);
        }
        JsonNode choice = root.path("choices").get(0);
        JsonNode msg = choice.path(MESSAGE_FIELD);

        String content = msg.path(CONTENT_FIELD).asText(null);
        String reasoning = reasoningFrom(msg);
        List<LLMResponse.ToolCall> toolCalls = parseToolCalls(msg.path(TOOL_CALLS_FIELD));
        LLMResponse.Usage usage = new LLMResponse.Usage(
            root.path(USAGE_FIELD).path("prompt_tokens").asInt(),
            root.path(USAGE_FIELD).path("completion_tokens").asInt(),
            root.path(USAGE_FIELD).path("total_tokens").asInt()
        );

        return new LLMResponse(content, reasoning, toolCalls, usage,
            root.path(MODEL_FIELD).asText(), choice.path("finish_reason").asText());
    }

    private LLMResponse parseOllamaResponse(JsonNode root) {
        JsonNode msg = root.path(MESSAGE_FIELD);
        String content = msg.path(CONTENT_FIELD).asText(null);
        String reasoning = reasoningFrom(msg);
        int promptTokens = root.path("prompt_eval_count").asInt();
        int completionTokens = root.path("eval_count").asInt();
        LLMResponse.Usage usage = new LLMResponse.Usage(promptTokens, completionTokens,
            promptTokens + completionTokens);
        return new LLMResponse(content, reasoning, List.of(), usage,
            root.path(MODEL_FIELD).asText(), root.path("done_reason").asText());
    }

    private LLMResponse parseAnthropicResponse(JsonNode root) {
        StringBuilder textContent = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<LLMResponse.ToolCall> toolCalls = new ArrayList<>();

        JsonNode content = root.path(CONTENT_FIELD);
        if (content.isArray()) {
            for (JsonNode block : content) {
                appendAnthropicBlock(block, textContent, reasoning, toolCalls);
            }
        }

        JsonNode usage = root.path(USAGE_FIELD);
        return new LLMResponse(
            !textContent.isEmpty() ? textContent.toString() : null,
            !reasoning.isEmpty() ? reasoning.toString() : null,
            toolCalls,
            new LLMResponse.Usage(
                usage.path("input_tokens").asInt(),
                usage.path("output_tokens").asInt(),
                usage.path("input_tokens").asInt() + usage.path("output_tokens").asInt()),
            root.path(MODEL_FIELD).asText(),
            root.path("stop_reason").asText()
        );
    }

    private void appendAnthropicBlock(JsonNode block, StringBuilder textContent,
                                      StringBuilder reasoning,
                                      List<LLMResponse.ToolCall> toolCalls) {
        switch (block.path(TYPE_FIELD).asText()) {
            case "text" -> appendBlock(textContent, block.path(CONTENT_FIELD).asText());
            case "thinking" -> appendBlock(reasoning, block.path("thinking").asText());
            case "tool_use" -> toolCalls.add(new LLMResponse.ToolCall(
                block.path("id").asText(), block.path(NAME_FIELD).asText(),
                jsonToMap(block.path("input").toString())));
            default -> {
                // Ignore provider-specific block types this client does not expose.
            }
        }
    }

    private static void appendBlock(StringBuilder target, String value) {
        if (!target.isEmpty()) target.append("\n");
        target.append(value);
    }

    private static String reasoningFrom(JsonNode message) {
        String reasoning = message.path(REASONING_CONTENT_FIELD).asText(null);
        if (reasoning == null || reasoning.isBlank()) {
            reasoning = message.path(REASONING_FIELD).asText(null);
        }
        return reasoning;
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
            node.put(ROLE_FIELD, m.role());
            if (m.content() != null) {
                node.put(CONTENT_FIELD, m.content());
            }
            if (m.toolCallId() != null) {
                node.put(TOOL_CALL_ID_FIELD, m.toolCallId());
            }
            if (m.name() != null) {
                node.put(NAME_FIELD, m.name());
            }
            if (m.toolCalls() != null) {
                node.set(TOOL_CALLS_FIELD, JSON.valueToTree(m.toolCalls()));
            }
            arr.add(node);
        }
        return arr;
    }

    private ObjectNode buildStructuredOutputSchema(Type outputModel) {
        ObjectNode schema = JSON.createObjectNode();
        if (JSON_SCHEMA_FORMAT.equals(System.getProperty(STRUCTURED_FORMAT_PROPERTY))) {
            schema.put("type", JSON_SCHEMA_FORMAT);
            ObjectNode jsonSchema = schema.putObject(JSON_SCHEMA_FORMAT);
            jsonSchema.put("name", outputModel.getTypeName().replace('.', '_'));
            jsonSchema.put("strict", true);
            try {
                jsonSchema.set("schema", schemaFor(outputModel));
            } catch (JsonProcessingException e) {
                log.warn("Could not generate JSON schema for {}", outputModel.getTypeName(), e);
                jsonSchema.put("schema", "{}");
            }
        } else {
            schema.put("type", "json_object");
        }
        return schema;
    }

    @SuppressWarnings({"deprecation", "java:S1874"})
    private JsonNode schemaFor(Type type) throws JsonProcessingException {
        if (type instanceof Class<?> clazz) {
            return JSON.valueToTree(JSON.generateJsonSchema(clazz));
        }
        if (type instanceof ParameterizedType parameterized
            && parameterized.getRawType() instanceof Class<?> raw
            && java.util.Collection.class.isAssignableFrom(raw)
            && parameterized.getActualTypeArguments().length == 1) {
            ObjectNode array = JSON.createObjectNode().put("type", "array");
            array.set("items", schemaFor(parameterized.getActualTypeArguments()[0]));
            return array;
        }
        return JSON.createObjectNode().put("type", "object");
    }

    private ArrayNode messagesToJsonAnthropic(List<Message> messages) {
        ArrayNode arr = JSON.createArrayNode();
        for (Message m : messages) {
            if (SYSTEM_ROLE.equals(m.role())) continue; // handled separately
            ObjectNode node = JSON.createObjectNode();
            node.put(ROLE_FIELD, m.role());
            if (m.content() != null) {
                node.put(CONTENT_FIELD, m.content());
            }
            arr.add(node);
        }
        return arr;
    }

    private void applySamplingParams(ObjectNode body, Map<String, Object> params) {
        if (params == null) { return; }
        if (params.containsKey(TEMPERATURE_FIELD)) {
            body.put(TEMPERATURE_FIELD, ((Number) params.get(TEMPERATURE_FIELD)).doubleValue());
        }
        if (params.containsKey(MAX_TOKENS_FIELD)) {
            body.put(MAX_TOKENS_FIELD, ((Number) params.get(MAX_TOKENS_FIELD)).intValue());
        }
        if (params.containsKey(TOP_P_FIELD)) {
            body.put(TOP_P_FIELD, ((Number) params.get(TOP_P_FIELD)).doubleValue());
        }
        if (params.containsKey(REASONING_EFFORT_FIELD) && params.get(REASONING_EFFORT_FIELD) != null) {
            body.put(REASONING_EFFORT_FIELD, String.valueOf(params.get(REASONING_EFFORT_FIELD)));
        }
        if (params.containsKey(THINK_FIELD) && params.get(THINK_FIELD) != null) {
            Object think = params.get(THINK_FIELD);
            if (think instanceof Boolean b) {
                body.put(THINK_FIELD, b);
            } else {
                body.put(THINK_FIELD, String.valueOf(think));
            }
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
