package ai.nooa.examples;

import ai.nooa.AgentFactory;

/** Runs the three strategy variants defined by {@link StrategyDemoAgent}. */
public final class StrategyComparisonDemo {
    private StrategyComparisonDemo() {}

    public static void main(String[] args) {
        var llm = ExampleLLM.create();
        var agent = AgentFactory.create(StrategyDemoAgent.class, llm);
        try {
            System.out.println(agent.solveWithCode("Solve 12 * 7"));
            System.out.println(agent.classifyProblem("Solve 12 * 7"));
            System.out.println(agent.solveWithReflection("Solve 12 * 7"));
        } finally {
            agent.close();
        }
    }
}