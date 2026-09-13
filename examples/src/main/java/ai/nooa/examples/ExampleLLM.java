package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.llm.UnifiedLLM;

import java.util.Map;

/**
 * Shared provider selection and per-example model tuning for the examples.
 *
 * <p>The examples intentionally do not hard-code a provider. {@link #create()}
 * picks one in this order:</p>
 * <ol>
 *   <li>{@code NOOA_BASE_URL} + {@code NOOA_API_KEY} + {@code NOOA_MODEL} (any
 *       OpenAI-compatible endpoint)</li>
 *   <li>{@code OPENAI_BASE_URL} + {@code OPENAI_API_KEY} + {@code OPENAI_MODEL}</li>
 *   <li>OpenAI using {@code OPENAI_API_KEY}</li>
 *   <li>Local Ollama using {@code NOOA_MODEL} (default {@value #DEFAULT_MODEL})</li>
 * </ol>
 *
 * <p><b>Why Ollama uses the OpenAI-compatible endpoint.</b> Local Ollama is
 * reached through {@code http://localhost:11434/v1}, not the native
 * {@code /api/chat} API. The native path in {@code UnifiedLLM} does not forward
 * tool definitions, which would silently disable the default
 * {@code CodeActStrategy} (its {@code executeJava}/{@code returnResult} tools
 * would never be offered to the model). The OpenAI-compatible endpoint speaks
 * the same tools + {@code response_format} protocol as the hosted providers, so
 * examples behave the same locally and remotely. Override the host with
 * {@code NOOA_OLLAMA_BASE_URL}.</p>
 *
 * <p><b>Per-example tuning.</b> {@link #tune(Agent, String, Integer)} maps each
 * example's needs onto sampling overrides that every strategy now honours
 * (the runtime merges {@link Agent#samplingOverride(String, Object)} into each
 * LLM request):</p>
 * <ul>
 *   <li>{@code reasoning_effort} — {@code "low"} for deterministic
 *       classification/extraction and short answers, {@code "medium"} for
 *       moderate judgement, {@code "high"} for multi-step reasoning or
 *       self-critique loops.</li>
 *   <li>{@code max_tokens} — a per-turn output bound so CodeAct turns cannot
 *       run away on a local model.</li>
 * </ul>
 * Set {@code NOOA_REASONING_EFFORT} (or {@code -Dnooa.reasoningEffort=...}) to
 * override every example's effort for a tuning run.</p>
 */
public final class ExampleLLM {

    /** Default local model used when no provider environment is configured. */
    public static final String DEFAULT_MODEL = "qwen3.8:27b-mlx";

    private static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434/v1";

    private ExampleLLM() {}

    public static UnifiedLLM create() {
        String baseUrl = firstNonBlank(System.getenv("NOOA_BASE_URL"), System.getenv("OPENAI_BASE_URL"));
        String apiKey = firstNonBlank(System.getenv("NOOA_API_KEY"), System.getenv("OPENAI_API_KEY"));
        String model = firstNonBlank(System.getenv("NOOA_MODEL"), System.getenv("OPENAI_MODEL"), DEFAULT_MODEL);

        if (baseUrl != null && !baseUrl.isBlank()) {
            return UnifiedLLM.create(UnifiedLLM.custom(baseUrl,
                apiKey == null ? "demo-key" : apiKey,
                model).build());
        }
        if (apiKey != null && !apiKey.isBlank()) {
            return UnifiedLLM.create(UnifiedLLM.openAI(apiKey, model).build());
        }

        // Local Ollama over the OpenAI-compatible endpoint so tool calling and
        // structured output match the hosted provider behaviour.
        String ollamaBaseUrl = firstNonBlank(
            System.getenv("NOOA_OLLAMA_BASE_URL"), DEFAULT_OLLAMA_BASE_URL);
        return UnifiedLLM.create(UnifiedLLM.custom(ollamaBaseUrl, "ollama", model).build());
    }

    /**
     * Applies a reasoning-effort sampling override to an agent.
     *
     * @param agent         the agent whose later LLM calls should use the level
     * @param defaultEffort fallback level when no override is configured
     * @see #tune(Agent, String, Integer)
     */
    public static void applyReasoningEffort(Agent agent, String defaultEffort) {
        tune(agent, defaultEffort, null);
    }

    /**
     * Applies per-example sampling overrides to an agent.
     *
     * <p>The reasoning effort can be overridden for a whole tuning session with
     * {@code NOOA_REASONING_EFFORT} or {@code -Dnooa.reasoningEffort=...};
     * the per-turn token bound can be overridden with
     * {@code -Dnooa.maxTokens=...}.</p>
     *
     * @param agent         agent to configure
     * @param defaultEffort reasoning effort for this example's task
     * @param maxTokens     optional per-turn output bound (recommended for
     *                      CodeAct examples on local models)
     */
    public static void tune(Agent agent, String defaultEffort, Integer maxTokens) {
        String effort = firstNonBlank(
            System.getenv("NOOA_REASONING_EFFORT"),
            System.getProperty("nooa.reasoningEffort"),
            defaultEffort);
        if (effort != null && !effort.isBlank()) {
            agent.samplingOverride("reasoning_effort", effort.trim());
        }
        String rawMaxTokens = System.getProperty("nooa.maxTokens");
        Integer effectiveMaxTokens = maxTokens;
        if (rawMaxTokens != null && !rawMaxTokens.isBlank()) {
            try {
                effectiveMaxTokens = Integer.parseInt(rawMaxTokens.trim());
            } catch (NumberFormatException ignored) {
                // keep the example default
            }
        }
        if (effectiveMaxTokens != null && effectiveMaxTokens > 0) {
            agent.samplingOverride("max_tokens", effectiveMaxTokens);
        }
    }

    /** Convenience for {@code Map.ofEntries} style multi-key sampling. */
    public static Map<String, Object> sampling(String effort, Integer maxTokens) {
        var params = new java.util.HashMap<String, Object>();
        if (effort != null) params.put("reasoning_effort", effort);
        if (maxTokens != null) params.put("max_tokens", maxTokens);
        return params;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
