package ai.nooa;

import ai.nooa.context.Event;
import ai.nooa.llm.FakeLLMClient;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Integration: Agent + Runtime + Strategy")
class IntegrationTest {

    @Test
    @DisplayName("shared instrumented bytecode across instances")
    void sharedInstrumentation() {
        var llm1 = new FakeLLMClient();
        var llm2 = new FakeLLMClient();
        var a1 = AgentFactory.create(TestGenerateAgent.class, llm1);
        var a2 = AgentFactory.create(TestGenerateAgent.class, llm2);
        assertThat(a1.getClass()).isSameAs(a2.getClass());
        a1.close(); a2.close();
    }

    @Test
    @DisplayName("agents are independent instances")
    void independentInstances() {
        var llm = new FakeLLMClient();
        var a1 = AgentFactory.create(TestGenerateAgent.class, llm);
        var a2 = AgentFactory.create(TestGenerateAgent.class, llm);
        assertThat(a1).isNotSameAs(a2);
        assertThat(a1.agentId()).isNotEqualTo(a2.agentId());
        a1.close(); a2.close();
    }

    @Test
    @DisplayName("context blocks persist")
    void contextBlocksPersist() {
        var llm = new FakeLLMClient();
        var agent = AgentFactory.create(TestGenerateAgent.class, llm);
        agent.context().put("session", "test-123");
        assertThat(agent.contextManager().allBlocks()).containsKey("session");
        agent.close();
    }

    @Test
    @DisplayName("events accumulate")
    void eventsAccumulate() {
        var llm = new FakeLLMClient();
        var agent = AgentFactory.create(TestGenerateAgent.class, llm);
        agent.eventManager().add(new Event.Task("task one"));
        assertThat(agent.eventManager().size()).isEqualTo(1);
        agent.eventManager().add(new Event.LLMOutput("response"));
        assertThat(agent.eventManager().size()).isEqualTo(2);
        agent.close();
    }

    @Test
    @DisplayName("close cleans up")
    void closeCleansUp() {
        var llm = new FakeLLMClient();
        var agent = AgentFactory.create(TestGenerateAgent.class, llm);
        assertThatCode(agent::close).doesNotThrowAnyException();
        assertThatCode(agent::close).doesNotThrowAnyException();
    }
}
