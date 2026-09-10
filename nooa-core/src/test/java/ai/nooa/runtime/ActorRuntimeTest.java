package ai.nooa.runtime;

import ai.nooa.Agent;
import ai.nooa.annotations.NoTrace;
import ai.nooa.annotations.Generate;
import ai.nooa.config.AgentConfig;
import ai.nooa.context.Event;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.Tool;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.strategy.CurrentCall;
import ai.nooa.strategy.GenerationStrategy;
import ai.nooa.strategy.RuntimeServices;
import ai.nooa.runtime.CallMiddleware;
import ai.nooa.runtime.sandbox.SandboxExecutor;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ActorRuntime")
class ActorRuntimeTest {

    static class TestAgent extends Agent {
        public TestAgent(UnifiedLLM llm) { super(llm); }
        public TestAgent(UnifiedLLM llm, AgentConfig config) { super(llm, config); }
        @Generate public String generate(String x) { throw new UnsupportedOperationException(); }
        @Generate @NoTrace public String untraced(String x) { throw new UnsupportedOperationException(); }
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
    @DisplayName("callPlan honors tracing configuration and @NoTrace")
    void callPlanHonorsTracingConfigurationAndNoTrace() throws Exception {
        var traceDir = Files.createTempDirectory("nooa-actor-trace");
        try {
            ai.nooa.tracing.Tracing.enable(ai.nooa.tracing.Tracing.jsonl(traceDir));
            var strategy = new GenerationStrategy() {
                public Object execute(RuntimeServices rt, CurrentCall call) {
                    return "done";
                }
            };

            var noTraceCall = CurrentCall.fromMethod(
                TestAgent.class.getDeclaredMethod("untraced", String.class),
                new Object[]{"test"});
            agent.runtime().callPlan(strategy, noTraceCall);
            ai.nooa.tracing.Tracing.shutdown();
            assertThat(Files.exists(traceDir.resolve("traces.jsonl"))).isFalse();

            var disabledAgent = new TestAgent(llm, AgentConfig.defaults().withTracing(false));
            try {
                var tracedCall = CurrentCall.fromMethod(
                    TestAgent.class.getDeclaredMethod("generate", String.class),
                    new Object[]{"test"});
                disabledAgent.runtime().callPlan(strategy, tracedCall);
            } finally {
                disabledAgent.close();
            }
        } finally {
            ai.nooa.tracing.Tracing.shutdown();
            try (var files = Files.walk(traceDir)) {
                files.sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                    });
            }
        }
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

    @Test
    @DisplayName("executeCode uses a configured sandbox executor")
    void executeCodeUsesConfiguredSandboxExecutor() {
        var executedCode = new java.util.concurrent.atomic.AtomicReference<String>();
        var boundName = new java.util.concurrent.atomic.AtomicReference<String>();
        var closed = new AtomicBoolean();
        SandboxExecutor executor = new SandboxExecutor() {
            @Override public void bindVariable(String name, String typeName, Object value) {
                boundName.set(name);
            }

            @Override public ai.nooa.strategy.ExecutionResult execute(String code) {
                executedCode.set(code);
                return ai.nooa.strategy.ExecutionResult.ofValue("injected");
            }

            @Override public void close() {
                closed.set(true);
            }
        };
        var configured = new TestAgent(llm,
            AgentConfig.defaults().withSandboxExecutor(ignored -> executor));
        try {
            configured.runtime().bindVariable("input", "String", "value");
            var result = configured.runtime().executeCode("returnResult(input);", Map.of());
            assertThat(result.returnValue()).isEqualTo("injected");
            assertThat(executedCode).hasValue("returnResult(input);");
            assertThat(boundName).hasValue("input");
        } finally {
            configured.close();
        }
        assertThat(closed).isTrue();
    }

    @Test
    void middlewareCanRewriteAndRejectOperationRequests() {
        var rewrite = new CallMiddleware() {
            @Override public LlmRequest beforeLlmRequest(Agent agent, LlmRequest request) {
                return request.withSamplingParams(Map.of("temperature", 0.0));
            }
            @Override public CodeRequest beforeCodeRequest(Agent agent, CodeRequest request) {
                return request.withCode("int rewritten = 1;");
            }
        };
        var configured = new TestAgent(llm, AgentConfig.defaults().withMiddleware(rewrite));
        try {
            llm.respondWith("ok");
            assertThat(configured.runtime().generate(List.of(), null, Map.of())
                .content()).isEqualTo("ok");
            assertThat(configured.runtime().executeCode("int original = 1;", Map.of()).success())
                .isTrue();
        } finally {
            configured.close();
        }
    }

    @Test
    @DisplayName("middleware wraps LLM and code operations")
    void middlewareWrapsOperations() {
        var beforeLlm = new AtomicInteger();
        var afterLlm = new AtomicInteger();
        var beforeCode = new AtomicInteger();
        var afterCode = new AtomicInteger();
        var hook = new CallMiddleware() {
            @Override public void beforeLlm(Agent agent, List<ai.nooa.llm.Message> messages,
                                             List<Tool> tools, Map<String, Object> params) {
                beforeLlm.incrementAndGet();
            }
            @Override public ai.nooa.llm.LLMResponse afterLlm(
                Agent agent, ai.nooa.llm.LLMResponse response) {
                afterLlm.incrementAndGet();
                return response;
            }
            @Override public String beforeCode(Agent agent, String code) {
                beforeCode.incrementAndGet();
                return code;
            }
            @Override public ai.nooa.strategy.ExecutionResult afterCode(
                Agent agent, ai.nooa.strategy.ExecutionResult result) {
                afterCode.incrementAndGet();
                return result;
            }
        };
        var configured = new TestAgent(llm, AgentConfig.defaults().withMiddleware(hook));
        try {
            llm.respondWith("ok");
            configured.runtime().generate(List.of(), null, Map.of());
            configured.runtime().executeCode("int x = 1;", Map.of());
        } finally {
            configured.close();
        }

        assertThat(beforeLlm).hasValue(1);
        assertThat(afterLlm).hasValue(1);
        assertThat(beforeCode).hasValue(1);
        assertThat(afterCode).hasValue(1);
    }
}
