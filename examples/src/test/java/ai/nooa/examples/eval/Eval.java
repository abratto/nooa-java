package ai.nooa.examples.eval;

import ai.nooa.Agent;
import ai.nooa.eval.Mode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an evaluation for a JUnit 5 test method. Pair with
 * {@link EvalExtension} (via {@code @ExtendWith}) and an
 * {@code EvalReport} parameter.
 *
 * <pre>{@code
 * @Test
 * @ExtendWith(EvalExtension.class)
 * @Eval(agent = MyAgent.class, dataset = "src/test/resources/eval/smoke.jsonl",
 *       minWeighted = 0.8)
 * void meetsBar(EvalReport report) { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Eval {

    Class<? extends Agent> agent();

    String dataset();

    int trials() default 1;

    Mode mode() default Mode.LIVE;

    /** Fail the test when the weighted mean is below this; 0 disables. */
    double minWeighted() default 0.0;

    /** Optional hard-gate scorer name checked independently of the weighted mean. */
    String gate() default "";

    double gateMin() default 0.0;
}
