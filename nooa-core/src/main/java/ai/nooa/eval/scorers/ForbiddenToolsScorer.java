package ai.nooa.eval.scorers;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;
import ai.nooa.eval.Score;
import ai.nooa.eval.Scorer;
import ai.nooa.eval.Values;

import java.util.ArrayList;
import java.util.List;

/**
 * Passes when none of the tools named in the case metadata
 * {@code forbiddenTools} were invoked. Cases without the metadata are not
 * applicable.
 */
public final class ForbiddenToolsScorer implements Scorer {

    @Override
    public String name() {
        return "ForbiddenTools";
    }

    @Override
    public double defaultWeight() {
        return 5.0;
    }

    @Override
    public Score score(EvalCase evalCase, RunTrace trace) {
        List<String> forbidden = Values.stringList(evalCase.metadata(), "forbiddenTools");
        if (forbidden.isEmpty()) {
            return Score.notApplicable(name(), "no forbiddenTools metadata");
        }
        List<String> called = trace.executedToolNames();
        List<String> violations = new ArrayList<>();
        for (String tool : forbidden) {
            if (called.contains(tool)) {
                violations.add(tool);
            }
        }
        boolean passed = violations.isEmpty();
        return Score.of(name(), passed, passed
            ? "no forbidden tools called"
            : "forbidden tools called: " + String.join(", ", violations));
    }
}
