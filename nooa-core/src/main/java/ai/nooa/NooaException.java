package ai.nooa;

/**
 * Base exception for all NOOA errors.
 */
public class NooaException extends RuntimeException {
    public NooaException(String message) { super(message); }
    public NooaException(String message, Throwable cause) { super(message, cause); }
}
