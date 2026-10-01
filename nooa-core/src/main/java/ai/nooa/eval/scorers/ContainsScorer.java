package ai.nooa.eval.scorers;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;
import ai.nooa.eval.Score;
import ai.nooa.eval.Scorer;

/**
 * Passes when the run output contains the expected substring. Cases without a
 * string expectation are neutral.
 */
public final class ContainsScorer implements Scorer {

    @Override
    public String name() {
        return "Contains";
    }

    @Override
    public double defaultWeight() {
        return 10.0;
    }

    @Override
    public Score score(EvalCase evalCase, RunTrace trace) {
        if (!(evalCase.expected() instanceof String expected)) {
            return Score.notApplicable(name(), "no string expectation");
        }
        if (!trace.outputPresent() || trace.output() == null) {
            return Score.of(name(), 0.0, false, "no output (run failed)");
        }
        String actual = String.valueOf(trace.output());
        boolean passed = actual.contains(expected);
        return Score.of(name(), passed,
            passed ? "contained expected text" : "expected text not found");
    }
}
