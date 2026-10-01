package ai.nooa.eval.scorers;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;
import ai.nooa.eval.Score;
import ai.nooa.eval.Scorer;
import ai.nooa.eval.Values;

/**
 * Scores step economy against the case metadata {@code maxSteps}. The value
 * scales down as the budget is exceeded. Not applicable without the metadata.
 */
public final class StepEfficiencyScorer implements Scorer {

    @Override
    public String name() {
        return "StepEfficiency";
    }

    @Override
    public double defaultWeight() {
        return 5.0;
    }

    @Override
    public Score score(EvalCase evalCase, RunTrace trace) {
        Double maxSteps = Values.number(evalCase.metadata(), "maxSteps");
        if (maxSteps == null || maxSteps <= 0) {
            return Score.notApplicable(name(), "no maxSteps metadata");
        }
        int steps = trace.stepCount();
        if (steps <= maxSteps) {
            return Score.of(name(), 1.0, true, "within step budget (" + steps + ")");
        }
        return Score.of(name(), maxSteps / steps, false,
            "exceeded step budget: " + steps + " > " + maxSteps.intValue());
    }
}
