package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.strategy.PredictStrategy;
import ai.nooa.strategy.ReflexionStrategy;

import java.util.List;

/** Typed result of a CodeAct-solved math problem. */
record MathSolution(String answer, List<String> steps) {}

/** Typed classification produced by {@link PredictStrategy}. */
record MathClassification(String type, String difficulty) {}

/**
 * The three built-in strategies side by side on the same problem.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>Default {@code CodeActStrategy} ({@link #solveWithCode}) — the model
 *       iterates over {@code executeJava}/{@code returnResult} tool calls, can
 *       compute in the sandbox, and the final value is converted to the
 *       declared {@link MathSolution} record.</li>
 *   <li>{@code @Strategy(PredictStrategy.class)} ({@link #classifyProblem}) —
 *       one focused model call whose JSON is validated against
 *       {@link MathClassification}.</li>
 *   <li>{@code @Strategy(ReflexionStrategy.class)} ({@link #solveWithReflection})
 *       — a draft-critique-revise loop wrapping CodeAct: the base strategy
 *       produces a solution, a reflection call scores it, and the loop repeats
 *       until the critique is satisfactory or iterations run out.</li>
 *   <li>Per-turn sampling overrides — {@link StrategyComparisonDemo} adjusts
 *       {@code reasoning_effort} between the three calls (low for direct
 *       solving, medium for classification, high for self-critique),
 *       demonstrating {@link Agent#samplingOverride(String, Object)} as a
 *       per-stage control.</li>
 * </ul>
 *
 * <p><b>Run</b> via {@link StrategyComparisonDemo}:
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.StrategyComparisonDemo
 * }</pre>
 */
public class StrategyDemoAgent extends Agent {
    public StrategyDemoAgent(UnifiedLLM llm) {
        super(llm);
        // Sensible default for the heaviest method here (reflexion); the demo
        // lowers the effort for the cheaper methods per turn.
        ExampleLLM.tune(this, "high", 4096);
    }

    @Generate(prompt = """
        Solve the math problem numerically. You may compute inside executeJava,
        then call returnResult with a JSON object with exactly two fields:
        {"answer": "<the numeric answer>", "steps": ["step one", "step two"]}
        This is a solve task, not a classification task — never return type or
        difficulty fields.
        """)
    public MathSolution solveWithCode(String problem) {
        throw new UnsupportedOperationException();
    }

    @Generate(prompt = """
        Classify the math problem by type and difficulty. Call returnResult with
        a JSON object with exactly two fields:
        {"type": "<arithmetic|algebra|geometry|...>", "difficulty": "<easy|medium|hard>"}
        This is a classification task — do not solve the problem.
        """)
    @Strategy(PredictStrategy.class)
    public MathClassification classifyProblem(String problem) {
        throw new UnsupportedOperationException();
    }

    @Generate(prompt = """
        Solve the math problem, review your solution for mistakes, and return
        the corrected result. Call returnResult with a JSON object with exactly
        two fields:
        {"answer": "<the numeric answer>", "steps": ["step one", "step two"]}
        """)
    @Strategy(ReflexionStrategy.class)
    public MathSolution solveWithReflection(String problem) {
        throw new UnsupportedOperationException();
    }
}
