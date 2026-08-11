package ai.nooa.config;

/**
 * Controls output truncation for LLM context — stdout/stderr size limits,
 * structural bounds on pformat output, and context-window token budgets.
 */
public record TruncationConfig(
    int maxStdout,
    int maxStderr,
    int maxError,
    int maxLength,    // max container elements in pformat
    int maxString,     // max string chars in pformat
    int maxDepth       // max nesting depth in pformat
) {
    public static TruncationConfig defaults() {
        return new TruncationConfig(8192, 4096, 4096, 50, 500, 4);
    }

    /**
     * Merge this config with an override. Non-default values in the override
     * take precedence. Used for instance-level over class-level over defaults.
     */
    public TruncationConfig mergeWith(TruncationConfig override) {
        return new TruncationConfig(
            override.maxStdout != 8192 ? override.maxStdout : maxStdout,
            override.maxStderr != 4096 ? override.maxStderr : maxStderr,
            override.maxError != 4096 ? override.maxError : maxError,
            override.maxLength != 50 ? override.maxLength : maxLength,
            override.maxString != 500 ? override.maxString : maxString,
            override.maxDepth != 4 ? override.maxDepth : maxDepth
        );
    }
}
