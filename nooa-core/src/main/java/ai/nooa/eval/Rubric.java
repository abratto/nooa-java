package ai.nooa.eval;

import ai.nooa.eval.scorers.ContainsScorer;
import ai.nooa.eval.scorers.ContextGroundingScorer;
import ai.nooa.eval.scorers.CostBudgetScorer;
import ai.nooa.eval.scorers.ExactMatchScorer;
import ai.nooa.eval.scorers.ForbiddenToolsScorer;
import ai.nooa.eval.scorers.LatencyBudgetScorer;
import ai.nooa.eval.scorers.LoopTerminationScorer;
import ai.nooa.eval.scorers.MeltdownScorer;
import ai.nooa.eval.scorers.MilestoneScorer;
import ai.nooa.eval.scorers.PolicyComplianceScorer;
import ai.nooa.eval.scorers.RequiredToolsScorer;
import ai.nooa.eval.scorers.SecretLeakScorer;
import ai.nooa.eval.scorers.StepEfficiencyScorer;
import ai.nooa.eval.scorers.StructuredFieldScorer;
import ai.nooa.eval.scorers.TokenBudgetScorer;
import ai.nooa.eval.scorers.ToolEfficiencyScorer;
import ai.nooa.eval.scorers.ToolRecoveryScorer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A weighted collection of {@link Scorer}s producing one aggregate score per
 * case. Scorers absent from a case's results are simply skipped when
 * aggregating, so the same rubric works with partial scorer sets.
 */
public record Rubric(List<WeightedScorer> scorers) {

    public record WeightedScorer(Scorer scorer, double weight) {
        public WeightedScorer {
            if (scorer == null) {
                throw new IllegalArgumentException("scorer must not be null");
            }
        }
    }

    public Rubric {
        scorers = scorers == null ? List.of() : List.copyOf(scorers);
    }

    /** The default rubric: correctness, tool-use, safety, and ops scorers. */
    public static Rubric defaults() {
        return new Rubric(List.of(
            new WeightedScorer(new ExactMatchScorer(), 20.0),
            new WeightedScorer(new StructuredFieldScorer(), 20.0),
            new WeightedScorer(new ContainsScorer(), 5.0),
            new WeightedScorer(new MilestoneScorer(), 15.0),
            new WeightedScorer(new RequiredToolsScorer(), 10.0),
            new WeightedScorer(new ForbiddenToolsScorer(), 5.0),
            new WeightedScorer(new ToolEfficiencyScorer(), 5.0),
            new WeightedScorer(new ToolRecoveryScorer(), 5.0),
            new WeightedScorer(new PolicyComplianceScorer(), 10.0),
            new WeightedScorer(new SecretLeakScorer(), 5.0),
            new WeightedScorer(new LatencyBudgetScorer(), 5.0),
            new WeightedScorer(new TokenBudgetScorer(), 3.0),
            new WeightedScorer(new CostBudgetScorer(), 2.0),
            new WeightedScorer(new LoopTerminationScorer(), 5.0),
            new WeightedScorer(new ContextGroundingScorer(), 5.0),
            new WeightedScorer(new StepEfficiencyScorer(), 3.0),
            new WeightedScorer(new MeltdownScorer(), 3.0)));
    }

    public static Rubric of(Scorer... scorerList) {
        return new Rubric(java.util.Arrays.stream(scorerList)
            .map(s -> new WeightedScorer(s, s.defaultWeight() > 0 ? s.defaultWeight() : 1.0))
            .toList());
    }

    /**
     * Weighted average of the scores, normalized over the scorers actually
     * present. Returns 0 when no scores are available.
     */
    public double aggregate(List<Score> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0.0;
        }
        Map<String, Double> weights = new LinkedHashMap<>();
        for (WeightedScorer weighted : scorers) {
            weights.put(weighted.scorer().name(), weighted.weight());
        }
        double totalWeight = 0;
        double weightedSum = 0;
        for (Score score : scores) {
            if (!score.applicable()) {
                continue;
            }
            double weight = weights.getOrDefault(score.scorer(), 1.0);
            totalWeight += weight;
            weightedSum += weight * score.value();
        }
        return totalWeight == 0 ? 0.0 : weightedSum / totalWeight;
    }
}
