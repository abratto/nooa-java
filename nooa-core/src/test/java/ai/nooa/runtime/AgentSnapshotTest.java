package ai.nooa.runtime;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.context.Event;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.UnifiedLLM;
import org.junit.jupiter.api.*;

import java.nio.file.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AgentSnapshot")
class AgentSnapshotTest {

    static class TestAgent extends Agent {
        public String customField = "hello";
        public TestAgent(UnifiedLLM llm) { super(llm); }
        @Generate public String generate(String x) { throw new UnsupportedOperationException(); }
    }

    private Path tempFile;

    @BeforeEach
    void setUp() throws Exception {
        tempFile = Files.createTempFile("snapshot", ".json");
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(tempFile);
    }

    @Test
    @DisplayName("take captures events and context blocks")
    void takeCapturesAll() {
        var llm = new FakeLLMClient();
        var agent = new TestAgent(llm);
        agent.eventManager().add(new Event.Task("hello"));
        agent.context().put("focus", "testing");

        var snap = AgentSnapshot.take(agent);
        assertThat(snap.events()).hasSize(1);
        assertThat(snap.events().getFirst().get("content")).isEqualTo("hello");
        assertThat(snap.contextBlocks()).containsEntry("focus", "testing");
        assertThat(snap.model()).isEqualTo("fake-model");
        agent.close();
    }

    @Test
    @DisplayName("save and load round-trips")
    void saveAndLoadRoundTrip() {
        var llm = new FakeLLMClient();
        var agent = new TestAgent(llm);
        agent.eventManager().add(new Event.Task("test"));
        agent.eventManager().add(new Event.LLMOutput("result"));

        var snap = AgentSnapshot.take(agent);
        AgentSnapshot.save(snap, tempFile);

        var loaded = AgentSnapshot.load(tempFile);
        assertThat(loaded.events()).hasSize(2);
        assertThat(loaded.agentId()).isEqualTo(agent.agentId());
        agent.close();
    }

    @Test
    @DisplayName("restoreEvents repopulates event manager")
    void restoreEvents() {
        var llm = new FakeLLMClient();
        var agent = new TestAgent(llm);
        agent.eventManager().add(new Event.Task("original"));

        var snap = AgentSnapshot.take(agent);
        agent.eventManager().clear();
        AgentSnapshot.restoreEvents(agent, snap);

        assertThat(agent.eventManager().size()).isEqualTo(1);
        assertThat(agent.eventManager().all().get(0)).isInstanceOf(Event.Task.class);
        agent.close();
    }

    @Test
    @DisplayName("restoreContext repopulates context blocks")
    void restoreContext() {
        var llm = new FakeLLMClient();
        var agent = new TestAgent(llm);
        agent.context().put("session", "123");

        var snap = AgentSnapshot.take(agent);
        agent.contextManager().remove("session");
        AgentSnapshot.restoreContext(agent, snap);

        assertThat(agent.contextManager().allBlocks()).containsKey("session");
        agent.close();
    }
}
