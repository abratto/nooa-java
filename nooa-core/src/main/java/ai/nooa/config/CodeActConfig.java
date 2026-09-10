package ai.nooa.config;

import ai.nooa.strategy.Prefill;
import ai.nooa.strategy.InspectInputsPrefill;

/**
 * Configuration for the {@link ai.nooa.strategy.CodeActStrategy}.
 */
public record CodeActConfig(
    int maxIterations,
    int maxRetries,
    int maxConsecutiveTextOnly,
    long cellTimeoutMillis,
    boolean allowTextFallback,
    Prefill prefill
) {
    public CodeActConfig(int maxIterations, int maxRetries, int maxConsecutiveTextOnly, long cellTimeoutMillis) {
        this(maxIterations, maxRetries, maxConsecutiveTextOnly, cellTimeoutMillis, true, null);
    }

    public CodeActConfig(int maxIterations, int maxRetries, int maxConsecutiveTextOnly,
                         long cellTimeoutMillis, boolean allowTextFallback) {
        this(maxIterations, maxRetries, maxConsecutiveTextOnly, cellTimeoutMillis,
            allowTextFallback, null);
    }

    public static CodeActConfig defaults() {
        return new CodeActConfig(50, 3, 3, 90_000, true, new InspectInputsPrefill());
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int maxIterations = 50;
        private int maxRetries = 3;
        private int maxConsecutiveTextOnly = 3;
        private long cellTimeoutMillis = 90_000;
        private boolean allowTextFallback = true;
        private Prefill prefill = new InspectInputsPrefill();

        public Builder maxIterations(int v) { this.maxIterations = v; return this; }
        public Builder maxRetries(int v) { this.maxRetries = v; return this; }
        public Builder maxConsecutiveTextOnly(int v) { this.maxConsecutiveTextOnly = v; return this; }
        public Builder cellTimeoutMillis(long v) { this.cellTimeoutMillis = v; return this; }
        public Builder allowTextFallback(boolean v) { this.allowTextFallback = v; return this; }
        public Builder prefill(Prefill v) { this.prefill = v; return this; }

        public CodeActConfig build() {
            return new CodeActConfig(maxIterations, maxRetries, maxConsecutiveTextOnly,
                cellTimeoutMillis, allowTextFallback, prefill);
        }
    }
}
