package ai.nooa.strategy;

import ai.nooa.context.Event;
import ai.nooa.llm.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reflexion strategy: generate → reflect → improve loop.
 * Wraps a base strategy and iteratively critiques/improves output.
 */
public final class ReflexionStrategy implements GenerationStrategy {

    private final GenerationStrategy baseStrategy;
    private final int maxIterations;

    public ReflexionStrategy(GenerationStrategy baseStrategy, int maxIterations) {
        this.baseStrategy = baseStrategy;
        this.maxIterations = maxIterations > 0 ? maxIterations : 3;
    }

    public ReflexionStrategy() {
        this(new CodeActStrategy(ai.nooa.config.CodeActConfig.defaults()), 3);
    }

    @Override
    public String name() { return "ReflexionStrategy"; }

    @Override
    public Object execute(RuntimeServices runtime, CurrentCall call) {
        Object lastResult = null;
        String critique = null;

        for (int i = 0; i < maxIterations; i++) {
            try {
                if (critique != null) {
                    var feedback = "Previous result: " + lastResult
                        + "\n\nCritique: " + critique
                        + "\n\nPlease improve the result based on this critique.";
                    runtime.eventManager().add(new Event.Feedback(feedback));
                }

                lastResult = baseStrategy.execute(runtime, call);

                if (i < maxIterations - 1) {
                    critique = reflect(runtime, lastResult, call);
                    if (critique == null || critique.isBlank()) {
                        return lastResult; // No critique → result is good enough
                    }
                }
            } catch (Exception e) {
                if (i >= maxIterations - 1) throw e;
                runtime.eventManager().add(new Event.ErrorEvent(
                    "Attempt " + (i + 1) + " failed: " + e.getMessage()));
            }
        }

        return lastResult;
    }

    private String reflect(RuntimeServices runtime, Object result, CurrentCall call) {
        try {
            var response = runtime.generate(
                List.of(), null, Map.of("max_tokens", 500));

            String content = response.content();
            if (content != null && content.strip().equalsIgnoreCase("OK")) {
                return null;
            }
            return content;
        } catch (Exception e) {
            return "Error during reflection: " + e.getMessage();
        }
    }
}
