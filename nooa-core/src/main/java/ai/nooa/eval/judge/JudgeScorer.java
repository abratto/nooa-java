package ai.nooa.eval.judge;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;
import ai.nooa.eval.Score;
import ai.nooa.eval.Scorer;
import ai.nooa.eval.Values;

import java.util.List;

/**
 * Adapts a {@link Judge} to the {@link Scorer} SPI so judged dimensions can
 * participate in a {@link ai.nooa.eval.Rubric}. Criteria are read from the case
 * metadata key {@code judgeCriteria}.
 */
public final class JudgeScorer implements Scorer {

    private final Judge judge;
    private final String name;

    public JudgeScorer(Judge judge) {
        this(judge, "Judge");
    }

    public JudgeScorer(Judge judge, String name) {
        this.judge = java.util.Objects.requireNonNull(judge, "judge");
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public double defaultWeight() {
        return 10.0;
    }

    @Override
    public Score score(EvalCase evalCase, RunTrace trace) {
        if (!trace.outputPresent()) {
            return Score.of(name(), 0.0, false, "no output (run failed)");
        }
        List<String> criteria = Values.stringList(evalCase.metadata(), "judgeCriteria");
        Judge.Verdict verdict = judge.judge(evalCase, trace, criteria);
        return Score.of(name(), verdict.score(), verdict.passed(), verdict.rationale());
    }
}
