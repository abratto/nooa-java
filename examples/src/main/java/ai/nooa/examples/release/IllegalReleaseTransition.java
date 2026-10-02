package ai.nooa.examples.release;

/** Raised by the enum-style FSM for an illegal release transition. */
public final class IllegalReleaseTransition extends RuntimeException {
    public IllegalReleaseTransition(String message) {
        super(message);
    }
}
