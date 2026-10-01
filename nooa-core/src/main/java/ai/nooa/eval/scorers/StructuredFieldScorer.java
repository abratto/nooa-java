package ai.nooa.eval.scorers;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.RunTrace;
import ai.nooa.eval.Score;
import ai.nooa.eval.Scorer;
import ai.nooa.eval.Values;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * Field-level correctness for typed outputs. When both the expected value and
 * the run output are records of the same type, every component is compared and
 * the score is the matched fraction. Non-record values fall back to exact
 * equality.
 */
public final class StructuredFieldScorer implements Scorer {

    @Override
    public String name() {
        return "StructuredField";
    }

    @Override
    public double defaultWeight() {
        return 25.0;
    }

    @Override
    public Score score(EvalCase evalCase, RunTrace trace) {
        Object expected = evalCase.expected();
        if (expected == null) {
            return Score.notApplicable(name(), "no expected value");
        }
        if (!trace.outputPresent()) {
            return Score.of(name(), 0.0, false, "no output (run failed)");
        }
        Object actual = trace.output();
        if (expected.getClass() != actual.getClass() || !expected.getClass().isRecord()) {
            boolean passed = Values.equal(actual, expected);
            return Score.of(name(), passed, passed
                ? "output matched"
                : "output does not match expected value");
        }

        RecordComponent[] components = expected.getClass().getRecordComponents();
        if (components.length == 0) {
            return Score.of(name(), 1.0, true, "empty record");
        }
        int matched = 0;
        List<String> mismatched = new ArrayList<>();
        for (RecordComponent component : components) {
            try {
                var accessor = component.getAccessor();
                accessor.setAccessible(true);
                Object expectedValue = accessor.invoke(expected);
                Object actualValue = accessor.invoke(actual);
                if (Values.equal(actualValue, expectedValue)) {
                    matched++;
                } else {
                    mismatched.add(component.getName());
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                mismatched.add(component.getName());
            }
        }
        double fraction = (double) matched / components.length;
        boolean passed = mismatched.isEmpty();
        String detail = passed ? "all fields matched"
            : "mismatched fields: " + String.join(", ", mismatched);
        return Score.of(name(), fraction, passed, detail);
    }
}
