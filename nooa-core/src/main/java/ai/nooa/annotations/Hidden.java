package ai.nooa.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Hides a method or field from the LLM's API documentation ({@code AgentDoc}).
 * Public members are visible by default; annotate with {@code @Hidden} to
 * exclude them from the agent's generated documentation.
 *
 * <pre>{@code
 * &#64;Hidden
 * private String apiKey;
 *
 * &#64;Hidden
 * void rebuildIndex() { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface Hidden {
}
