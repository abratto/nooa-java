package ai.nooa.examples.incident;

import ai.nooa.examples.incident.IncidentEvent.Approve;
import ai.nooa.examples.incident.IncidentEvent.Escalate;
import ai.nooa.examples.incident.IncidentEvent.Investigate;
import ai.nooa.examples.incident.IncidentEvent.ProposeMitigation;
import ai.nooa.examples.incident.IncidentEvent.Reject;
import ai.nooa.examples.incident.IncidentEvent.Resolve;
import ai.nooa.examples.incident.IncidentEvent.Triage;
import ai.nooa.examples.incident.IncidentEvent.Verify;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IncidentStateMachine (pure transitions)")
class IncidentStateMachineTest {

    private static final IncidentReport REPORT =
        new IncidentReport("INC-1", "checkout-api", "prod", "summary", List.of("symptom"));
    private static final Severity HIGH = new Severity(SeverityLevel.HIGH, "breach");
    private static final Hypothesis HYPOTHESIS = new Hypothesis("db pool", List.of("timeouts"));
    private static final RemediationPlan PLAN =
        new RemediationPlan(List.of("rollback"), "low", "redeploy previous");
    private static final Postmortem POSTMORTEM = new Postmortem("done", List.of("alert"));

    private static IncidentState next(Transition transition) {
        assertThat(transition).isInstanceOf(Transition.Accepted.class);
        return ((Transition.Accepted) transition).next();
    }

    @Test
    void happyPathAdvancesThroughEveryState() {
        IncidentState state = new IncidentState.Detected(REPORT);
        state = next(IncidentStateMachine.transition(state, new Triage(HIGH, "s")));
        assertThat(state).isInstanceOf(IncidentState.Triaged.class);

        state = next(IncidentStateMachine.transition(state, new Investigate(HYPOTHESIS)));
        assertThat(state).isInstanceOf(IncidentState.Investigating.class);

        state = next(IncidentStateMachine.transition(state, new ProposeMitigation(PLAN)));
        assertThat(state).isInstanceOf(IncidentState.MitigationProposed.class);

        state = next(IncidentStateMachine.transition(state, new Approve("alice")));
        assertThat(state).isEqualTo(new IncidentState.Mitigating(PLAN, "alice", false));

        state = next(IncidentStateMachine.transition(state, new Verify(true, "ok")));
        assertThat(state).isEqualTo(new IncidentState.Mitigating(PLAN, "alice", true));

        state = next(IncidentStateMachine.transition(state, new Resolve(POSTMORTEM)));
        assertThat(state).isEqualTo(new IncidentState.Resolved(POSTMORTEM));
    }

    @Test
    void illegalEventsAreRejectedAsData() {
        var detected = new IncidentState.Detected(REPORT);
        assertThat(IncidentStateMachine.transition(detected, new Approve("alice")))
            .isInstanceOf(Transition.Rejected.class);

        var proposed = new IncidentState.MitigationProposed(PLAN);
        assertThat(IncidentStateMachine.transition(proposed, new Verify(true, "ok")))
            .isInstanceOf(Transition.Rejected.class);
    }

    @Test
    void resolvingBeforeVerificationIsRejected() {
        var mitigating = new IncidentState.Mitigating(PLAN, "alice", false);
        assertThat(IncidentStateMachine.transition(mitigating, new Resolve(POSTMORTEM)))
            .isInstanceOf(Transition.Rejected.class);
    }

    @Test
    void failedVerificationEscalates() {
        var mitigating = new IncidentState.Mitigating(PLAN, "alice", true);
        assertThat(next(IncidentStateMachine.transition(mitigating, new Verify(false, "still bad"))))
            .isEqualTo(new IncidentState.Escalated("verification failed: still bad"));
    }

    @Test
    void rejectionAndEscalationReachTerminalStates() {
        var proposed = new IncidentState.MitigationProposed(PLAN);
        assertThat(next(IncidentStateMachine.transition(proposed, new Reject("no"))))
            .isEqualTo(new IncidentState.Escalated("mitigation rejected: no"));

        var triaged = new IncidentState.Triaged(HIGH, "s");
        assertThat(next(IncidentStateMachine.transition(triaged, new Escalate("critical"))))
            .isEqualTo(new IncidentState.Escalated("critical"));
    }

    @Test
    void terminalStatesRejectEverything() {
        for (IncidentState terminal : List.of(
                new IncidentState.Resolved(POSTMORTEM),
                new IncidentState.Escalated("done"))) {
            assertThat(IncidentStateMachine.transition(terminal, new Triage(HIGH, "s")))
                .isInstanceOf(Transition.Rejected.class);
        }
    }
}
