package ai.nooa.examples.incident;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A deterministic snapshot of monitoring telemetry for an incident. In a real
 * deployment this would come from a metrics/log backend; here it is generated
 * in-process so the example and its tests run without network access.
 *
 * <p>The agent exposes slices of this snapshot to the model as callable helper
 * methods ({@link IncidentAgent#breachedMetrics()}, {@link IncidentAgent#errorLogs(int)},
 * {@link IncidentAgent#recentDeploys()}), so the investigation step grounds on
 * real facts rather than a description of them.</p>
 */
public record TelemetrySnapshot(
    List<Metric> metrics,
    List<LogLine> logs,
    List<String> recentDeploys,
    Map<String, String> featureFlags) {

    public TelemetrySnapshot {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        logs = logs == null ? List.of() : List.copyOf(logs);
        recentDeploys = recentDeploys == null ? List.of() : List.copyOf(recentDeploys);
        featureFlags = featureFlags == null ? Map.of() : Map.copyOf(featureFlags);
    }

    /** A single observed metric and whether it breached its threshold. */
    public record Metric(String name, double value, double threshold, boolean breached) {}

    /** A single log line. */
    public record LogLine(Instant at, String level, String message) {}
}
