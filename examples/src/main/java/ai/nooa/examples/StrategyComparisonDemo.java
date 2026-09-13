package ai.nooa.examples;

import ai.nooa.AgentFactory;

/**
 * Runs the three strategy variants defined by {@link StrategyDemoAgent} against
 * one problem, adjusting the reasoning effort per stage.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>CodeAct, Predict, and Reflexion on identical input, so their
 *       behaviour and cost can be compared directly.</li>
 *   <li>Per-turn sampling overrides — {@code agent.samplingOverride(...)} is
 *       changed between calls to match each task's needs: medium for
 *       producing typed records, low for simple classification, high for the
 *       self-critique loop.
 *       The runtime merges these overrides into every LLM request, whichever
 *       strategy is active.</li>
 * </ul>
 *
 * <p><b>Run</b>:
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.StrategyComparisonDemo
 * }</pre>
 * or {@code examples/run.sh StrategyComparisonDemo}. This is the most
 * model-intensive demo (up to three strategies plus reflection iterations);
 * give it a generous timeout on local models.</p>
 */
public final class StrategyComparisonDemo {
    private StrategyComparisonDemo() {}

    public static void main(String[] args) {
        var llm = ExampleLLM.create();
        var agent = AgentFactory.create(StrategyDemoAgent.class, llm);
        try {
            // Effort follows the task, not the strategy: producing a typed
            // MathSolution record needs protocol discipline (medium), the
            // single-field classification is easy (low), and self-critique
            // benefits from the most deliberation (high).
            System.out.println("--- CodeAct (reasoning_effort=medium) ---");
            agent.samplingOverride("reasoning_effort", "medium");
            System.out.println(agent.solveWithCode("Solve 12 * 7"));

            System.out.println("--- Predict (reasoning_effort=low) ---");
            agent.samplingOverride("reasoning_effort", "low");
            System.out.println(agent.classifyProblem("Solve 12 * 7"));

            System.out.println("--- Reflexion (reasoning_effort=high) ---");
            agent.samplingOverride("reasoning_effort", "high");
            System.out.println(agent.solveWithReflection("Solve 12 * 7"));
        } finally {
            agent.close();
        }
    }
}
