package ai.nooa.eval.scorers;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;
import ai.nooa.eval.Score;
import ai.nooa.eval.Scorer;
import ai.nooa.eval.Values;

/**
 * Scores tool-call economy against the case metadata {@code maxToolCalls}.
 * The value scales down as the run exceeds the budget. Cases without the
 * metadata are not applicable.
 */
public final class ToolEfficiencyScorer implements Scorer {

    @Override
    public String name() {
        return "ToolEfficiency";
    }

    @Override
    public double defaultWeight() {
        return 5.0;
    }

    @Override
    public Score score(EvalCase evalCase, RunTrace trace) {
        Double max = Values.number(evalCase.metadata(), "maxToolCalls");
        if (max == null) {
            return Score.notApplicable(name(), "no maxToolCalls metadata");
        }
        int calls = trace.toolCallCount();
        if (calls <= max) {
            return Score.of(name(), 1.0, true, "within tool budget (" + calls + " <= " + max.intValue() + ")");
        }
        double value = max / calls;
        return Score.of(name(), value, false,
            "exceeded tool budget: " + calls + " > " + max.intValue());
    }
}
