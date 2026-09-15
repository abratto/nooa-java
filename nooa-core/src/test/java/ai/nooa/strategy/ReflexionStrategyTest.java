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
        try (var agent = new TestAgent(llm)) {

            // Base strategy: returnResult
            llm.respondWith(List.of(
                new LLMResponse.ToolCall("c1", "returnResult", Map.of("value", "good"))));

            var reflexion = new ReflexionStrategy(
                new CodeActStrategy(ai.nooa.config.CodeActConfig.defaults()), 2);

            var call = CurrentCall.fromMethod(
                TestAgent.class.getDeclaredMethod("generate", String.class),
                new Object[]{"test"});

            // Reflection call will also try to generate — need another response for critique
            llm.respondWith("OK"); // critique says OK -> stops

            var result = reflexion.execute(agent.runtime(), call);
            assertThat(result).isNotNull();
        }
    }

    @Test
    @DisplayName("reflection prompt includes the result and evaluation instruction")
    void reflectionPromptIncludesResult() throws Exception {
        var llm = new FakeLLMClient();
        try (var agent = new TestAgent(llm)) {
            llm.respondWith(List.of(
                new LLMResponse.ToolCall("c1", "returnResult", Map.of("value", "good"))));
            llm.respondWith("{\"satisfactory\":true,\"reasoning\":\"valid\",\"issues\":[],\"suggestions\":[]}");

            var reflexion = new ReflexionStrategy(
                new CodeActStrategy(ai.nooa.config.CodeActConfig.defaults()), 2);
            var call = CurrentCall.fromMethod(
                TestAgent.class.getDeclaredMethod("generate", String.class),
                new Object[]{"test"});

            reflexion.execute(agent.runtime(), call);

            assertThat(llm.lastMessages()).anyMatch(message ->
                message.role().equals("system")
                    && message.content().contains("Result to evaluate:")
                    && message.content().contains("good")
                    && message.content().contains("Return JSON with fields satisfactory"));
        }
    }

    @Test
    @DisplayName("structured critique is included in the next attempt")
    void structuredCritiqueFeedsNextAttempt() throws Exception {
        var llm = new FakeLLMClient();
        try (var agent = new TestAgent(llm)) {
            llm.respondWith(List.of(
                new LLMResponse.ToolCall("c1", "returnResult", Map.of("value", "first"))));
            llm.respondWith("{\"satisfactory\":false,\"reasoning\":\"needs work\","
                + "\"issues\":[\"missing detail\"],\"suggestions\":[\"add detail\"]}");
            llm.respondWith(List.of(
                new LLMResponse.ToolCall("c2", "returnResult", Map.of("value", "second"))));

            var reflexion = new ReflexionStrategy(
                new CodeActStrategy(ai.nooa.config.CodeActConfig.defaults()), 2);
            var call = CurrentCall.fromMethod(
                TestAgent.class.getDeclaredMethod("generate", String.class),
                new Object[]{"test"});

            assertThat(reflexion.execute(agent.runtime(), call)).isEqualTo("second");
            assertThat(agent.runtime().eventManager().all()).anyMatch(event ->
                event instanceof Event.Feedback feedback
                    && feedback.content().contains("needs work")
                    && feedback.content().contains("missing detail")
                    && feedback.content().contains("add detail"));
        }
    }
}
