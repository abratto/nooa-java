package ai.nooa.eval.judge;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Deterministic {@link Judge} for tests: returns scripted verdicts in order,
 * then repeats the last verdict (or a fixed default).
 */
public final class FakeJudge implements Judge {

    private final Deque<Verdict> verdicts = new ArrayDeque<>();
    private Verdict last;

    public FakeJudge(Verdict... scripted) {
        for (Verdict verdict : scripted) {
            verdicts.add(verdict);
        }
    }

    public FakeJudge(double defaultScore, String rationale) {
        this.last = new Verdict(defaultScore, rationale);
    }

    @Override
    public Verdict judge(EvalCase evalCase, RunTrace trace, List<String> criteria) {
        Verdict next = verdicts.poll();
        if (next != null) {
            last = next;
        }
        if (last == null) {
            throw new IllegalStateException("FakeJudge has no verdicts configured");
        }
        return last;
    }
}
