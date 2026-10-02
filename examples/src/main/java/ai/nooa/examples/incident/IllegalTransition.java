package ai.nooa.examples.incident;

/**
 * Raised when the workflow is asked to apply an event that is illegal in the
 * current state (see {@link Transition.Rejected}). {@link IncidentAgent#fire}
 * is the only place this is thrown; the pure state machine never throws.
 */
public final class IllegalTransition extends RuntimeException {
    public IllegalTransition(String message) {
        super(message);
    }
}
