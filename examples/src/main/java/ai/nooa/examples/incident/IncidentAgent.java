package ai.nooa.examples.incident;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Hidden;
import ai.nooa.annotations.Strategy;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.strategy.PredictStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * An incident-response agent whose workflow is a compile-time-checked state
 * machine (see {@link IncidentStateMachine}).
 *
 * <p>The object-first split is deliberate:</p>
 * <ul>
 *   <li><b>Java owns the workflow.</b> {@link #respond} sequences the steps and
 *       is the only place the state advances, via {@link #fire}. Illegal
 *       transitions raise a typed {@link IllegalTransition}.</li>
 *   <li><b>The model produces typed payloads.</b> {@code assess},
 *       {@code investigate}, {@code proposePlan}, and {@code writePostmortem}
 *       use {@link PredictStrategy} with record contracts; {@code scanTelemetry}
 *       uses the default CodeAct strategy to inspect telemetry with helper
 *       methods (code-as-action) and return a compact observations string.</li>
 *   <li><b>Humans approve</b> the remediation through an {@link ApprovalGate}
 *       before it is applied.</li>
 * </ul>
 */
@SystemPrompt("""
    You are an incident-response commander. Investigate alerts using the
    telemetry helper methods, form a grounded root-cause hypothesis, and propose
    a minimal, reversible remediation. Be concise, factual, and do not invent
    telemetry you were not given.
    """)
public class IncidentAgent extends Agent {

    private static final ApprovalGate AUTO_APPROVE = plan -> true;
    private static final ApprovalGate REJECT = plan -> false;

    @Hidden private final IncidentScenarios.Scenario scenario;
    @Hidden private IncidentState state;
    @Hidden private String status = "Detected";
    @Hidden private final List<String> audit = new ArrayList<>();

    public IncidentAgent(UnifiedLLM llm, IncidentScenarios.Scenario scenario) {
        super(llm);
        this.scenario = scenario;
        this.state = new IncidentState.Detected(scenario.report());
        // The model can always see the current workflow state in its context.
        context().putDynamic("incident_state", "self.status()");
    }

    // ---- model-powered steps ----

    @Generate(prompt = "Assess the severity of the incident. Return a level "
        + "(LOW, MEDIUM, HIGH, or CRITICAL) and a one-sentence rationale.")
    @Strategy(PredictStrategy.class)
    public Severity assess(IncidentReport report) {
        throw new UnsupportedOperationException();
    }

    @Generate(prompt = "Inspect the telemetry using the helper methods "
        + "(breachedMetrics(), errorLogs(limit), recentDeploys()). Return a "
        + "concise, factual summary of the most relevant observations.")
    public String scanTelemetry() {
        throw new UnsupportedOperationException();
    }

    @Generate(prompt = "Using the incident report and the observations, state the "
        + "most likely root cause and the concrete evidence that supports it.")
    @Strategy(PredictStrategy.class)
    public Hypothesis investigate(IncidentReport report, String observations) {
        throw new UnsupportedOperationException();
    }

    @Generate(prompt = "Propose a minimal, reversible remediation. Return "
        + "concrete steps, the risk, and a rollback plan.")
    @Strategy(PredictStrategy.class)
    public RemediationPlan proposePlan(IncidentReport report, Hypothesis hypothesis) {
        throw new UnsupportedOperationException();
    }

    @Generate(prompt = "Write a short postmortem: a summary and a few follow-up actions.")
    @Strategy(PredictStrategy.class)
    public Postmortem writePostmortem(IncidentReport report, Hypothesis hypothesis,
                                      RemediationPlan plan) {
        throw new UnsupportedOperationException();
    }

    // ---- deterministic, model-callable telemetry helpers ----

    /** The full telemetry snapshot the agent may reason about. */
    public TelemetrySnapshot collectTelemetry() {
        return scenario.telemetry();
    }

    /** Only the metrics that breached their threshold. */
    public List<TelemetrySnapshot.Metric> breachedMetrics() {
        return scenario.telemetry().metrics().stream()
            .filter(TelemetrySnapshot.Metric::breached)
            .toList();
    }

    /** The most recent WARN/ERROR log lines, up to {@code limit}. */
    public List<TelemetrySnapshot.LogLine> errorLogs(int limit) {
        return scenario.telemetry().logs().stream()
            .filter(line -> "ERROR".equals(line.level()) || "WARN".equals(line.level()))
            .limit(Math.max(0, limit))
            .toList();
    }

    /** Recent deploys, so the model can connect a change to the symptoms. */
    public List<String> recentDeploys() {
        return scenario.telemetry().recentDeploys();
    }

    // ---- workflow orchestration (Java owns the transitions) ----

    @Hidden
    public IncidentOutcome respond(ApprovalGate gate) {
        IncidentReport report = scenario.report();

        var severity = assess(report);
        fire(new IncidentEvent.Triage(severity, report.summary()));

        if (severity.level() == SeverityLevel.CRITICAL) {
            fire(new IncidentEvent.Escalate("critical severity: " + severity.rationale()));
            return outcome();
        }

        var hypothesis = investigate(report, scanTelemetry());
        fire(new IncidentEvent.Investigate(hypothesis));

        var plan = proposePlan(report, hypothesis);
        fire(new IncidentEvent.ProposeMitigation(plan));

        if (gate.approve(plan)) {
            fire(new IncidentEvent.Approve(gate.reviewer()));
            var verification = verify(plan);
            fire(new IncidentEvent.Verify(verification.healthy(), verification.notes()));
            if (verification.healthy()) {
                fire(new IncidentEvent.Resolve(writePostmortem(report, hypothesis, plan)));
            }
        } else {
            fire(new IncidentEvent.Reject("declined by reviewer"));
        }
        return outcome();
    }

    /** Convenience entry point for demos and evals: approve the plan automatically. */
    @Hidden
    public IncidentOutcome respondAuto() {
        return respond(AUTO_APPROVE);
    }

    /** Convenience entry point for demos and evals: reject the plan. */
    @Hidden
    public IncidentOutcome respondRejected() {
        return respond(REJECT);
    }

    // ---- deterministic verification ----

    record Verification(boolean healthy, String notes) {}

    /** Deterministic stand-in for a post-mitigation health check. */
    public Verification verify(RemediationPlan plan) {
        boolean healthy = plan.rollback() != null && !plan.rollback().isBlank();
        return new Verification(healthy, healthy ? "health check recovered" : "no rollback defined");
    }

    // ---- state machine glue ----

    /** Apply an event; illegal transitions raise {@link IllegalTransition}. */
    @Hidden
    public void fire(IncidentEvent event) {
        Transition transition = IncidentStateMachine.transition(state, event);
        switch (transition) {
            case Transition.Accepted accepted -> {
                state = accepted.next();
                status = state.getClass().getSimpleName();
                audit.add(status + " <- " + event.getClass().getSimpleName());
            }
            case Transition.Rejected rejected -> throw new IllegalTransition(rejected.reason());
        }
    }

    @Hidden
    public IncidentState state() {
        return state;
    }

    public String status() {
        return status;
    }

    public List<String> audit() {
        return List.copyOf(audit);
    }

    private IncidentOutcome outcome() {
        return new IncidentOutcome(scenario.report().id(), status, state, audit);
    }
}
