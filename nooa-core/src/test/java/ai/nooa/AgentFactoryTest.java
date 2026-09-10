package ai.nooa;

import ai.nooa.llm.FakeLLMClient;
import ai.nooa.config.AgentConfig;
import ai.nooa.runtime.CallMiddleware;
import ai.nooa.strategy.CurrentCall;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AgentFactory")
class AgentFactoryTest {

    public static class MiddlewareAgent extends Agent {
        public MiddlewareAgent(ai.nooa.llm.UnifiedLLM llm, AgentConfig config) { super(llm, config); }
        @ai.nooa.annotations.Generate
        public String generate(String input) { throw new UnsupportedOperationException(); }
    }

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

    @Test
    @DisplayName("middleware wraps generated calls")
    void middlewareWrapsGeneratedCalls() {
        var before = new AtomicInteger();
        var after = new AtomicInteger();
        var hook = new CallMiddleware() {
            @Override public void before(Agent agent, CurrentCall call) { before.incrementAndGet(); }
            @Override public Object after(Agent agent, CurrentCall call, Object result) {
                after.incrementAndGet();
                return result;
            }
        };
        var config = AgentConfig.defaults()
            .withDefaultStrategy((runtime, call) -> "done")
            .withMiddleware(hook);
        var agent = AgentFactory.create(MiddlewareAgent.class, new FakeLLMClient(), config);
        assertThat(agent.generate("input")).isEqualTo("done");
        agent.close();
        assertThat(before).hasValue(1);
        assertThat(after).hasValue(1);
    }
}
