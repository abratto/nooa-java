package ai.nooa.examples.incident;

import java.util.List;

/** A proposed remediation, subject to human approval before it is applied. */
public record RemediationPlan(List<String> steps, String risk, String rollback) {
    public RemediationPlan {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
