package ai.nooa.eval.judge;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;
import ai.nooa.llm.Message;
import ai.nooa.llm.UnifiedLLM;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Reference LLM-as-judge implementation. Opt-in: it only exists when a judge
 * model is explicitly supplied, and it never assumes the agent's own model.
 *
 * <p>Uses temperature 0 for stability and asks for a JSON verdict. Judge
 * prompts are derived from the case, the run output, and the supplied
 * criteria. This is an instrument reading, not ground truth; calibrate against
 * human review before relying on it for release gates.</p>
 */
public final class LlmJudge implements Judge {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String DEFAULT_SYSTEM = """
        You are a strict, fair evaluator of AI agent outputs. Score each output
        from 0.0 (fails the criteria) to 1.0 (fully satisfies the criteria).
        Base your judgement only on the provided criteria and evidence.
        Respond with a single JSON object: {"score": <number>, "rationale": "<one or two sentences>"}.
        """;

    private final UnifiedLLM judgeModel;
    private final String systemPrompt;

    public LlmJudge(UnifiedLLM judgeModel) {
        this(judgeModel, DEFAULT_SYSTEM);
    }

    public LlmJudge(UnifiedLLM judgeModel, String systemPrompt) {
        this.judgeModel = java.util.Objects.requireNonNull(judgeModel, "judgeModel");
        this.systemPrompt = systemPrompt == null ? DEFAULT_SYSTEM : systemPrompt;
    }

    @Override
    public Verdict judge(EvalCase evalCase, RunTrace trace, List<String> criteria) {
        List<Message> messages = List.of(
            Message.system(systemPrompt),
            Message.user(buildPrompt(evalCase, trace, criteria)));
        var response = judgeModel.chat(messages, List.of(), Object.class,
            Map.of("temperature", 0));
        return parse(response.content());
    }

    private static String buildPrompt(EvalCase evalCase, RunTrace trace, List<String> criteria) {
        StringBuilder sb = new StringBuilder();
        sb.append("Criteria:\n");
        if (criteria == null || criteria.isEmpty()) {
            sb.append("- Correctness and instruction adherence.\n");
        } else {
            for (String criterion : criteria) {
                sb.append("- ").append(criterion).append('\n');
            }
        }
        sb.append("\nTask inputs:\n").append(evalCase.input()).append('\n');
        if (evalCase.expected() != null) {
            sb.append("\nExpected (reference): ").append(evalCase.expected()).append('\n');
        }
        sb.append("\nAgent output:\n").append(trace.output()).append('\n');
        return sb.toString();
    }

    private static Verdict parse(String content) {
        if (content == null || content.isBlank()) {
            return new Verdict(0.0, "judge returned empty response");
        }
        String text = content.strip();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return new Verdict(0.0, "judge returned non-JSON response");
        }
        try {
            JsonNode node = JSON.readTree(text.substring(start, end + 1));
            double score = node.path("score").asDouble(0.0);
            score = Math.max(0.0, Math.min(1.0, score));
            String rationale = node.path("rationale").asText("");
            return new Verdict(score, rationale);
        } catch (Exception e) {
            return new Verdict(0.0, "judge response could not be parsed: " + e.getMessage());
        }
    }
}
