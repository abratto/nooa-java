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
    void tearDown() {
        System.clearProperty("nooa.log.prompts");
        System.clearProperty("nooa.log.prompts.raw");
        agent.close();
    }

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
    @DisplayName("generate emits PromptBuilt event when prompt logging is enabled")
    void generateEmitsPromptBuiltWhenEnabled() {
        System.setProperty("nooa.log.prompts", "true");
        llm.respondWith("ok");

        agent.runtime().generate(List.of(), null, Map.of());

        assertThat(agent.eventManager().all().stream().anyMatch(e -> e instanceof Event.PromptBuilt))
            .isTrue();
    }

    @Test
    @DisplayName("PromptBuilt is redacted by default")
    void promptBuiltRedactsSecretsByDefault() {
        System.setProperty("nooa.log.prompts", "true");
        agent.context().put("credentials", "api_key=abc123 bearer sk-secret");
        llm.respondWith("ok");

        agent.runtime().generate(List.of(), null, Map.of());

        Event.PromptBuilt promptBuilt = (Event.PromptBuilt) agent.eventManager().all().stream()
            .filter(e -> e instanceof Event.PromptBuilt)
            .findFirst()
            .orElseThrow();

        String promptText = promptBuilt.messages().stream()
            .map(m -> m.content() != null ? m.content() : "")
            .reduce("", (a, b) -> a + "\n" + b);
        assertThat(promptBuilt.redacted()).isTrue();
        assertThat(promptText).doesNotContain("abc123");
        assertThat(promptText).contains("[REDACTED]");
    }

    @Test
    @DisplayName("PromptBuilt can include raw content with explicit raw flag")
    void promptBuiltCanBeRawWhenEnabled() {
        System.setProperty("nooa.log.prompts", "true");
        System.setProperty("nooa.log.prompts.raw", "true");
        agent.context().put("credentials", "token=abc123");
        llm.respondWith("ok");

        agent.runtime().generate(List.of(), null, Map.of());

        Event.PromptBuilt promptBuilt = (Event.PromptBuilt) agent.eventManager().all().stream()
            .filter(e -> e instanceof Event.PromptBuilt)
            .findFirst()
            .orElseThrow();

        String promptText = promptBuilt.messages().stream()
            .map(m -> m.content() != null ? m.content() : "")
            .reduce("", (a, b) -> a + "\n" + b);
        assertThat(promptBuilt.redacted()).isFalse();
        assertThat(promptText).contains("token=abc123");
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
    @DisplayName("callPlan includes method arguments in the prompt")
    void callPlanIncludesArguments() throws Exception {
        llm.respondWith("ok");
        var strategy = new GenerationStrategy() {
            public Object execute(RuntimeServices rt, CurrentCall call) {
                rt.generate(List.of(), null, Map.of());
                return "done";
            }
        };
        var call = CurrentCall.fromMethod(
            TestAgent.class.getDeclaredMethod("generate", String.class),
            new Object[]{"hello-world-arg"});
        agent.runtime().callPlan(strategy, call);

        assertThat(llm.lastMessages()).anyMatch(m ->
            m.role().equals("user") && m.content() != null && m.content().contains("hello-world-arg"));
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
