package ai.nooa.examples;

import ai.nooa.llm.UnifiedLLM;

public final class ExampleLLM {
    private ExampleLLM() {}

    public static UnifiedLLM create() {
        String baseUrl = firstNonBlank(System.getenv("NOOA_BASE_URL"), System.getenv("OPENAI_BASE_URL"));
        String apiKey = firstNonBlank(System.getenv("NOOA_API_KEY"), System.getenv("OPENAI_API_KEY"));
        String model = firstNonBlank(System.getenv("NOOA_MODEL"), System.getenv("OPENAI_MODEL"), "qwen3-coder-next:latest");

        if (baseUrl != null && !baseUrl.isBlank()) {
            return UnifiedLLM.create(UnifiedLLM.custom(baseUrl,
                apiKey == null ? "demo-key" : apiKey,
                model).build());
        }
        if (apiKey != null && !apiKey.isBlank()) {
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
