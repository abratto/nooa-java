package ai.nooa.examples.eval;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
import ai.nooa.eval.EvalReport;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.strategy.PredictStrategy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reference usage of {@link EvalExtension}: a deterministic scripted client and
 * a JSONL dataset, run as an ordinary JUnit test.
 */
@DisplayName("eval extension (reference)")
class EvalExtensionDemoTest {

    public static class EchoAgent extends Agent {
        public EchoAgent(UnifiedLLM llm) {
            super(llm);
        }

        @Generate(prompt = "Return exactly the provided text.")
        @Strategy(PredictStrategy.class)
        public String echo(String text) {
            throw new UnsupportedOperationException();
        }
    }

    @BeforeAll
    static void setUp() {
        FakeLLMClient llm = new FakeLLMClient();
        llm.respondWith("hello");
        EvalExtension.useLlm(llm);
    }

    @AfterAll
    static void tearDown() {
        EvalExtension.useLlm(null);
    }

    @Test
    @ExtendWith(EvalExtension.class)
    @Eval(agent = EchoAgent.class,
          dataset = "src/test/resources/eval/smoke.jsonl",
          minWeighted = 0.5, gate = "ExactMatch", gateMin = 1.0)
    void meetsThreshold(EvalReport report) {
        assertThat(report.cases()).hasSize(1);
        assertThat(report.aggregate().completion().passAt1()).isEqualTo(1.0);
    }
}
