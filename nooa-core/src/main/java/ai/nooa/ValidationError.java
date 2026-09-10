package ai.nooa;

/** Raised when generated output fails a declared validation contract. */
public class ValidationError extends GenerationError {
    public ValidationError(String message) { super(message); }
    public ValidationError(String message, Throwable cause) { super(message, cause); }
}