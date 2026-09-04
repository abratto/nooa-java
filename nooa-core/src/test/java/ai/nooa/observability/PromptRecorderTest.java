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

    @Test
    @DisplayName("enriches every line with static run metadata")
    void enrichesLinesWithMetadata() throws Exception {
        var agent = new TestAgent(new FakeLLMClient());
        var file = tempDir.resolve("prompts.jsonl");

        try (var recorder = PromptRecorder.attach(agent, file, event ->
            Map.of("run_id", "run-1", "kind", "demo", "attempt", 2))) {
            agent.eventManager().add(new Event.PromptBuilt(
                "m", List.of(Message.user("one")), List.of(), null, Map.of(), true));
            agent.eventManager().add(new Event.PromptBuilt(
                "m", List.of(Message.user("two")), List.of(), null, Map.of(), true));

            var lines = Files.readAllLines(file);
            assertThat(lines).hasSize(2);
            assertThat(recorder.parseLine(lines.get(0)).get("run_id").asText()).isEqualTo("run-1");
            assertThat(recorder.parseLine(lines.get(1)).get("attempt").asInt()).isEqualTo(2);
            assertThat(recorder.count()).isEqualTo(2);
        }

        // Detached by close(): no further recording.
        agent.eventManager().add(new Event.PromptBuilt(
            "m", List.of(Message.user("late")), List.of(), null, Map.of(), true));
        assertThat(Files.readAllLines(file)).hasSize(2);
    }

    @Test
    @DisplayName("metadata provider can change per event")
    void providerCanChangePerEvent() throws Exception {
        var agent = new TestAgent(new FakeLLMClient());
        var file = tempDir.resolve("prompts.jsonl");
        var current = new Object() { String stage = "stage-1"; };

        var recorder = PromptRecorder.attach(agent, file, event -> Map.of("stage", current.stage));
        agent.eventManager().add(new Event.PromptBuilt(
            "m", List.of(Message.user("one")), List.of(), null, Map.of(), true));
        current.stage = "stage-2";
        agent.eventManager().add(new Event.PromptBuilt(
            "m", List.of(Message.user("two")), List.of(), null, Map.of(), true));

        var lines = Files.readAllLines(file);
        assertThat(recorder.parseLine(lines.get(0)).get("stage").asText()).isEqualTo("stage-1");
        assertThat(recorder.parseLine(lines.get(1)).get("stage").asText()).isEqualTo("stage-2");
    }

    @Test
    @DisplayName("every written line is exactly one valid JSON object")
    void linesAreValidJsonObjects() throws Exception {
        var agent = new TestAgent(new FakeLLMClient());
        var file = tempDir.resolve("prompts.jsonl");
        var recorder = PromptRecorder.attach(agent, file, event -> Map.of("x", "has \"quotes\" and\nnewline"));

        agent.eventManager().add(new Event.PromptBuilt(
            "m", List.of(Message.system("sys with\nnewline"), Message.user("hi \"there\"")),
            List.of("toolA"), null, Map.of("temperature", 0.2), true));

        for (String line : Files.readAllLines(file)) {
            var node = recorder.parseLine(line);
            assertThat(node.isObject()).isTrue();
            assertThat(node.has("event_id")).isTrue();
            assertThat(node.has("timestamp")).isTrue();
            assertThat(node.has("model")).isTrue();
            assertThat(node.has("redacted")).isTrue();
            assertThat(node.has("message_count")).isTrue();
            assertThat(node.get("messages").isArray()).isTrue();
            assertThat(node.get("messages").get(1).get("content").asText()).isEqualTo("hi \"there\"");
        }
    }

    @Test
    @DisplayName("concurrent PromptBuilt events keep JSONL consistent")
    void concurrentEventsRemainConsistent() throws Exception {
        var agent = new TestAgent(new FakeLLMClient());
        var file = tempDir.resolve("prompts.jsonl");
        var recorder = PromptRecorder.attach(agent, file);

        int threads = 4;
        int perThread = 10;
        var executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                for (int i = 0; i < perThread; i++) {
                    agent.eventManager().add(new Event.PromptBuilt(
                        "m", List.of(Message.user("msg")), List.of(), null, Map.of(), true));
                }
            });
        }
        executor.shutdown();
        while (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) { /* await */ }

        assertThat(recorder.count()).isEqualTo(threads * perThread);
        var lines = Files.readAllLines(file);
        assertThat(lines.size()).isEqualTo(threads * perThread);
        for (String line : lines) {
            assertThat(recorder.parseLine(line).isObject()).isTrue();
        }
    }

    @Test
    @DisplayName("a failing write target never breaks the agent loop")
    void writeFailureNeverBreaksRecording() throws Exception {
        var agent = new TestAgent(new FakeLLMClient());
        var file = tempDir.resolve("prompts.jsonl");
        Files.createFile(file);
        var recorder = PromptRecorder.attach(agent, file);

        agent.eventManager().add(new Event.PromptBuilt(
            "m", List.of(Message.user("one")), List.of(), null, Map.of(), true));

        // Make every subsequent append fail by swapping the file for a directory.
        Files.deleteIfExists(file);
        Files.createDirectories(file);

        agent.eventManager().add(new Event.PromptBuilt(
            "m", List.of(Message.user("two")), List.of(), null, Map.of(), true));
        agent.eventManager().add(new Event.PromptBuilt(
            "m", List.of(Message.user("three")), List.of(), null, Map.of(), true));

        assertThat(recorder.count()).isEqualTo(3);
        assertThat(recorder.writeFailures()).isEqualTo(2);
        assertThat(recorder.recorded().size()).isEqualTo(3);
    }

    @Test
    @DisplayName("an unusable log path degrades to in-memory recording")
    void unusablePathDegradesToMemory() {
        var agent = new TestAgent(new FakeLLMClient());
        var dir = tempDir.resolve("existing-directory");
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (java.io.IOException ignored) { }

        var recorder = PromptRecorder.attach(agent, dir);

        agent.eventManager().add(new Event.PromptBuilt(
            "m", List.of(Message.user("one")), List.of(), null, Map.of(), true));

        assertThat(recorder.count()).isEqualTo(1);
        assertThat(recorder.recorded().size()).isEqualTo(1);
    }
}
