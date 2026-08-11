package ai.nooa.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Tracing")
class TracingTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("nooa-tracing-test");
    }

    @AfterEach
    void tearDown() throws Exception {
        Tracing.shutdown();
        try (var files = Files.walk(tempDir)) {
            files.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        }
    }

    @Test
    @DisplayName("enable with JSONL exporter")
    void enableJsonl() {
        Tracing.enable(Tracing.jsonl(tempDir));
        assertThat(Tracing.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("startAgentSpan creates span with attributes")
    void startAgentSpan() {
        Tracing.enable(Tracing.jsonl(tempDir));
        var span = Tracing.startAgentSpan("TestAgent", "analyze");
        assertThat(span).isNotNull();
        span.setStatus(StatusCode.OK);
        span.end();
    }

    @Test
    @DisplayName("startLLMSpan creates span with model attribute")
    void startLLMSpan() {
        Tracing.enable(Tracing.jsonl(tempDir));
        var span = Tracing.startLLMSpan("gpt-4o");
        assertThat(span).isNotNull();
        span.end();
    }

    @Test
    @DisplayName("startCodeExecutionSpan creates span")
    void startCodeSpan() {
        Tracing.enable(Tracing.jsonl(tempDir));
        var span = Tracing.startCodeExecutionSpan();
        assertThat(span).isNotNull();
        span.end();
    }

    @Test
    @DisplayName("trace data is written to JSONL file")
    void traceWrittenToFile() throws Exception {
        Tracing.enable(Tracing.jsonl(tempDir));
        var span = Tracing.startAgentSpan("Test", "method");
        span.setStatus(StatusCode.OK);
        span.end();
        Tracing.shutdown();

        try (var lines = Files.lines(tempDir.resolve("traces.jsonl"))) {
            List<String> allLines = lines.toList();
            assertThat(allLines).hasSize(1);
            assertThat(allLines.get(0)).contains("Test.method")
                .contains("traceId").contains("spanId").contains("OK");
        }
    }

    @Test
    @DisplayName("tracer returns noop tracer when not enabled")
    void noopWhenDisabled() {
        Tracing.shutdown();
        var span = Tracing.startAgentSpan("test", "method");
        assertThat(span).isNotNull();
        span.end();
    }
}
