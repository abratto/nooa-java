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

@DisplayName("CodeActStrategy")
class CodeActStrategyTest {

    static class TestAgent extends Agent {
        public TestAgent(UnifiedLLM llm) { super(llm); }
        @Generate public String generate(String x) { throw new UnsupportedOperationException(); }
    }

    record Result(String text, int count) {}

    static class TypedAgent extends Agent {
        public TypedAgent(UnifiedLLM llm) { super(llm); }
        @Generate public Result generate(String x) { throw new UnsupportedOperationException(); }
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
    @DisplayName("tool descriptions document the agent binding and return contract")
    void toolDescriptionsDocumentProtocol() {
        assertThat(CodeActStrategy.EXECUTE_JAVA_TOOL.description())
            .contains("__agent__")
            .contains("returnResult");
        assertThat(CodeActStrategy.RETURN_RESULT_TOOL.description())
            .contains("final result");
    }

    @Test
    @DisplayName("injects strategy prompt and execution context into the system prompt")
    void injectsStrategyPromptAndExecutionContext() throws Exception {
        llm.respondWith(List.of(
            new LLMResponse.ToolCall("c1", "returnResult", Map.of("value", "done"))));
        var call = CurrentCall.fromMethod(
            TestAgent.class.getDeclaredMethod("generate", String.class),
            new Object[]{"test"});
        strategy.execute(agent.runtime(), call);

        var system = llm.lastMessages().get(0);
        assertThat(system.role()).isEqualTo("system");
        assertThat(system.content())
            .contains("## Strategy")
            .contains("## Execution Context")
            .contains("executeJava")
            .contains("returnResult")
            .contains("__agent__");
    }

    @Test
    @DisplayName("text-only response emits a correction when fallback is disabled")
    void textOnlyEmitsCorrectionWhenFallbackDisabled() throws Exception {
        var strict = new CodeActStrategy(
            CodeActConfig.builder().allowTextFallback(false).build());
        llm.respondWith("Just thinking out loud");
        var call = CurrentCall.fromMethod(
            TestAgent.class.getDeclaredMethod("generate", String.class),
            new Object[]{"test"});
        try {
            strict.execute(agent.runtime(), call);
        } catch (Exception ignored) {}

        assertThat(agent.eventManager().all()).anyMatch(e ->
            e instanceof Event.ErrorEvent err
                && err.message().contains("plain text with no tool call"));
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

    @Test
    @DisplayName("returnResult tool schema reflects the return type")
    void returnResultToolSchemaReflectsReturnType() {
        var tool = CodeActStrategy.returnResultTool(Result.class);

        assertThat(tool.name()).isEqualTo("returnResult");
        assertThat(tool.description()).contains("Result");

        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) tool.inputSchema().get("properties");
        @SuppressWarnings("unchecked")
        var valueSchema = (Map<String, Object>) properties.get("value");
        assertThat(valueSchema).containsKey("properties");
    }

    @Test
    @DisplayName("returnResult converts the value to the declared return type")
    void returnResultConvertsToTypedResult() throws Exception {
        var typedLlm = new FakeLLMClient();
        var typedAgent = new TypedAgent(typedLlm);
        typedLlm.respondWith(List.of(
            new LLMResponse.ToolCall("c1", "returnResult",
                Map.of("value", Map.of("text", "hi", "count", 3)))));

        var call = CurrentCall.fromMethod(
            TypedAgent.class.getDeclaredMethod("generate", String.class),
            new Object[]{"test"});
        var result = strategy.execute(typedAgent.runtime(), call);

        assertThat(result).isInstanceOf(Result.class);
        var typed = (Result) result;
        assertThat(typed.text()).isEqualTo("hi");
        assertThat(typed.count()).isEqualTo(3);
        typedAgent.close();
    }

    @Test
    @DisplayName("inline returnResult inside executeJava terminates the loop")
    void inlineReturnResultTerminates() throws Exception {
        llm.respondWith(List.of(
            new LLMResponse.ToolCall("c1", "executeJava",
                Map.of("code", "returnResult(\"hello from code\");"))));
        var call = CurrentCall.fromMethod(
            TestAgent.class.getDeclaredMethod("generate", String.class),
            new Object[]{"test"});

        var result = strategy.execute(agent.runtime(), call);

        assertThat(result).isEqualTo("hello from code");
    }

    @Test
    @DisplayName("executeJava without returnResult continues the loop")
    void executeJavaWithoutReturnResultContinues() throws Exception {
        llm.respondWith(List.of(
            new LLMResponse.ToolCall("c1", "executeJava", Map.of("code", "int x = 42;"))));
        llm.respondWith(List.of(
            new LLMResponse.ToolCall("c2", "returnResult", Map.of("value", "done"))));
        var call = CurrentCall.fromMethod(
            TestAgent.class.getDeclaredMethod("generate", String.class),
            new Object[]{"test"});

        var result = strategy.execute(agent.runtime(), call);

        assertThat(result).isEqualTo("done");
        assertThat(llm.callCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("generated code can call concrete agent helper methods via __agent__")
    void generatedCodeCallsAgentHelperMethods() throws Exception {
        var helperLlm = new FakeLLMClient();
        var helperAgent = new ai.nooa.TestDeterministicAgent(helperLlm);
        helperLlm.respondWith(List.of(
            new LLMResponse.ToolCall("c1", "executeJava",
                Map.of("code", "returnResult(__agent__.helper(\"world\"));"))));
        var call = CurrentCall.fromMethod(
            ai.nooa.TestDeterministicAgent.class.getDeclaredMethod("helper", String.class),
            new Object[]{"test"});

        var result = strategy.execute(helperAgent.runtime(), call);

        assertThat(result).isEqualTo("helped: world");
        helperAgent.close();
    }

    @Test
    @DisplayName("method arguments are bound as typed REPL variables")
    void methodArgumentsAreBoundAsVariables() throws Exception {
        llm.respondWith(List.of(
            new LLMResponse.ToolCall("c1", "executeJava",
                Map.of("code", "returnResult(x.toUpperCase() + \"!\");"))));
        var call = CurrentCall.fromMethod(
            TestAgent.class.getDeclaredMethod("generate", String.class),
            new Object[]{"hello"});

        var result = strategy.execute(agent.runtime(), call);

        assertThat(result).isEqualTo("HELLO!");
    }

    @Test
    @DisplayName("returns text fallback when the model responds without tool calls")
    void returnsTextFallbackWhenNoToolCallIsIssued() throws Exception {
        llm.respondWith("Hello from a text-only model");
        var strategy = new CodeActStrategy(
            ai.nooa.config.CodeActConfig.builder().allowTextFallback(true).build());
        var call = CurrentCall.fromMethod(
            TestAgent.class.getDeclaredMethod("generate", String.class),
            new Object[]{"test"});

        var result = strategy.execute(agent.runtime(), call);

        assertThat(result).isEqualTo("Hello from a text-only model");
    }

    @Test
    @DisplayName("baseline and argument-aware prompts differ in grounding content")
    void baselineAndArgumentAwarePromptsDifferInGrounding() throws Exception {
        var article = "Acme announced a new battery chemistry that cuts charging time by 40% and reduces heat.";
        var call = CurrentCall.fromMethod(
            TestAgent.class.getDeclaredMethod("generate", String.class),
            new Object[]{article});

        var baseline = call.docstring();
        var enriched = call.userPrompt(true, 200);

        assertThat(baseline).contains("generate");
        assertThat(enriched)
            .contains("Inputs:")
            .contains("x")
            .contains(article.substring(0, 30));
        assertThat(enriched.length()).isGreaterThan(baseline.length());
        assertThat(call.userPrompt(false, 200)).isEqualTo(baseline);
    }
}
