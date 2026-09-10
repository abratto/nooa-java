package ai.nooa.strategy;

import java.util.List;

/** Structured result returned by the Reflexion critic. */
public record ReflectionResult(
    boolean satisfactory,
    String reasoning,
    List<String> issues,
    List<String> suggestions
) {
    public ReflectionResult {
        reasoning = reasoning != null ? reasoning : "";
        issues = issues != null ? List.copyOf(issues) : List.of();
        suggestions = suggestions != null ? List.copyOf(suggestions) : List.of();
    }
}
