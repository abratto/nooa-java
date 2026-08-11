package ai.nooa.strategy;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.LLMResponse;
import ai.nooa.llm.UnifiedLLM;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CodeActStrategy")
class CodeActStrategyTest {

    static class TestAgent extends Agent {
        public TestAgent(UnifiedLLM llm) { super(llm); }
        @Generate public String generate(String x) { throw new UnsupportedOperationException(); }
    }

    private FakeLLMClient llm;
    private TestAgent agent;
    private CodeActStrategy strategy;

    @BeforeEach
    void setUp() {
        llm = new FakeLLMClient();
        agent = new TestAgent(llm);
        strategy = new CodeActStrategy(ai.nooa.config.CodeActConfig.defaults());
    }

    @AfterEach
    void tearDown() { agent.close(); }

    @Test
    @DisplayName("strategy name is CodeActStrategy")
    void strategyName() {
        assertThat(strategy.name()).isEqualTo("CodeActStrategy");
    }

    @Test
    @DisplayName("exposes executeJava and returnResult tools")
    void exposesStandardTools() {
        assertThat(CodeActStrategy.EXECUTE_JAVA_TOOL.name()).isEqualTo("executeJava");
        assertThat(CodeActStrategy.RETURN_RESULT_TOOL.name()).isEqualTo("returnResult");
    }

    @Test
    @DisplayName("returns result from returnResult tool call")
    void returnsFromReturnResult() throws Exception {
        llm.respondWith(List.of(
            new LLMResponse.ToolCall("call_1", "returnResult", Map.of("value", "final"))));
        var call = CurrentCall.fromMethod(
            TestAgent.class.getDeclaredMethod("generate", String.class),
            new Object[]{"test"});
        var result = strategy.execute(agent.runtime(), call);
        assertThat(result).isNotNull();
    }
}
