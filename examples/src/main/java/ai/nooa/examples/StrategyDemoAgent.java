package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.strategy.PredictStrategy;
import ai.nooa.strategy.ReflexionStrategy;

import java.util.List;

record MathSolution(String answer, List<String> steps) {}
record MathClassification(String type, String difficulty) {}

public class StrategyDemoAgent extends Agent {
    public StrategyDemoAgent(UnifiedLLM llm) { super(llm); }

    @Generate
    public MathSolution solveWithCode(String problem) {
        throw new UnsupportedOperationException();
    }

    @Generate @Strategy(PredictStrategy.class)
    public MathClassification classifyProblem(String problem) {
        throw new UnsupportedOperationException();
    }

    @Generate @Strategy(ReflexionStrategy.class)
    public MathSolution solveWithReflection(String problem) {
        throw new UnsupportedOperationException();
    }
}
