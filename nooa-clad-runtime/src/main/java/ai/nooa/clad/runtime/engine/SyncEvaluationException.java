package ai.nooa.clad.runtime.engine;

/**
 * Thrown by the predicate engine when a concept produces an outcome
 * that no sync handles. This is a protocol violation — the developer
 * must add a sync rule for the outcome.
 *
 * <p>Also thrown when the atomic composite write (completion + syncs) fails.</p>
 */
public class SyncEvaluationException extends RuntimeException {
    public SyncEvaluationException(String message) {
        super(message);
    }
    public SyncEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
