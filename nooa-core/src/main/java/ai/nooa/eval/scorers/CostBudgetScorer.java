package ai.nooa.eval.scorers;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;
import ai.nooa.eval.Score;
import ai.nooa.eval.Scorer;
import ai.nooa.eval.Values;

/**
 * Scores run cost against the case metadata {@code maxCostUsd}. Passes within
 * budget; the value scales down as the budget is exceeded. Not applicable when
 * no budget is declared or cost could not be computed (unknown model pricing).
 */
public final class CostBudgetScorer implements Scorer {

    @Override
    public String name() {
        return "CostBudget";
    }

    @Override
    public double defaultWeight() {
        return 2.0;
    }

    @Override
    public Score score(EvalCase evalCase, RunTrace trace) {
        Double max = Values.number(evalCase.metadata(), "maxCostUsd");
        if (max == null || max <= 0) {
            return Score.notApplicable(name(), "no maxCostUsd metadata");
        }
        if (trace.costUsd() == null) {
            return Score.notApplicable(name(), "cost unavailable (unknown model pricing)");
        }
        double cost = trace.costUsd();
        if (cost <= max) {
            return Score.of(name(), 1.0, true, "within cost budget");
        }
        return Score.of(name(), max / cost, false,
            "exceeded cost budget: " + cost + " > " + max);
    }
}
