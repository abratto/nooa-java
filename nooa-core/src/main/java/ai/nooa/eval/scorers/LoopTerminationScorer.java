package ai.nooa.eval.scorers;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;
import ai.nooa.eval.Score;
import ai.nooa.eval.Scorer;
import ai.nooa.eval.Values;

/**
 * Scores loop termination: the run must finish cleanly and, when the case
 * metadata {@code maxSteps} is set, within that step budget. Applicable to all
 * cases; the step budget is optional.
 */
public final class LoopTerminationScorer implements Scorer {

    @Override
    public String name() {
        return "LoopTermination";
    }

    @Override
    public double defaultWeight() {
        return 5.0;
    }

    @Override
    public Score score(EvalCase evalCase, RunTrace trace) {
        Double maxSteps = Values.number(evalCase.metadata(), "maxSteps");
        boolean clean = trace.terminatedCleanly();
        if (maxSteps == null) {
            return Score.of(name(), clean ? 1.0 : 0.0, clean,
                clean ? "terminated cleanly" : "run did not terminate cleanly");
        }
        boolean within = trace.stepCount() <= maxSteps;
        boolean passed = clean && within;
        String detail = passed ? "terminated cleanly within step budget"
            : !clean ? "run did not terminate cleanly"
            : "exceeded step budget: " + trace.stepCount() + " > " + maxSteps.intValue();
        return Score.of(name(), passed, detail);
    }
}
