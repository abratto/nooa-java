package ai.nooa.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracing configuration for NOOA agents.
 *
 * <pre>{@code
 * // Auto-enable: set NOOA_TRACE_DIR env var
 * // Or programmatic:
 * Tracing.enable(Tracing.jsonl(Path.of("./traces")));
 * }</pre>
 */
public final class Tracing {

    private static final Logger log = LoggerFactory.getLogger(Tracing.class);
    private static final String INSTRUMENTATION_NAME = "nooa-java";
    private static final AtomicReference<OpenTelemetry> openTelemetry = new AtomicReference<>();
    private static final AtomicReference<Tracer> tracer = new AtomicReference<>();

    private Tracing() {}

    /** Enable tracing with one or more exporters. */
    public static synchronized void enable(SpanExporter... exporters) {
        if (openTelemetry.get() != null) return;

        var builder = SdkTracerProvider.builder();
        for (var exporter : exporters) {
            builder.addSpanProcessor(SimpleSpanProcessor.create(exporter));
        }
        var provider = builder.build();

        OpenTelemetry telemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(provider)
            .build();
        openTelemetry.set(telemetry);
        tracer.set(telemetry.getTracer(INSTRUMENTATION_NAME));
        log.info("Tracing enabled with {} exporter(s)", exporters.length);
    }

    /** Auto-enable from NOOA_TRACE_DIR or OTLP endpoint env vars. */
    public static synchronized void autoEnable() {
        if (openTelemetry.get() != null) return;
        String traceDir = System.getenv("NOOA_TRACE_DIR");
        String otlpEndpoint = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT");

        if (traceDir != null && !traceDir.isBlank()) {
            enable(jsonl(Path.of(traceDir)));
        } else if (otlpEndpoint != null && !otlpEndpoint.isBlank()) {
            enable(OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpEndpoint)
                .build());
        }
    }

    /** Create a JSONL file exporter writing to a directory. */
    public static SpanExporter jsonl(Path directory) {
        return new JsonlSpanExporter(directory);
    }

    /** Return the configured tracer, lazily enabling tracing or a no-op fallback. */
    public static synchronized Tracer tracer() {
        if (tracer.get() == null) {
            autoEnable();
            if (tracer.get() == null) {
                tracer.set(OpenTelemetry.noop().getTracer(INSTRUMENTATION_NAME));
            }
        }
        return tracer.get();
    }

    /** Return whether the SDK has been initialized with an active tracing provider. */
    public static boolean isEnabled() { return openTelemetry.get() != null; }

    /** Shut down the global tracing provider and reset lazy initialization state. */
    public static synchronized void shutdown() {
        OpenTelemetry telemetry = openTelemetry.get();
        if (telemetry instanceof OpenTelemetrySdk sdk) {
            sdk.getSdkTracerProvider().shutdown();
        }
        openTelemetry.set(null);
        tracer.set(null);
    }

    // ---- Span helpers for ActorRuntime ----

    public static Span startAgentSpan(String agentName, String methodName) {
        return tracer().spanBuilder("AGENT " + agentName + "." + methodName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("agent.name", agentName)
            .setAttribute("agent.method", methodName)
            .startSpan();
    }

    public static Span startLLMSpan(String model) {
        return tracer().spanBuilder("LLM generate")
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute("llm.model", model)
            .startSpan();
    }

    public static Span startCodeExecutionSpan() {
        return tracer().spanBuilder("CODE execute")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();
    }

    // ---- JSONL file exporter ----

    private static class JsonlSpanExporter implements SpanExporter {
        private final Path directory;

        JsonlSpanExporter(Path directory) {
            this.directory = directory;
            try { Files.createDirectories(directory); } catch (Exception e) {
                throw new RuntimeException("Cannot create trace dir: " + directory, e);
            }
        }

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            for (var span : spans) {
                try {
                    var json = formatSpan(span);
                    var file = directory.resolve("traces.jsonl");
                    Files.writeString(file, json + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (Exception e) {
                    log.debug("Failed to write trace span", e);
                }
            }
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        private String formatSpan(io.opentelemetry.sdk.trace.data.SpanData span) {
            var sb = new StringBuilder();
            sb.append("{\"name\":\"").append(escape(span.getName())).append("\"");
            sb.append(",\"traceId\":\"").append(span.getTraceId()).append("\"");
            sb.append(",\"spanId\":\"").append(span.getSpanId()).append("\"");
            if (!span.getParentSpanId().isEmpty() && !span.getParentSpanId().equals("0000000000000000")) {
                sb.append(",\"parentSpanId\":\"").append(span.getParentSpanId()).append("\"");
            }
            sb.append(",\"kind\":\"").append(span.getKind().name()).append("\"");
            sb.append(",\"startTime\":\"").append(
                Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(span.getStartEpochNanos()))).append("\"");
            sb.append(",\"endTime\":\"").append(
                Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(span.getEndEpochNanos()))).append("\"");
            sb.append(",\"status\":\"").append(span.getStatus().getStatusCode().name()).append("\"");
            sb.append(",\"attributes\":{");
            var first = true;
            for (var entry : span.getAttributes().asMap().entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escape(entry.getKey().getKey())).append("\":\"")
                  .append(escape(String.valueOf(entry.getValue()))).append("\"");
                first = false;
            }
            sb.append("}}");
            return sb.toString();
        }

        private String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        }
    }
}
