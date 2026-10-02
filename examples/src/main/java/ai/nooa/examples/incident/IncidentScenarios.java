package ai.nooa.examples.incident;

import ai.nooa.examples.incident.TelemetrySnapshot.LogLine;
import ai.nooa.examples.incident.TelemetrySnapshot.Metric;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Canned, deterministic incident scenarios used by the demo and tests. Each
 * scenario pairs an alert ({@link IncidentReport}) with the telemetry the agent
 * investigates, so no external monitoring system is required.
 */
public final class IncidentScenarios {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    private IncidentScenarios() {}

    public record Scenario(String name, IncidentReport report, TelemetrySnapshot telemetry) {}

    public static Scenario checkoutLatency() {
        var report = new IncidentReport(
            "INC-1001", "checkout-api", "prod",
            "p99 latency above 2s for 10 minutes",
            List.of("p99 2.4s", "5xx rate 4.1%", "checkout success rate down 18%"));
        var telemetry = new TelemetrySnapshot(
            List.of(
                new Metric("http_p99_latency_ms", 2400, 800, true),
                new Metric("http_5xx_rate", 0.041, 0.01, true),
                new Metric("db_pool_active", 50, 40, true),
                new Metric("cpu_usage", 0.62, 0.9, false)),
            List.of(
                new LogLine(BASE.plusSeconds(30), "WARN", "HikariPool-1 - Connection is not available, request timed out after 30000ms"),
                new LogLine(BASE.plusSeconds(45), "WARN", "HikariPool-1 - Connection is not available, request timed out after 30000ms"),
                new LogLine(BASE.plusSeconds(60), "ERROR", "Failed to acquire JDBC connection for order settlement")),
            List.of("checkout-api@2025.12.31-a1b2c3"),
            Map.of("new-settlement-path", "on"));
        return new Scenario("checkout-latency", report, telemetry);
    }

    public static Scenario authErrorSurge() {
        var report = new IncidentReport(
            "INC-1002", "auth-service", "prod",
            "401 responses up 30x in the last 15 minutes",
            List.of("401 rate 12%", "login failures spiking", "no latency change"));
        var telemetry = new TelemetrySnapshot(
            List.of(
                new Metric("http_401_rate", 0.12, 0.02, true),
                new Metric("http_p99_latency_ms", 210, 800, false),
                new Metric("token_refresh_errors", 480, 50, true)),
            List.of(
                new LogLine(BASE.plusSeconds(20), "WARN", "JWT validation failed: audience mismatch"),
                new LogLine(BASE.plusSeconds(22), "WARN", "JWT validation failed: audience mismatch")),
            List.of("auth-service@2025.12.31-f9e8d7"),
            Map.of("strict-audience-check", "on"));
        return new Scenario("auth-error-surge", report, telemetry);
    }

    public static Scenario diskPressure() {
        var report = new IncidentReport(
            "INC-1003", "metrics-node-3", "prod",
            "disk usage at 95% on metrics node",
            List.of("disk 95%", "write latency rising"));
        var telemetry = new TelemetrySnapshot(
            List.of(
                new Metric("disk_used_ratio", 0.95, 0.85, true),
                new Metric("disk_write_latency_ms", 340, 100, true)),
            List.of(
                new LogLine(BASE.plusSeconds(10), "WARN", "segment rotation delayed: no space left on device")),
            List.of(),
            Map.of());
        return new Scenario("node-disk-pressure", report, telemetry);
    }

    public static List<Scenario> all() {
        return List.of(checkoutLatency(), authErrorSurge(), diskPressure());
    }
}
