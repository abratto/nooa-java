package ai.nooa.atif;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.context.Event;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.UnifiedLLM;
import org.junit.jupiter.api.*;

import java.nio.file.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ATIF Exporter")
class AtifExporterTest {

    static class TestAgent extends Agent {
        public TestAgent(UnifiedLLM llm) { super(llm); }
        @Generate public String generate(String x) { throw new UnsupportedOperationException(); }
    }

    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("nooa-atif-test");
    }

    @AfterEach
    void tearDown() throws Exception {
        try (var files = Files.walk(tempDir)) {
            files.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        }
    }

    @Test
    @DisplayName("attaches to agent and captures events")
    void capturesEvents() throws Exception {
        var agent = new TestAgent(new FakeLLMClient());
        var atif = AtifExporter.attach(agent, tempDir);

        agent.eventManager().add(new Event.Task("test task"));
        agent.eventManager().add(new Event.LLMOutput("response"));

        atif.close();

        // Check that a trajectory file was written
        try (var files = Files.list(tempDir)) {
            var jsonFiles = files
                .filter(f -> f.getFileName().toString().endsWith(".json"))
                .toList();
            assertThat(jsonFiles).hasSize(1);

            String content = Files.readString(jsonFiles.get(0));
            assertThat(content).contains("trajectory_id")
                .contains("steps")
                .contains("test task")
                .contains("response");
        }
    }

    @Test
    @DisplayName("flush writes complete trajectory")
    void flushWritesTrajectory() {
        var agent = new TestAgent(new FakeLLMClient());
        var atif = AtifExporter.attach(agent, tempDir);
        atif.flush();

        assertThat(tempDir.toFile().listFiles()).isNotEmpty();
        atif.close();
    }
}
