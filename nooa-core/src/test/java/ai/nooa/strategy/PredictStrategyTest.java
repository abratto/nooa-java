package ai.nooa.strategy;

import com.fasterxml.jackson.annotation.JsonProperty;
import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
import ai.nooa.config.PredictConfig;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.UnifiedLLM;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PredictStrategy")
class PredictStrategyTest {

    record SentimentResult(
        @JsonProperty("sentiment") String sentiment,
        @JsonProperty("confidence") double confidence) {}

    static class TestPredictAgent extends Agent {
        public TestPredictAgent(UnifiedLLM llm) { super(llm); }
        @Generate @Strategy(PredictStrategy.class)
        public SentimentResult analyze(String text) { throw new UnsupportedOperationException(); }
    }

    private FakeLLMClient llm;
    private TestPredictAgent agent;
    private PredictStrategy strategy;

    @BeforeEach
    void setUp() {
        llm = new FakeLLMClient();
        agent = new TestPredictAgent(llm);
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
}
