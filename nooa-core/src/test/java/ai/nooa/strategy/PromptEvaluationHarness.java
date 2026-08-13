package ai.nooa.strategy;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class PromptEvaluationHarness {

    public static final class TaskSpec {
        private final String id;
        private final String instruction;
        private final String input;
        private final String expected;

        public TaskSpec(String id, String instruction, String input, String expected) {
            this.id = id;
            this.instruction = instruction;
            this.input = input;
            this.expected = expected;
        }

        public String id() { return id; }
        public String instruction() { return instruction; }
        public String input() { return input; }
        public String expected() { return expected; }
    }

    public static final class Result {
        private final String taskId;
        private final String variant;
        private final double groundingScore;
        private final long promptChars;
        private final long latencyMs;

        public Result(String taskId, String variant, double groundingScore, long promptChars, long latencyMs) {
            this.taskId = taskId;
            this.variant = variant;
            this.groundingScore = groundingScore;
            this.promptChars = promptChars;
            this.latencyMs = latencyMs;
        }

        public String taskId() { return taskId; }
        public String variant() { return variant; }
        public double groundingScore() { return groundingScore; }
        public long promptChars() { return promptChars; }
        public long latencyMs() { return latencyMs; }
    }

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
            System.out.println(result.taskId() + " | " + result.variant() + " | grounding=" + String.format(Locale.ROOT, "%.3f", result.groundingScore()) + " | chars=" + result.promptChars() + " | latencyMs=" + result.latencyMs());
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
