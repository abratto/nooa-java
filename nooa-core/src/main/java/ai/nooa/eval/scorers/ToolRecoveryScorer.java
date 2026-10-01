package ai.nooa.eval.scorers;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;
import ai.nooa.eval.Score;
import ai.nooa.eval.Scorer;

/**
 * Scores recovery from tool failures: passes when the run still produced output
 * after one or more tool errors. Cases with no tool errors are neutral
 * (applicable, passed) since there was nothing to recover from.
 */
public final class ToolRecoveryScorer implements Scorer {

    @Override
    public String name() {
        return "ToolRecovery";
    }

    @Override
    public double defaultWeight() {
        return 5.0;
    }

    @Override
    public Score score(EvalCase evalCase, RunTrace trace) {
        if (trace.toolErrorCount() == 0) {
            return Score.of(name(), 1.0, true, "no tool errors");
        }
        boolean recovered = trace.outputPresent();
        return Score.of(name(), recovered, recovered
            ? "recovered from " + trace.toolErrorCount() + " tool error(s)"
            : "did not recover from tool errors");
    }
}
