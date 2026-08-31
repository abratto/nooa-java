package ai.nooa.config;

/**
 * Configuration for the {@link ai.nooa.strategy.PredictStrategy}.
 */
public record PredictConfig(
    int maxRetries,
    Integer maxTokens,
    Float temperature,
    String reasoningEffort
) {
    public static PredictConfig defaults() {
        return new PredictConfig(3, null, null, null);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int maxRetries = 3;
        private Integer maxTokens = null;
        private Float temperature = null;
        private String reasoningEffort = null;

        public Builder maxRetries(int v) { this.maxRetries = v; return this; }
        public Builder maxTokens(int v) { this.maxTokens = v; return this; }
        public Builder temperature(float v) { this.temperature = v; return this; }
        public Builder reasoningEffort(String v) { this.reasoningEffort = v; return this; }

        public PredictConfig build() {
            return new PredictConfig(maxRetries, maxTokens, temperature, reasoningEffort);
        }
    }
}
