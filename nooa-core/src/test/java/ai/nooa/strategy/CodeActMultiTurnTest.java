package ai.nooa.strategy;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.config.CodeActConfig;
import ai.nooa.context.Event;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.LLMResponse;
import ai.nooa.llm.UnifiedLLM;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CodeActStrategy — Multi-turn")
class CodeActMultiTurnTest {

    static class TestAgent extends Agent {
        public TestAgent(UnifiedLLM llm) { super(llm); }
        @Generate public String calculate(String problem) { throw new UnsupportedOperationException(); }
    }

    private FakeLLMClient llm;
    private TestAgent agent;
    private CodeActStrategy strategy;

    @BeforeEach
    void setUp() {
        llm = new FakeLLMClient();
        agent = new TestAgent(llm);
        strategy = new CodeActStrategy(CodeActConfig.defaults());
    }

    @AfterEach
    void tearDown() { agent.close(); }

    @Test
    @DisplayName("multi-turn: executeJava stores state, returnResult returns")
    void multiTurnStatePersistence() throws Exception {
        llm.respondWith(List.of(
            new LLMResponse.ToolCall("c1", "executeJava", Map.of("code", "int result = 42;"))));
        llm.respondWith(List.of(
            new LLMResponse.ToolCall("c2", "returnResult", Map.of("value", "done"))));

        var call = CurrentCall.fromMethod(
            TestAgent.class.getDeclaredMethod("calculate", String.class),
            new Object[]{"what is 21+21?"});
        strategy.execute(agent.runtime(), call);

        var outputs = agent.eventManager().all().stream()
            .filter(e -> e instanceof Event.ExecutionOutput).count();
        assertThat(outputs).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("error recovery in multi-turn")
    void errorRecoveryInMultiTurn() throws Exception {
        llm.respondWith(List.of(
            new LLMResponse.ToolCall("c1", "executeJava", Map.of("code", "int x = 1/0;"))));
        llm.respondWith(List.of(
            new LLMResponse.ToolCall("c2", "executeJava", Map.of("code", "int x = 42;"))));
        llm.respondWith(List.of(
            new LLMResponse.ToolCall("c3", "returnResult", Map.of("value", "recovered"))));

        var call = CurrentCall.fromMethod(
            TestAgent.class.getDeclaredMethod("calculate", String.class),
            new Object[]{"test"});
        strategy.execute(agent.runtime(), call);

        assertThat(agent.eventManager().all()).anyMatch(e ->
            e instanceof Event.ExecutionOutput ex && ex.error() != null);
    }

    @Test
    @DisplayName("text-only response accumulates LLMOutput events")
    void textOnlyIncrements() throws Exception {
        llm.respondWith("Let me think about this...");
        var call = CurrentCall.fromMethod(
            TestAgent.class.getDeclaredMethod("calculate", String.class),
            new Object[]{"test"});
        try {
            strategy.execute(agent.runtime(), call);
        } catch (Exception ignored) {}

        assertThat(agent.eventManager().all()).anyMatch(e -> e instanceof Event.LLMOutput);
    }

    @Test
    @DisplayName("strategy respects maxIterations config")
    void respectsMaxIterations() throws Exception {
        var limitedStrategy = new CodeActStrategy(CodeActConfig.builder().maxIterations(2).build());
        llm.respondWith(List.of(
            new LLMResponse.ToolCall("c1", "executeJava", Map.of("code", "int x = 1;"))));
        llm.respondWith(List.of(
            new LLMResponse.ToolCall("c2", "executeJava", Map.of("code", "int y = 2;"))));
        llm.respondWith(List.of(
            new LLMResponse.ToolCall("c3", "returnResult", Map.of("value", "done"))));

        var call = CurrentCall.fromMethod(
            TestAgent.class.getDeclaredMethod("calculate", String.class),
            new Object[]{"test"});

        assertThatThrownBy(() -> limitedStrategy.execute(agent.runtime(), call))
            .isInstanceOf(ai.nooa.GenerationError.class)
            .hasMessageContaining("Max iterations");
    }
}
