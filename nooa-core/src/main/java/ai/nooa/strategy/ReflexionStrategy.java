package ai.nooa.strategy;

import ai.nooa.context.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

/**
 * Reflexion strategy: generate -> reflect -> improve loop.
 * Wraps a base strategy and iteratively critiques/improves output.
 */
public final class ReflexionStrategy implements GenerationStrategy {

    private static final ObjectMapper JSON = new ObjectMapper();

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
    public String name() {
        return "ReflexionStrategy";
    }

    @Override
    public Object execute(RuntimeServices runtime, CurrentCall call) {
        Object lastResult = null;
        ReflectionResult critique = null;

        for (int i = 0; i < maxIterations; i++) {
            try {
                if (critique != null) {
                    var feedback = "Previous result: " + lastResult
                        + "\n\nReasoning: " + critique.reasoning()
                        + "\n\nIssues: " + String.join("; ", critique.issues())
                        + "\n\nSuggestions: " + String.join("; ", critique.suggestions())
                        + "\n\nPlease improve the result based on this critique.";
                    runtime.eventManager().add(new Event.Feedback(feedback));
                }

                lastResult = baseStrategy.execute(runtime, call);

                if (i < maxIterations - 1) {
                    critique = reflect(runtime, lastResult, call);
                    if (critique.satisfactory()) {
                        return lastResult;
                    }
                }
            } catch (Exception e) {
                if (i >= maxIterations - 1) {
                    throw e;
                }
                runtime.eventManager().add(new Event.ErrorEvent(
                    "Attempt " + (i + 1) + " failed: " + e.getMessage()));
            }
        }

        return lastResult;
    }

    private ReflectionResult reflect(RuntimeServices runtime, Object result, CurrentCall call) {
        try {
            String reflectionPrompt = "Evaluate the result produced for the task below.\n"
                + "Return JSON with fields satisfactory, reasoning, issues, and suggestions.\n\n"
                + "Result to evaluate:\n" + String.valueOf(result);
            var response = runtime.generate(
                List.of(), ReflectionResult.class, Map.of("max_tokens", 500), reflectionPrompt);

            String content = response.content();
            if (content != null && content.strip().equalsIgnoreCase("OK")) {
                return new ReflectionResult(true, "", List.of(), List.of());
            }
            if (content != null && !content.isBlank()) {
                return JSON.readValue(content, ReflectionResult.class);
            }
            return new ReflectionResult(false, "Empty reflection",
                List.of("No reflection returned"), List.of());
        } catch (Exception e) {
            return new ReflectionResult(false, "Error during reflection: " + e.getMessage(),
                List.of("Reflection could not be parsed"), List.of("Retry the task"));
        }
    }
}
