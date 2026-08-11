package ai.nooa.config;

/**
 * Configuration for the {@link ai.nooa.strategy.PredictStrategy}.
 */
public record PredictConfig(
    int maxRetries,
    Integer maxTokens,
    Float temperature
) {
    public static PredictConfig defaults() {
        return new PredictConfig(3, null, null);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int maxRetries = 3;
        private Integer maxTokens = null;
        private Float temperature = null;

        public Builder maxRetries(int v) { this.maxRetries = v; return this; }
        public Builder maxTokens(int v) { this.maxTokens = v; return this; }
        public Builder temperature(float v) { this.temperature = v; return this; }

        public PredictConfig build() {
            return new PredictConfig(maxRetries, maxTokens, temperature);
        }
    }
}
