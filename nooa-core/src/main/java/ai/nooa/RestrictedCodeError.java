package ai.nooa;

/** Raised when generated code violates sandbox policy. */
public class RestrictedCodeError extends GenerationError {
    public RestrictedCodeError(String message) { super(message); }
}