package ai.nooa.examples.incident;

import ai.nooa.examples.incident.IncidentEvent.Approve;
import ai.nooa.examples.incident.IncidentEvent.Escalate;
import ai.nooa.examples.incident.IncidentEvent.Investigate;
import ai.nooa.examples.incident.IncidentEvent.ProposeMitigation;
import ai.nooa.examples.incident.IncidentEvent.Reject;
import ai.nooa.examples.incident.IncidentEvent.Resolve;
import ai.nooa.examples.incident.IncidentEvent.Triage;
import ai.nooa.examples.incident.IncidentEvent.Verify;
import ai.nooa.examples.incident.IncidentState.Detected;
import ai.nooa.examples.incident.IncidentState.Escalated;
import ai.nooa.examples.incident.IncidentState.Investigating;
import ai.nooa.examples.incident.IncidentState.Mitigating;
import ai.nooa.examples.incident.IncidentState.MitigationProposed;
import ai.nooa.examples.incident.IncidentState.Resolved;
import ai.nooa.examples.incident.IncidentState.Triaged;

/**
 * The incident workflow as a pure function {@code (state, event) -> Transition}.
 *
 * <ul>
 *   <li>The outer {@code switch} is exhaustive over the sealed state type, so a
 *       new state cannot be added without handling it.</li>
 *   <li>Record patterns and {@code when} guards express the rules directly, e.g.
 *       a mitigation may only be resolved once verification has passed.</li>
 *   <li>Illegal transitions are returned as {@link Transition.Rejected} data.</li>
 * </ul>
 */
public final class IncidentStateMachine {

    private IncidentStateMachine() {}

    public static Transition transition(IncidentState current, IncidentEvent event) {
        return switch (current) {
            case Detected state -> switch (event) {
                case Triage e -> accepted(new Triaged(e.severity(), e.summary()));
                case Escalate e -> accepted(new Escalated(e.reason()));
                default -> reject(state, event);
            };

            case Triaged state -> switch (event) {
                case Investigate e -> accepted(new Investigating(e.hypothesis()));
                case Escalate e -> accepted(new Escalated(e.reason()));
                default -> reject(state, event);
            };

            case Investigating state -> switch (event) {
                case ProposeMitigation e -> accepted(new MitigationProposed(e.plan()));
                case Escalate e -> accepted(new Escalated(e.reason()));
                default -> reject(state, event);
            };

            case MitigationProposed state -> switch (event) {
                case Approve e -> accepted(new Mitigating(state.plan(), e.reviewer(), false));
                case Reject e -> accepted(new Escalated("mitigation rejected: " + e.reason()));
                default -> reject(state, event);
            };

            case Mitigating state -> switch (event) {
                // Guard: only a passing verification keeps the workflow alive.
                case Verify e when e.healthy() ->
                    accepted(new Mitigating(state.plan(), state.approver(), true));
                case Verify e ->
                    accepted(new Escalated("verification failed: " + e.notes()));
                // Guard: resolution requires prior successful verification.
                case Resolve e when state.verified() -> accepted(new Resolved(e.postmortem()));
                case Resolve e -> reject(state, event);
                case Escalate e -> accepted(new Escalated(e.reason()));
                default -> reject(state, event);
            };

            // Terminal states: nothing may advance them.
            case Resolved state -> reject(state, event);
            case Escalated state -> reject(state, event);
        };
    }

    private static Transition accepted(IncidentState next) {
        return new Transition.Accepted(next);
    }

    private static Transition reject(IncidentState state, IncidentEvent event) {
        return new Transition.Rejected(state, event,
            "Cannot apply " + event.getClass().getSimpleName()
                + " while in state " + state.getClass().getSimpleName());
    }
}
