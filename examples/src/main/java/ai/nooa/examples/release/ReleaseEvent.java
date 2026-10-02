package ai.nooa.examples.release;

/** Payload-free release-gate events (the enum-style FSM). */
public enum ReleaseEvent {
    START_BUILD,
    TESTS_PASS,
    TESTS_FAIL,
    APPROVE,
    REJECT,
    DEPLOY,
    ROLLBACK
}
