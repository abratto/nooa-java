package ai.nooa.runtime;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.context.Event;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.Tool;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.strategy.CurrentCall;
import ai.nooa.strategy.GenerationStrategy;
import ai.nooa.strategy.RuntimeServices;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ActorRuntime")
class ActorRuntimeTest {

    static class TestAgent extends Agent {
        public TestAgent(UnifiedLLM llm) { super(llm); }
        @Generate public String generate(String x) { throw new UnsupportedOperationException(); }
    }

    private FakeLLMClient llm;
    private TestAgent agent;

    @BeforeEach
    void setUp() {
        llm = new FakeLLMClient();
        agent = new TestAgent(llm);
    }

    @AfterEach
    void tearDown() { agent.close(); }

    @Test
    @DisplayName("generate calls LLM and returns response")
    void generateCallsLLM() {
        llm.respondWith("response text");
        var response = agent.runtime().generate(List.of(), null, Map.of());
        assertThat(response.content()).isEqualTo("response text");
        assertThat(llm.callCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("generate passes tools to LLM")
    void generatePassesTools() {
        llm.respondWith("ok");
        var tool = Tool.builder().name("t").description("d").parameter("a", "string", "a").build();
        agent.runtime().generate(List.of(tool), null, Map.of());
        assertThat(llm.lastTools()).contains(tool);
    }

    @Test
    @DisplayName("generate includes context blocks")
    void generateIncludesContext() {
        agent.context().put("focus", "security");
        llm.respondWith("ok");
        agent.runtime().generate(List.of(), null, Map.of());
        assertThat(llm.lastMessages().get(0).content()).contains("security");
    }

    @Test
    @DisplayName("callPlan adds Task and executes strategy")
    void callPlanAddsTask() throws Exception {
        var result = new AtomicBoolean(false);
        var strategy = new GenerationStrategy() {
            public Object execute(RuntimeServices rt, CurrentCall call) {
                result.set(true);
                return "done";
            }
        };
        llm.respondWith("ok");
        var call = CurrentCall.fromMethod(
            TestAgent.class.getDeclaredMethod("generate", String.class), new Object[]{"test"});
        var output = agent.runtime().callPlan(strategy, call);
        assertThat(output).isEqualTo("done");
        assertThat(result.get()).isTrue();
    }

    @Test
    @DisplayName("expandVariables resolves expressions")
    void expandVariables() {
        var result = agent.runtime().expandVariables("Agent: {type.name}");
        assertThat(result).contains("TestAgent");
    }

    @Test
    @DisplayName("executeCode runs JShell snippet")
    void executeCodeRunsJShell() {
        var result = agent.runtime().executeCode("int x = 1 + 2;", Map.of());
        assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("executeCode returns errors for bad code")
    void executeCodeReturnsErrors() {
        var result = agent.runtime().executeCode("int x = nonexistent();", Map.of());
        assertThat(result.success()).isFalse();
        assertThat(result.error()).isNotNull();
    }
}
