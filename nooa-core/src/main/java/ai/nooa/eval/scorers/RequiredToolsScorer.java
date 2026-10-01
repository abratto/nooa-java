package ai.nooa.eval.scorers;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;
import ai.nooa.eval.Score;
import ai.nooa.eval.Scorer;
import ai.nooa.eval.Values;

import java.util.ArrayList;
import java.util.List;

/**
 * Passes when every tool named in the case metadata {@code requiredTools} was
 * invoked at least once. Cases without the metadata are not applicable.
 */
public final class RequiredToolsScorer implements Scorer {

    @Override
    public String name() {
        return "RequiredTools";
    }

    @Override
    public double defaultWeight() {
        return 10.0;
    }

    @Override
    public Score score(EvalCase evalCase, RunTrace trace) {
        List<String> required = Values.stringList(evalCase.metadata(), "requiredTools");
        if (required.isEmpty()) {
            return Score.notApplicable(name(), "no requiredTools metadata");
        }
        List<String> called = trace.executedToolNames();
        List<String> missing = new ArrayList<>();
        for (String tool : required) {
            if (!called.contains(tool)) {
                missing.add(tool);
            }
        }
        double fraction = (double) (required.size() - missing.size()) / required.size();
        boolean passed = missing.isEmpty();
        return Score.of(name(), fraction, passed, passed
            ? "all required tools called"
            : "missing required tools: " + String.join(", ", missing));
    }
}
