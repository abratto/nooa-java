package ai.nooa.eval.scorers;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;
import ai.nooa.eval.Score;
import ai.nooa.eval.Scorer;
import ai.nooa.eval.Values;

/**
 * Detects behavioural collapse ("meltdown") via the Shannon entropy of the
 * tool-call sequence captured by {@link RunRecorder}. High entropy indicates
 * repetitive, unfocused tool use. Scores against the case metadata
 * {@code maxToolEntropy}; not applicable without it.
 */
public final class MeltdownScorer implements Scorer {

    @Override
    public String name() {
        return "Meltdown";
    }

    @Override
    public double defaultWeight() {
        return 5.0;
    }

    @Override
    public Score score(EvalCase evalCase, RunTrace trace) {
        Double maxEntropy = Values.number(evalCase.metadata(), "maxToolEntropy");
        if (maxEntropy == null || maxEntropy <= 0) {
            return Score.notApplicable(name(), "no maxToolEntropy metadata");
        }
        Object signal = trace.signals().get("toolEntropy");
        double entropy = signal instanceof Number n ? n.doubleValue() : 0.0;
        boolean passed = entropy <= maxEntropy;
        return Score.of(name(), passed, passed
            ? String.format(java.util.Locale.ROOT, "tool entropy %.2f within budget", entropy)
            : String.format(java.util.Locale.ROOT,
                "tool entropy %.2f exceeds %.2f", entropy, maxEntropy));
    }
}
