package ai.nooa.examples.clad;

import java.util.List;

/**
 * Structured representation of a CONTEXT.md contract.
 */
public record StageContract(
    String stageId,
    String stageName,
    List<String> inputs,
    String process,
    List<String> outputs,
    List<String> verifySteps,
    boolean hasHumanGate
) {
    @Override
    public String toString() {
        return "Stage " + stageId + ": " + stageName
            + "\n  Inputs: " + inputs
            + "\n  Outputs: " + outputs
            + "\n  Gate: " + (hasHumanGate ? "YES" : "auto-advance");
    }
}
