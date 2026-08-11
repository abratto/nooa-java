package ai.nooa;

import ai.nooa.llm.FakeLLMClient;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AgentFactory")
class AgentFactoryTest {

    @Test
    @DisplayName("create produces a non-null instance")
    void createProducesInstance() {
        var llm = new FakeLLMClient();
        var agent = AgentFactory.create(TestGenerateAgent.class, llm);
        assertThat(agent).isNotNull();
        assertThat(agent).isInstanceOf(TestGenerateAgent.class);
        agent.close();
    }

    @Test
    @DisplayName("created instance has runtime, events, context wired")
    void instanceHasRuntimeWired() {
        var llm = new FakeLLMClient();
        var agent = AgentFactory.create(TestGenerateAgent.class, llm);
        assertThat(agent.runtime()).isNotNull();
        assertThat(agent.eventManager()).isNotNull();
        assertThat(agent.contextManager()).isNotNull();
        agent.close();
    }

    @Test
    @DisplayName("created instance is a ByteBuddy subclass")
    void instanceIsSubclass() {
        var llm = new FakeLLMClient();
        var agent = AgentFactory.create(TestGenerateAgent.class, llm);
        assertThat(agent.getClass().getSimpleName()).contains("$Nooa");
        agent.close();
    }

    @Test
    @DisplayName("repeated calls return same instrumented class")
    void cachesInstrumentation() {
        var llm = new FakeLLMClient();
        var a1 = AgentFactory.create(TestGenerateAgent.class, llm);
        var a2 = AgentFactory.create(TestGenerateAgent.class, llm);
        assertThat(a1.getClass()).isSameAs(a2.getClass());
        a1.close(); a2.close();
    }

    @Test
    @DisplayName("rejects non-Agent classes")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void rejectsNonAgent() {
        var llm = new FakeLLMClient();
        assertThatThrownBy(() -> AgentFactory.create((Class) String.class, llm))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must extend Agent");
    }

    @Test
    @DisplayName("rejects abstract classes")
    void rejectsAbstract() {
        var llm = new FakeLLMClient();
        assertThatThrownBy(() -> AgentFactory.create(Agent.class, llm))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be abstract");
    }

    @Test
    @DisplayName("deterministic helpers work on instrumented instance")
    void helpersWork() {
        var llm = new FakeLLMClient();
        var agent = AgentFactory.create(TestDeterministicAgent.class, llm);
        assertThat(agent.helper("test")).isEqualTo("helped: test");
        agent.close();
    }
}
