package ai.nooa.examples.incident;

import java.util.List;

/**
 * The result of running the workflow: the terminal status, the final state, and
 * the transition audit trail.
 */
public record IncidentOutcome(
    String incidentId,
    String finalStatus,
    IncidentState finalState,
    List<String> audit) {

    public IncidentOutcome {
        audit = audit == null ? List.of() : List.copyOf(audit);
    }
}
