package ai.nooa.strategy;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.context.Event;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.LLMResponse;
import ai.nooa.llm.UnifiedLLM;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ReflexionStrategy")
class ReflexionStrategyTest {

    static class TestAgent extends Agent {
        public TestAgent(UnifiedLLM llm) { super(llm); }
        @Generate public String generate(String x) { throw new UnsupportedOperationException(); }
    }

    @Test
    @DisplayName("reflexion wraps base strategy")
    void wrapsBaseStrategy() {
        var base = new CodeActStrategy(ai.nooa.config.CodeActConfig.defaults());
        var reflexion = new ReflexionStrategy(base, 2);
        assertThat(reflexion.name()).isEqualTo("ReflexionStrategy");
    }

    @Test
    @DisplayName("reflexion passes through successful first result")
    void passesThroughSuccessfulResult() throws Exception {
        var llm = new FakeLLMClient();
        var agent = new TestAgent(llm);

        // Base strategy: returnResult
        llm.respondWith(List.of(
            new LLMResponse.ToolCall("c1", "returnResult", Map.of("value", "good"))));

        var reflexion = new ReflexionStrategy(
            new CodeActStrategy(ai.nooa.config.CodeActConfig.defaults()), 2);

        var call = CurrentCall.fromMethod(
            TestAgent.class.getDeclaredMethod("generate", String.class),
            new Object[]{"test"});

        // Reflection call will also try to generate — need another response for critique
        llm.respondWith("OK"); // critique says OK → stops

        var result = reflexion.execute(agent.runtime(), call);
        assertThat(result).isNotNull();
    }
}
