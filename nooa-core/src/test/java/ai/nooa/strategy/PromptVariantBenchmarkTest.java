package ai.nooa.strategy;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.UnifiedLLM;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Prompt variant benchmark")
class PromptVariantBenchmarkTest {

    private static final class TestAgent extends Agent {
        TestAgent(UnifiedLLM llm) { super(llm); }

        @Generate
        public String summarize(String article) {
            throw new UnsupportedOperationException();
        }
    }

    private record Task(String id, String docstring, String input) {}

    @Test
    @DisplayName("enriched arguments prompt has materially higher grounding than docstring-only baseline")
    void enrichedArgumentPromptGroundsMoreOfTheInput() throws Exception {
        var tasks = List.of(
            new Task("news-1",
                "Summarize the article in 3 bullet points.",
                "Acme announced a new battery chemistry that cuts charging time by 40% and reduces heat while extending cycle life for electric vehicles."),
            new Task("news-2",
                "Extract the key risk and mitigation from this coverage.",
                "Analysts warn that the supply chain remains fragile after the port strike, but the company says it has diversified vendors and added local warehousing."),
            new Task("news-3",
                "Return the main action item from the article in one sentence.",
                "The board approved a capex plan to expand data centers and hired a chief systems reliability officer to support a larger AI workload.")
        );

        var llm = new FakeLLMClient();
        var baselineResults = new ArrayList<String>();
        var enrichedResults = new ArrayList<String>();

        for (var task : tasks) {
            var call = CurrentCall.fromMethod(
                TestAgent.class.getDeclaredMethod("summarize", String.class),
                new Object[]{task.input()});

            var baselinePrompt = call.docstring();
            var enrichedPrompt = call.userPrompt(true, 200);

            var baselineScore = groundingScore(baselinePrompt, task.input());
            var enrichedScore = groundingScore(enrichedPrompt, task.input());

            baselineResults.add(task.id() + "=" + String.format(Locale.ROOT, "%.3f", baselineScore));
            enrichedResults.add(task.id() + "=" + String.format(Locale.ROOT, "%.3f", enrichedScore));

            System.out.println("Task " + task.id() + " | baseline=" + baselineScore + " | enriched=" + enrichedScore);
            assertThat(enrichedScore).isGreaterThanOrEqualTo(baselineScore);
        }

        var meanBaseline = average(baselineResults);
        var meanEnriched = average(enrichedResults);
        System.out.println("Mean baseline grounding=" + meanBaseline);
        System.out.println("Mean enriched grounding=" + meanEnriched);
        assertThat(meanEnriched).isGreaterThan(meanBaseline);
    }

    private static double groundingScore(String prompt, String input) {
        var promptWords = tokenize(prompt);
        var inputWords = tokenize(input);
        var overlap = 0;
        for (var word : inputWords) {
            if (promptWords.contains(word)) {
                overlap++;
            }
        }
        return inputWords.isEmpty() ? 0.0 : (double) overlap / inputWords.size();
    }

    private static Set<String> tokenize(String text) {
        return java.util.Arrays.stream(text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .split("\\s+"))
            .filter(token -> !token.isBlank() && token.length() > 2)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static double average(List<String> values) {
        double total = 0.0;
        for (var value : values) {
            total += Double.parseDouble(value.substring(value.indexOf('=') + 1));
        }
        return values.isEmpty() ? 0.0 : total / values.size();
    }
}
