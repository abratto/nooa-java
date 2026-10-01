package ai.nooa.eval.scorers;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;
import ai.nooa.eval.Score;
import ai.nooa.eval.Scorer;
import ai.nooa.eval.Values;

/**
 * Scores wall-clock latency against the case metadata {@code maxDurationMs}.
 * Passes within budget; the value scales down as the budget is exceeded.
 * Cases without the metadata are not applicable.
 */
public final class LatencyBudgetScorer implements Scorer {

    @Override
    public String name() {
        return "LatencyBudget";
    }

    @Override
    public double defaultWeight() {
        return 5.0;
    }

    @Override
    public Score score(EvalCase evalCase, RunTrace trace) {
        Double max = Values.number(evalCase.metadata(), "maxDurationMs");
        if (max == null || max <= 0) {
            return Score.notApplicable(name(), "no maxDurationMs metadata");
        }
        long duration = trace.durationMs();
        if (duration <= max) {
            return Score.of(name(), 1.0, true, "within latency budget (" + duration + "ms)");
        }
        return Score.of(name(), max / duration, false,
            "exceeded latency budget: " + duration + "ms > " + max.intValue() + "ms");
    }
}
