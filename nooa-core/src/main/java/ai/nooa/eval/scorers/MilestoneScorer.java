package ai.nooa.eval.scorers;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;
import ai.nooa.eval.Score;
import ai.nooa.eval.Scorer;
import ai.nooa.eval.Values;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Partial-credit scoring against a case's weighted {@code milestones}.
 *
 * <p>Milestones are evaluated against the post-run state fields when present
 * (recorded by {@code EvalRunner} under the {@code postState} signal), falling
 * back to the run output when it is a {@code Map}. The score is the weighted
 * fraction of satisfied milestones; the case passes when all are satisfied.</p>
 */
public final class MilestoneScorer implements Scorer {

    @Override
    public String name() {
        return "Milestone";
    }

    @Override
    public double defaultWeight() {
        return 15.0;
    }

    @Override
    public Score score(EvalCase evalCase, RunTrace trace) {
        if (evalCase.milestones().isEmpty()) {
            return Score.notApplicable(name(), "no milestones");
        }
        Map<String, Object> state = stateOf(trace);
        double totalWeight = 0;
        double achievedWeight = 0;
        List<String> unmet = new ArrayList<>();
        for (EvalCase.Milestone milestone : evalCase.milestones()) {
            double weight = milestone.weight() <= 0 ? 1.0 : milestone.weight();
            totalWeight += weight;
            if (milestoneMet(milestone, state)) {
                achievedWeight += weight;
            } else {
                unmet.add(milestone.name());
            }
        }
        double fraction = totalWeight == 0 ? 1.0 : achievedWeight / totalWeight;
        boolean passed = unmet.isEmpty();
        String detail = passed ? "all milestones met"
            : "unmet milestones: " + String.join(", ", unmet);
        return Score.of(name(), fraction, passed, detail);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stateOf(RunTrace trace) {
        Object postState = trace.signals().get("postState");
        if (postState instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (trace.output() instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static boolean milestoneMet(EvalCase.Milestone milestone, Map<String, Object> state) {
        if (milestone.expected().isEmpty()) {
            return true;
        }
        for (var entry : milestone.expected().entrySet()) {
            if (!Values.equal(state.get(entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }
}
