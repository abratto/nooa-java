package ai.nooa.examples.incident;

/**
 * Outcome of applying an event to a state. Modelling rejection as data (rather
 * than throwing) keeps {@link IncidentStateMachine#transition} a total, pure
 * function and mirrors NOOA's preference for typed outcomes over exceptions.
 */
public sealed interface Transition
    permits Transition.Accepted, Transition.Rejected {

    /** The event was legal; the workflow advances to {@code next}. */
    record Accepted(IncidentState next) implements Transition {}

    /** The event was illegal in the current state; the state is unchanged. */
    record Rejected(IncidentState current, IncidentEvent event, String reason)
        implements Transition {}
}
