package ai.nooa.eval.scorers;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;
import ai.nooa.eval.Score;
import ai.nooa.eval.Scorer;
import ai.nooa.eval.Values;

/**
 * Passes when the run output exactly equals the case's expected value
 * (case-sensitive strings after trimming, numeric-aware for numbers).
 * Cases without an expected value are neutral.
 */
public final class ExactMatchScorer implements Scorer {

    @Override
    public String name() {
        return "ExactMatch";
    }

    @Override
    public double defaultWeight() {
        return 30.0;
    }

    @Override
    public Score score(EvalCase evalCase, RunTrace trace) {
        if (evalCase.expected() == null) {
            return Score.notApplicable(name(), "no expected value");
        }
        if (!trace.outputPresent()) {
            return Score.of(name(), 0.0, false, "no output (run failed)");
        }
        boolean passed = Values.equal(trace.output(), evalCase.expected());
        return Score.of(name(), passed,
            passed ? "output matched" : "output " + describe(trace.output())
                + " != expected " + describe(evalCase.expected()));
    }

    private static String describe(Object value) {
        String text = String.valueOf(value);
        return text.length() > 120 ? text.substring(0, 120) + "..." : text;
    }
}
