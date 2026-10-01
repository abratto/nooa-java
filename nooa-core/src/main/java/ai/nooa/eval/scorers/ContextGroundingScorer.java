package ai.nooa.eval.scorers;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;
import ai.nooa.eval.Score;
import ai.nooa.eval.Scorer;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Lexical grounding of the captured prompt against the case inputs: the
 * fraction of input tokens that appear in the prompt. This promotes the
 * test-only {@code PromptEvaluationHarness.groundingScore} into the eval
 * layer. Not applicable when prompt capture is disabled (no {@code lastPrompt}
 * signal) or the inputs carry no comparable tokens.
 */
public final class ContextGroundingScorer implements Scorer {

    @Override
    public String name() {
        return "ContextGrounding";
    }

    @Override
    public double defaultWeight() {
        return 5.0;
    }

    @Override
    public Score score(EvalCase evalCase, RunTrace trace) {
        Object promptSignal = trace.signals().get("lastPrompt");
        if (!(promptSignal instanceof String prompt) || prompt.isBlank()) {
            return Score.notApplicable(name(), "no captured prompt (enable NOOA_LOG_PROMPTS)");
        }
        String inputText = evalCase.input().values().stream()
            .map(String::valueOf)
            .reduce("", (a, b) -> a + " " + b);
        Set<String> inputTokens = tokens(inputText);
        if (inputTokens.isEmpty()) {
            return Score.notApplicable(name(), "no comparable input tokens");
        }
        Set<String> promptTokens = tokens(prompt);
        int present = 0;
        for (String token : inputTokens) {
            if (promptTokens.contains(token)) {
                present++;
            }
        }
        double score = (double) present / inputTokens.size();
        return Score.of(name(), score, score >= 0.5,
            String.format(java.util.Locale.ROOT, "input token grounding %.0f%%", score * 100));
    }

    private static Set<String> tokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (text == null) {
            return tokens;
        }
        for (String raw : text.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ").split("\\s+")) {
            if (raw.length() > 2) {
                tokens.add(raw);
            }
        }
        return tokens;
    }
}
