package ai.nooa.examples.release;

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
 * A small agent that drives the enum-style {@link ReleasePhase} state machine.
 *
 * <p>This is the payload-free counterpart to {@link ai.nooa.examples.incident.IncidentAgent}:
 * the model supplies exactly one bit of content (a change summary) while Java
 * owns every transition. Use this shape when the states carry no data.</p>
 */
@SystemPrompt("You are a release assistant. Summarize the change for reviewers in one sentence.")
public class ReleaseGateAgent extends Agent {

    @Hidden private ReleasePhase phase = ReleasePhase.PLANNED;
    @Hidden private final List<String> history = new ArrayList<>();

    public ReleaseGateAgent(UnifiedLLM llm) {
        super(llm);
        context().putDynamic("release_phase", "self.phase().name()");
    }

    @Generate(prompt = "Summarize the change described by the ticket in one sentence.")
    @Strategy(PredictStrategy.class)
    public String summarize(String ticket) {
        throw new UnsupportedOperationException();
    }

    /**
     * Run the release gate: summarize the change, then walk the phases until a
     * terminal phase is reached. The two booleans stand in for external signals
     * (test result, human approval).
     */
    @Hidden
    public String run(String ticket, boolean testsPass, boolean approve) {
        String summary = summarize(ticket);
        advance(ReleaseEvent.START_BUILD);
        advance(testsPass ? ReleaseEvent.TESTS_PASS : ReleaseEvent.TESTS_FAIL);
        if (phase == ReleasePhase.TESTING) {
            advance(approve ? ReleaseEvent.APPROVE : ReleaseEvent.REJECT);
            if (phase == ReleasePhase.APPROVED) {
                advance(ReleaseEvent.DEPLOY);
            }
        }
        return summary + " -> " + phase;
    }

    @Hidden
    public void advance(ReleaseEvent event) {
        phase = phase.advance(event);
        history.add(phase.name() + " <- " + event);
    }

    public ReleasePhase phase() {
        return phase;
    }

    public List<String> history() {
        return List.copyOf(history);
    }
}
