package ai.nooa.runtime;

import ai.nooa.NooaException;

/** Raised when a durable session cannot be read or written. */
public class SessionStorageError extends NooaException {
    public SessionStorageError(String message, Throwable cause) { super(message, cause); }
}