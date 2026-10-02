package ai.nooa.examples.incident;

/**
 * Incident workflow events as a sealed type. Events are the only way state
 * advances; the model proposes their payloads, but Java decides whether a given
 * event is legal in the current state.
 */
public sealed interface IncidentEvent
    permits IncidentEvent.Triage,
            IncidentEvent.Investigate,
            IncidentEvent.ProposeMitigation,
            IncidentEvent.Approve,
            IncidentEvent.Reject,
            IncidentEvent.Verify,
            IncidentEvent.Resolve,
            IncidentEvent.Escalate {

    record Triage(Severity severity, String summary) implements IncidentEvent {}

    record Investigate(Hypothesis hypothesis) implements IncidentEvent {}

    record ProposeMitigation(RemediationPlan plan) implements IncidentEvent {}

    record Approve(String reviewer) implements IncidentEvent {}

    record Reject(String reason) implements IncidentEvent {}

    record Verify(boolean healthy, String notes) implements IncidentEvent {}

    record Resolve(Postmortem postmortem) implements IncidentEvent {}

    record Escalate(String reason) implements IncidentEvent {}
}
