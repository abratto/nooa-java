package ai.nooa.examples.incident;

import java.util.List;

/**
 * The alert that triggers the workflow. This is the agent's typed input: a
 * Java method call with an {@code IncidentReport}, not free-form chat.
 */
public record IncidentReport(
    String id,
    String service,
    String environment,
    String summary,
    List<String> symptoms) {

    public IncidentReport {
        symptoms = symptoms == null ? List.of() : List.copyOf(symptoms);
    }
}
