package ai.nooa.strategy;

/**
 * Result of executing code snippets via the sandbox.
 */
public record ExecutionResult(
    String stdout,
    String stderr,
    String error,
    Object returnValue,
    boolean success
) {
    public static ExecutionResult ofValue(Object value) {
        return new ExecutionResult("", "", null, value, true);
    }

    public static ExecutionResult ofError(String error) {
        return new ExecutionResult("", "", error, null, false);
    }

    public static ExecutionResult ofStdout(String stdout, Object value) {
        return new ExecutionResult(stdout, "", null, value, true);
    }
}
