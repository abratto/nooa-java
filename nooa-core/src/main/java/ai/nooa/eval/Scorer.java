package ai.nooa.eval;

/**
 * Scoring extension point. Implementations evaluate one dimension of a single
 * run captured as a {@link RunTrace}.
 *
 * <p>Scorers are pure functions: they must not perform I/O or mutate the
 * agent. The {@link ai.nooa.eval.judge.Judge} SPI is a scoring strategy for
 * dimensions that require model judgement.</p>
 */
public interface Scorer {

    /** Stable identifier used in reports and {@link Rubric} weighting. */
    String name();

    /** Evaluate one run. Implementations should never return {@code null}. */
    Score score(EvalCase evalCase, RunTrace trace);

    /** Default relative weight used by {@link Rubric#defaults()}. */
    default double defaultWeight() {
        return 0.0;
    }
}
