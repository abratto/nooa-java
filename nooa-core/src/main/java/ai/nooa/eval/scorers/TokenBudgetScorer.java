package ai.nooa.eval.scorers;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;
import ai.nooa.eval.Score;
import ai.nooa.eval.Scorer;
import ai.nooa.eval.Values;

/**
 * Scores total token usage against the case metadata {@code maxTotalTokens}.
 * Passes within budget; the value scales down as the budget is exceeded.
 * Cases without the metadata are not applicable.
 */
public final class TokenBudgetScorer implements Scorer {

    @Override
    public String name() {
        return "TokenBudget";
    }

    @Override
    public double defaultWeight() {
        return 3.0;
    }

    @Override
    public Score score(EvalCase evalCase, RunTrace trace) {
        Double max = Values.number(evalCase.metadata(), "maxTotalTokens");
        if (max == null || max <= 0) {
            return Score.notApplicable(name(), "no maxTotalTokens metadata");
        }
        int tokens = trace.totalTokens();
        if (tokens <= max) {
            return Score.of(name(), 1.0, true, "within token budget (" + tokens + ")");
        }
        return Score.of(name(), max / tokens, false,
            "exceeded token budget: " + tokens + " > " + max.intValue());
    }
}
