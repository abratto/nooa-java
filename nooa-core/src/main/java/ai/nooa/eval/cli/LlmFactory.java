package ai.nooa.eval.cli;

import ai.nooa.llm.UnifiedLLM;

/**
 * Builds a {@link UnifiedLLM} from environment configuration for the
 * {@code nooa-eval} CLI.
 *
 * <p>Resolution order: {@code NOOA_BASE_URL}/{@code OPENAI_BASE_URL} for any
 * OpenAI-compatible endpoint, then {@code NOOA_API_KEY}/{@code OPENAI_API_KEY}
 * against OpenAI, then local Ollama. Model comes from
 * {@code NOOA_MODEL}/{@code OPENAI_MODEL}.</p>
 */
public final class LlmFactory {

    private LlmFactory() {}

    public static UnifiedLLM fromEnv() {
        String baseUrl = firstNonBlank(
            System.getenv("NOOA_BASE_URL"), System.getenv("OPENAI_BASE_URL"));
        String apiKey = firstNonBlank(
            System.getenv("NOOA_API_KEY"), System.getenv("OPENAI_API_KEY"));
        String model = firstNonBlank(
            System.getenv("NOOA_MODEL"), System.getenv("OPENAI_MODEL"), "qwen3-coder-next:latest");

        if (baseUrl != null) {
            return UnifiedLLM.create(UnifiedLLM.custom(baseUrl,
                apiKey == null ? "demo-key" : apiKey, model).build());
        }
        if (apiKey != null) {
            return UnifiedLLM.create(UnifiedLLM.openAI(apiKey, model).build());
        }
        return UnifiedLLM.create(UnifiedLLM.ollama(model).build());
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
