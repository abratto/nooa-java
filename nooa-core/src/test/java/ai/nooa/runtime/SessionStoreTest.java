package ai.nooa.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.UnifiedLLM;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SessionStoreTest {
    public static class TestAgent extends Agent {
        public TestAgent(UnifiedLLM llm) { super(llm); }
    }

    @Test
    void savesAndRestoresEventsAndContext() throws Exception {
        var agent = AgentFactory.create(TestAgent.class, new FakeLLMClient());
        agent.context().put("user", "Ada");
        agent.eventManager().add(new ai.nooa.context.Event.Task("hello"));
        var store = new SessionStore(Files.createTempDirectory("nooa-session"));
        store.save("demo", agent);
        agent.eventManager().clear();
        agent.context().remove("user");
        store.restore("demo", agent);
        assertThat(agent.eventManager().all()).hasSize(1);
        assertThat(agent.contextManager().allBlocks()).containsKey("user");
        agent.close();
    }

    @Test
    void preservesStructuredContextValuesAcrossSnapshotRoundTrip() throws Exception {
        var agent = AgentFactory.create(TestAgent.class, new FakeLLMClient());
        agent.context().put("payload", Map.of("kind", "input", "count", 2));
        var store = new SessionStore(Files.createTempDirectory("nooa-structured-session"));
        store.save("structured", agent);

        agent.context().remove("payload");
        store.restore("structured", agent);

        assertThat(agent.contextManager().allBlocks().get("payload"))
            .isInstanceOf(ai.nooa.context.ContextBlock.Structured.class);
        var block = (ai.nooa.context.ContextBlock.Structured)
            agent.contextManager().allBlocks().get("payload");
        assertThat(block.value()).isEqualTo(Map.of("kind", "input", "count", 2));
        agent.close();
    }

    @Test
    void appendsEventsWithoutDroppingExistingSnapshotState() throws Exception {
        var agent = AgentFactory.create(TestAgent.class, new FakeLLMClient());
        agent.context().put("user", "Ada");
        var store = new SessionStore(Files.createTempDirectory("nooa-append-session"));
        store.save("append", agent);
        store.appendEvent("append", new ai.nooa.context.Event.Task("follow-up"));

        var snapshot = store.load("append");
        assertThat(snapshot.events()).hasSize(1);
        assertThat(snapshot.contextBlocks()).containsEntry("user", "Ada");
        agent.close();
    }
}