package ai.nooa;

/** Raised when generation is explicitly aborted. */
public class AbortError extends GenerationError {
    public AbortError(String message) { super(message); }
}