package ai.nooa.examples.incident;

/**
 * Incident workflow states as an algebraic data type: a sealed interface whose
 * records carry the state-specific payload. Being sealed, the compiler can
 * prove the transition {@code switch} in {@link IncidentStateMachine} handles
 * every state.
 */
public sealed interface IncidentState
    permits IncidentState.Detected,
            IncidentState.Triaged,
            IncidentState.Investigating,
            IncidentState.MitigationProposed,
            IncidentState.Mitigating,
            IncidentState.Resolved,
            IncidentState.Escalated {

    /** Initial state: the alert has arrived but is not yet triaged. */
    record Detected(IncidentReport report) implements IncidentState {}

    /** Triage verdict. */
    record Triaged(Severity severity, String summary) implements IncidentState {}

    /** A root-cause hypothesis is being pursued. */
    record Investigating(Hypothesis hypothesis) implements IncidentState {}

    /** A remediation is proposed and awaiting human approval. */
    record MitigationProposed(RemediationPlan plan) implements IncidentState {}

    /** Approved remediation in progress; {@code verified} gates resolution. */
    record Mitigating(RemediationPlan plan, String approver, boolean verified)
        implements IncidentState {}

    /** Terminal: resolved with a postmortem. */
    record Resolved(Postmortem postmortem) implements IncidentState {}

    /** Terminal: escalated to humans with a reason. */
    record Escalated(String reason) implements IncidentState {}
}
