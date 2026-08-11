package ai.nooa.clad;

import java.util.List;

public record CladStage(
    String stageId,
    String stageName,
    List<String> inputs,
    String process,
    List<String> outputs,
    List<String> verifySteps,
    boolean hasHumanGate
) {}
