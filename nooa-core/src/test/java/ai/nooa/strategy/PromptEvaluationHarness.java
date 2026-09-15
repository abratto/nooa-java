package ai.nooa.strategy;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PromptEvaluationHarness {
    private static final Logger log = LoggerFactory.getLogger(PromptEvaluationHarness.class);

    private PromptEvaluationHarness() {}

    public record TaskSpec(String id, String instruction, String input, String expected) {}

    public record Result(String taskId, String variant, double groundingScore,
                         long promptChars, long latencyMs) {}

    public static List<Result> run(List<TaskSpec> tasks) {
        var results = new ArrayList<Result>();
        for (var task : tasks) {
            results.add(runVariant(task, "baseline", task.instruction()));
            results.add(runVariant(task, "args", task.instruction() + "\n\nInputs:\n- article: " + truncate(task.input(), 200)));
            results.add(runVariant(task, "truncated-args", task.instruction() + "\n\nInputs:\n- article: " + truncate(task.input(), 500)));
        }
        results.sort(Comparator.comparing(Result::taskId).thenComparing(Result::variant));
        return results;
    }

    private static Result runVariant(TaskSpec task, String variant, String promptText) {
        long start = System.nanoTime();
        double score = groundingScore(promptText, task.input());
        long latency = (System.nanoTime() - start) / 1_000_000L;
        return new Result(task.id(), variant, score, promptText.length(), latency);
    }

    private static double groundingScore(String prompt, String input) {
        var promptSet = normalize(prompt);
        var inputSet = normalize(input);
        if (inputSet.isEmpty()) return 0.0;
        int overlap = 0;
        for (String token : inputSet) {
            if (promptSet.contains(token)) overlap++;
        }
        return (double) overlap / inputSet.size();
    }

    private static List<String> normalize(String text) {
        if (text == null || text.isBlank()) return List.of();
        return java.util.Arrays.stream(text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .split("\\s+"))
            .filter(token -> !token.isBlank() && token.length() > 2)
            .toList();
    }

    private static String truncate(String value, int maxChars) {
        if (value == null) return "null";
        if (value.length() <= maxChars) return value;
        return value.substring(0, Math.max(0, maxChars)).trim() + "...";
    }

    public static void printSummary(List<Result> results) {
        for (var result : results) {
            log.info("{} | {} | grounding={} | chars={} | latencyMs={}",
                result.taskId(), result.variant(),
                String.format(Locale.ROOT, "%.3f", result.groundingScore()),
                result.promptChars(), result.latencyMs());
        }
    }

    public static class BenchmarkAgent extends Agent {
        public BenchmarkAgent(UnifiedLLM llm) { super(llm); }

        @Generate
        public String summarize(String article) {
            throw new UnsupportedOperationException();
        }
    }
}
