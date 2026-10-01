package ai.nooa.eval.judge;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;

import java.util.List;
import java.util.Map;

/**
 * Scoring strategy for dimensions that require judgement rather than
 * deterministic comparison (reasoning quality, instruction adherence, tone).
 *
 * <p>The SPI is deliberately small. The reference implementation
 * {@link LlmJudge} is opt-in and only usable when a judge model is explicitly
 * supplied; {@link FakeJudge} drives deterministic tests.</p>
 */
public interface Judge {

    Verdict judge(EvalCase evalCase, RunTrace trace, List<String> criteria);

    /**
     * @param score     normalized score in {@code [0, 1]}
     * @param rationale short explanation for the verdict
     * @param data      optional structured payload
     */
    record Verdict(double score, String rationale, Map<String, Object> data) {
        public Verdict {
            data = data == null ? Map.of() : Map.copyOf(data);
        }

        public Verdict(double score, String rationale) {
            this(score, rationale, Map.of());
        }

        public boolean passed() {
            return score >= 0.5;
        }

        public boolean passed(double threshold) {
            return score >= threshold;
        }
    }
}
