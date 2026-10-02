package ai.nooa.examples.incident;

import java.util.List;

/**
 * A root-cause hypothesis with supporting evidence, produced by the CodeAct
 * investigation step.
 */
public record Hypothesis(String summary, List<String> evidence) {
    public Hypothesis {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
