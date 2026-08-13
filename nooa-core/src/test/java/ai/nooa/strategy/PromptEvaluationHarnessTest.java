package ai.nooa.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Prompt evaluation harness")
class PromptEvaluationHarnessTest {

    @Test
    @DisplayName("enriched variants improve grounding over the baseline")
    void enrichedVariantsImproveGrounding() {
        var tasks = List.of(
            new PromptEvaluationHarness.TaskSpec(
                "news-1",
                "Summarize this article in 3 bullet points.",
                "Acme announced a new battery chemistry that cuts charging time by 40% and reduces heat while extending cycle life for electric vehicles.",
                "Acme disclosed a new battery chemistry that reduces charging time and heat while extending cycle life."),
            new PromptEvaluationHarness.TaskSpec(
                "news-2",
                "Extract the key risk and mitigation.",
                "Analysts warn that the supply chain remains fragile after the port strike, but the company says it has diversified vendors and added local warehousing.",
                "Key risk: fragile supply chain. Mitigation: diversified vendors and local warehousing."),
            new PromptEvaluationHarness.TaskSpec(
                "news-3",
                "Return the main action item in one sentence.",
                "The board approved a capex plan to expand data centers and hired a chief systems reliability officer to support a larger AI workload.",
                "The company will expand data centers and hire a chief systems reliability officer to support AI growth.")
        );

        var results = PromptEvaluationHarness.run(tasks);
        PromptEvaluationHarness.printSummary(results);

        var baseline = results.stream().filter(r -> "baseline".equals(r.variant())).mapToDouble(PromptEvaluationHarness.Result::groundingScore).average().orElse(0.0);
        var enriched = results.stream().filter(r -> "args".equals(r.variant()) || "truncated-args".equals(r.variant())).mapToDouble(PromptEvaluationHarness.Result::groundingScore).average().orElse(0.0);

        assertThat(enriched).isGreaterThan(baseline);
    }
}
