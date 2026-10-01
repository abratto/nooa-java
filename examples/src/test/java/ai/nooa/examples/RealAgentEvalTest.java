package ai.nooa.examples;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.EvalDataset;
import ai.nooa.eval.EvalReport;
import ai.nooa.eval.EvalRunner;
import ai.nooa.eval.Score;
import ai.nooa.llm.FakeLLMClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evaluates a real example agent ({@link SentimentAgent}) end-to-end through
 * {@link EvalRunner} with a scripted client, exercising structured-output
 * scoring against the agent's own record contract.
 */
@DisplayName("eval against a real example agent")
class RealAgentEvalTest {

    @Test
    void evaluatesSentimentAgentStructuredOutput() {
        FakeLLMClient llm = new FakeLLMClient();
        llm.respondWith(
            "{\"sentiment\":\"positive\",\"confidence\":0.9,\"reasoning\":\"praise\"}");

        EvalCase evalCase = new EvalCase(
            "positive", "analyze", Map.of("text", "I love this product"),
            new SentimentResult("positive", 0.9, "praise"),
            Map.of(), List.of(), Set.of("canonical"), Map.of());
        EvalDataset dataset = EvalDataset.of("sentiment", List.of(evalCase));

        EvalReport report = EvalRunner.builder(SentimentAgent.class, llm).build().run(dataset);
        EvalReport.CaseReport caseReport = report.cases().get(0);

        assertThat(report.aggregate().completion().passAt1()).isEqualTo(1.0);
        assertThat(score(caseReport, "StructuredField").passed()).isTrue();
        assertThat(score(caseReport, "ExactMatch").passed()).isTrue();
    }

    private static Score score(EvalReport.CaseReport caseReport, String scorer) {
        return caseReport.scores().stream()
            .filter(s -> s.scorer().equals(scorer))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no score for " + scorer));
    }
}
