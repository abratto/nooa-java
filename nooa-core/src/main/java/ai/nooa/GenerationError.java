package ai.nooa;

/**
 * Thrown when LLM generation fails after exhausting retries.
 */
public class GenerationError extends NooaException {
    public GenerationError(String message) { super(message); }
    public GenerationError(String message, Throwable cause) { super(message, cause); }
}
