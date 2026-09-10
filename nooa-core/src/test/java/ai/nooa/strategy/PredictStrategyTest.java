package ai.nooa.strategy;

import com.fasterxml.jackson.annotation.JsonProperty;
import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
import ai.nooa.config.PredictConfig;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.context.Event;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PredictStrategy")
class PredictStrategyTest {

    public record SentimentResult(
        @JsonProperty("sentiment") String sentiment,
        @JsonProperty("confidence") double confidence) {}

    public static class TestPredictAgent extends Agent {
        public TestPredictAgent(UnifiedLLM llm) { super(llm); }
        @Generate(prompt = "Classify the text and return its sentiment and confidence.")
        @Strategy(PredictStrategy.class)
        public SentimentResult analyze(String text) { throw new UnsupportedOperationException(); }

        @Generate(prompt = "Return a concise plain-text answer.")
        @Strategy(PredictStrategy.class)
        public String answer(String text) { throw new UnsupportedOperationException(); }

        @Generate(prompt = "Return sentiment results.")
        @Strategy(PredictStrategy.class)
        public List<SentimentResult> analyzeMany(String text) { throw new UnsupportedOperationException(); }
    }

    private FakeLLMClient llm;
    private TestPredictAgent agent;
    private PredictStrategy strategy;

    @BeforeEach
    void setUp() {
        llm = new FakeLLMClient();
        agent = AgentFactory.create(TestPredictAgent.class, llm);
        strategy = new PredictStrategy(PredictConfig.defaults());
    }

    @AfterEach
    void tearDown() { agent.close(); }

    @Test
    @DisplayName("parses JSON response into return type record")
    void parsesJsonResponse() throws Exception {
        llm.respondWith("{\"sentiment\":\"positive\",\"confidence\":0.95}");
        var call = CurrentCall.fromMethod(
            TestPredictAgent.class.getDeclaredMethod("analyze", String.class),
            new Object[]{"I love this!"});
        var result = strategy.execute(agent.runtime(), call);
        assertThat(result).isInstanceOf(SentimentResult.class);
        var sr = (SentimentResult) result;
        assertThat(sr.sentiment()).isEqualTo("positive");
        assertThat(sr.confidence()).isCloseTo(0.95, within(0.001));
    }

    @Test
    @DisplayName("parses generic collection return types")
    void parsesGenericCollectionResponse() throws Exception {
        llm.respondWith("[{\"sentiment\":\"positive\",\"confidence\":0.95}]");
        var call = CurrentCall.fromMethod(
            TestPredictAgent.class.getDeclaredMethod("analyzeMany", String.class),
            new Object[]{"I love this!"});

        var result = strategy.execute(agent.runtime(), call);

        assertThat(result).isInstanceOf(List.class);
        assertThat((List<?>) result).singleElement()
            .isInstanceOf(SentimentResult.class);
    }

    @Test
    @DisplayName("uses Generate.prompt as the runtime task instruction")
    void usesExplicitGeneratePrompt() throws Exception {
        llm.respondWith("{\"sentiment\":\"positive\",\"confidence\":0.95}");

        String previousLogging = System.getProperty("nooa.log.prompts");
        System.setProperty("nooa.log.prompts", "true");
        try {
            agent.analyze("I love this!");
        } finally {
            restore(previousLogging);
        }

        var prompt = agent.eventManager().all().stream()
            .filter(Event.PromptBuilt.class::isInstance)
            .map(Event.PromptBuilt.class::cast)
            .findFirst()
            .orElseThrow();
        assertThat(prompt.messages()).anyMatch(message ->
            message.content().contains("Classify the text and return its sentiment and confidence."));
    }

    @Test
    @DisplayName("strips markdown code fences from LLM response")
    void stripsCodeFences() throws Exception {
        llm.respondWith("```json\n{\"sentiment\":\"negative\",\"confidence\":0.88}\n```");
        var call = CurrentCall.fromMethod(
            TestPredictAgent.class.getDeclaredMethod("analyze", String.class),
            new Object[]{"terrible"});
        var result = strategy.execute(agent.runtime(), call);
        var sr = (SentimentResult) result;
        assertThat(sr.sentiment()).isEqualTo("negative");
        assertThat(sr.confidence()).isCloseTo(0.88, within(0.001));
    }

    @Test
    @DisplayName("returns raw text for String predictions")
    void returnsRawText() throws Exception {
        llm.respondWith("The answer is plain text.");
        var call = CurrentCall.fromMethod(
            TestPredictAgent.class.getDeclaredMethod("answer", String.class),
            new Object[]{"question"});

        assertThat(strategy.execute(agent.runtime(), call))
            .isEqualTo("The answer is plain text.");
    }

    @Test
    @DisplayName("throws GenerationError after exhausting retries")
    void throwsAfterMaxRetries() throws Exception {
        llm.respondWith("bad json {{{");
        llm.respondWith("also bad {{{");
        llm.respondWith("still bad {{{");
        var call = CurrentCall.fromMethod(
            TestPredictAgent.class.getDeclaredMethod("analyze", String.class),
            new Object[]{"test"});
        assertThatThrownBy(() -> strategy.execute(agent.runtime(), call))
            .isInstanceOf(ai.nooa.GenerationError.class)
            .hasMessageContaining("3 attempts");
    }

    @Test
    @DisplayName("includes a concise parse failure on the next retry")
    void includesRetryDiagnosticInNextPrompt() throws Exception {
        llm.respondWith("bad json {{{");
        llm.respondWith("{\"sentiment\":\"positive\",\"confidence\":0.9}");
        var call = CurrentCall.fromMethod(
            TestPredictAgent.class.getDeclaredMethod("analyze", String.class),
            new Object[]{"test"});

        String previousLogging = System.getProperty("nooa.log.prompts");
        System.setProperty("nooa.log.prompts", "true");
        try {
            strategy.execute(agent.runtime(), call);
        } finally {
            if (previousLogging == null) {
                System.clearProperty("nooa.log.prompts");
            } else {
                System.setProperty("nooa.log.prompts", previousLogging);
            }
        }

        var prompts = agent.eventManager().all().stream()
            .filter(Event.PromptBuilt.class::isInstance)
            .map(Event.PromptBuilt.class::cast)
            .toList();
        assertThat(prompts).hasSize(2);
        assertThat(prompts.get(0).messages()).noneMatch(message ->
            message.content().contains("Previous attempt failed structured-output validation"));
        assertThat(prompts.get(1).messages()).anyMatch(message ->
            message.content().contains("Previous attempt failed structured-output validation"));
    }

    @Test
    @DisplayName("retry diagnostic names the target schema")
    void retryDiagnosticNamesTargetSchema() throws Exception {
        llm.respondWith("bad json {{{");
        llm.respondWith("{\"sentiment\":\"positive\",\"confidence\":0.9}");
        var call = CurrentCall.fromMethod(
            TestPredictAgent.class.getDeclaredMethod("analyze", String.class),
            new Object[]{"test"});

        String previousLogging = System.getProperty("nooa.log.prompts");
        System.setProperty("nooa.log.prompts", "true");
        try {
            strategy.execute(agent.runtime(), call);
        } finally {
            restore(previousLogging);
        }

        var second = agent.eventManager().all().stream()
            .filter(Event.PromptBuilt.class::isInstance)
            .map(Event.PromptBuilt.class::cast)
            .skip(1)
            .findFirst()
            .orElseThrow();
        assertThat(second.messages()).anyMatch(message ->
            message.content().contains("Expected shape: `SentimentResult`"));
        assertThat(second.messages()).anyMatch(message ->
            message.content().contains("sentiment, confidence"));
    }

    private void restore(String previousLogging) {
        if (previousLogging == null) {
            System.clearProperty("nooa.log.prompts");
        } else {
            System.setProperty("nooa.log.prompts", previousLogging);
        }
    }
}
