package ai.nooa.config;

/**
 * Framework-level execution guards.
 */
public record ExecutionConfig(
    int maxNestingDepth   // max nested agent-within-agent calls
) {
    public static ExecutionConfig defaults() {
        return new ExecutionConfig(50);
    }
}
