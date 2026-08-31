package ai.nooa.observability;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.context.Event;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.Message;
import ai.nooa.llm.UnifiedLLM;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Prompt Recorder")
class PromptRecorderTest {

    static class TestAgent extends Agent {
        public TestAgent(UnifiedLLM llm) { super(llm); }
        @Generate public String generate(String x) { throw new UnsupportedOperationException(); }
    }

    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("nooa-prompt-recorder-test");
    }

    @AfterEach
    void tearDown() throws Exception {
        try (var files = Files.walk(tempDir)) {
            files.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        }
    }

    @Test
    @DisplayName("writes one JSON line per PromptBuilt event")
    void writesOneJsonLinePerPrompt() throws Exception {
        var agent = new TestAgent(new FakeLLMClient());
        var file = tempDir.resolve("prompts.jsonl");
        var recorder = PromptRecorder.attach(agent, file);

        agent.eventManager().add(new Event.PromptBuilt(
            "fake-model",
            List.of(Message.system("sys"), Message.user("hello")),
            List.of("executeJava", "returnResult"),
            "java.lang.String",
            Map.of("temperature", 0.2),
            true));

        assertThat(recorder.count()).isEqualTo(1);
        assertThat(Files.exists(file)).isTrue();

        var lines = Files.readAllLines(file);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0))
            .contains("\"model\":\"fake-model\"")
            .contains("\"redacted\":true")
            .contains("\"tool_names\"")
            .contains("executeJava")
            .contains("returnResult")
            .contains("\"messages\"")
            .contains("hello")
            .contains("\"output_model\":\"java.lang.String\"");
    }

    @Test
    @DisplayName("ignores non-PromptBuilt events")
    void ignoresNonPromptEvents() throws Exception {
        var agent = new TestAgent(new FakeLLMClient());
        var file = tempDir.resolve("prompts.jsonl");
        var recorder = PromptRecorder.attach(agent, file);

        agent.eventManager().add(new Event.Task("task"));
        agent.eventManager().add(new Event.LLMOutput("response"));
        agent.eventManager().add(new Event.LLMCallStart("fake-model"));

        assertThat(recorder.count()).isZero();
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    @DisplayName("appends multiple events as separate lines")
    void appendsMultipleEvents() throws Exception {
        var agent = new TestAgent(new FakeLLMClient());
        var file = tempDir.resolve("prompts.jsonl");
        var recorder = PromptRecorder.attach(agent, file);

        agent.eventManager().add(new Event.PromptBuilt(
            "m1", List.of(Message.user("one")), List.of(), null, Map.of(), true));
        agent.eventManager().add(new Event.PromptBuilt(
            "m2", List.of(Message.user("two")), List.of(), null, Map.of(), true));

        var lines = Files.readAllLines(file);
        assertThat(lines).hasSize(2);
        assertThat(recorder.count()).isEqualTo(2);
        assertThat(lines.get(0)).contains("\"model\":\"m1\"");
        assertThat(lines.get(1)).contains("\"model\":\"m2\"");
    }

    @Test
    @DisplayName("exposes recorded events for in-process inspection")
    void exposesRecordedEvents() {
        var agent = new TestAgent(new FakeLLMClient());
        var recorder = PromptRecorder.attach(agent, tempDir.resolve("prompts.jsonl"));

        agent.eventManager().add(new Event.PromptBuilt(
            "m", List.of(Message.user("hi")), List.of("t"), null, Map.of(), false));

        assertThat(recorder.recorded()).hasSize(1);
        assertThat(recorder.recorded().get(0).modelName()).isEqualTo("m");
        assertThat(recorder.recorded().get(0).redacted()).isFalse();
    }
}
