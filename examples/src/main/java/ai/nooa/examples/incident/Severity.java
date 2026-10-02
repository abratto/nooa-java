package ai.nooa.examples.incident;

/**
 * The model's triage verdict. Produced by {@link IncidentAgent#assess} with
 * {@code PredictStrategy}, so the record fields are the enforced output schema.
 */
public record Severity(SeverityLevel level, String rationale) {}
