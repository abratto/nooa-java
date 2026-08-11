package ai.nooa.config;

/**
 * Configuration for the {@link ai.nooa.strategy.CodeActStrategy}.
 */
public record CodeActConfig(
    int maxIterations,
    int maxRetries,
    int maxConsecutiveTextOnly,
    long cellTimeoutMillis
) {
    public static CodeActConfig defaults() {
        return new CodeActConfig(50, 3, 3, 90_000);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int maxIterations = 50;
        private int maxRetries = 3;
        private int maxConsecutiveTextOnly = 3;
        private long cellTimeoutMillis = 90_000;

        public Builder maxIterations(int v) { this.maxIterations = v; return this; }
        public Builder maxRetries(int v) { this.maxRetries = v; return this; }
        public Builder maxConsecutiveTextOnly(int v) { this.maxConsecutiveTextOnly = v; return this; }
        public Builder cellTimeoutMillis(long v) { this.cellTimeoutMillis = v; return this; }

        public CodeActConfig build() {
            return new CodeActConfig(maxIterations, maxRetries, maxConsecutiveTextOnly, cellTimeoutMillis);
        }
    }
}
