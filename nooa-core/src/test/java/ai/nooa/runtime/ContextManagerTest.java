package ai.nooa.runtime;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.UnifiedLLM;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

class ContextManagerTest {

    static class TestAgent extends Agent {
        public TestAgent(UnifiedLLM llm) { super(llm); }
        @Generate public String generate(String x) { throw new UnsupportedOperationException(); }
    }

    @Test
    @DisplayName("Protected blocks exist after construction")
    void protectedBlocksExist() {
        var llm = new FakeLLMClient();
        var agent = new TestAgent(llm);
        assertThat(agent.contextManager().allBlocks())
            .containsKeys("system_prompt", "self", "state");
        agent.close();
    }

    @Test
    @DisplayName("Cannot override protected blocks")
    void cannotOverrideProtected() {
        var llm = new FakeLLMClient();
        var agent = new TestAgent(llm);
        assertThatThrownBy(() -> agent.contextManager().put("system_prompt", "x"))
            .isInstanceOf(IllegalArgumentException.class);
        agent.close();
    }

    @Test
    @DisplayName("Can add and remove user blocks")
    void userBlocksAddRemove() {
        var llm = new FakeLLMClient();
        var agent = new TestAgent(llm);
        agent.contextManager().put("focus", "security");
        assertThat(agent.contextManager().allBlocks()).containsKey("focus");
        agent.contextManager().remove("focus");
        assertThat(agent.contextManager().allBlocks()).doesNotContainKey("focus");
        agent.close();
    }

    @Test
    @DisplayName("context API delegates to context manager")
    void contextApiDelegates() {
        var llm = new FakeLLMClient();
        var agent = new TestAgent(llm);
        agent.context().put("key1", "value1");
        assertThat(agent.contextManager().allBlocks()).containsKey("key1");
        agent.close();
    }

    @Test
    @DisplayName("Dynamic blocks render their current expression value")
    void dynamicBlocksRenderCurrentValue() {
        var agent = new TestAgent(new FakeLLMClient());
        agent.context().putDynamic("agent_id", "self.agentId()");

        assertThat(agent.contextManager().render(agent))
            .contains("<agent_id>\n" + agent.agentId() + "\n</agent_id>");
        agent.close();
    }

    @Test
    @DisplayName("Cannot remove protected blocks")
    void cannotRemoveProtectedBlocks() {
        var agent = new TestAgent(new FakeLLMClient());

        assertThatThrownBy(() -> agent.context().remove("state"))
            .isInstanceOf(IllegalArgumentException.class);
        agent.close();
    }
}
