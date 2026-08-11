package ai.nooa.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the system prompt for an agent class.
 * If absent, the class Javadoc comment is used as the system prompt.
 *
 * <p>Supports {@code {expression}} placeholders resolved against the
 * agent instance at generation time. {@code {type.xxx}} references the
 * class, and {@code {self.xxx}} references the instance.</p>
 *
 * <pre>{@code
 * &#64;SystemPrompt("You are {type.name}, a support agent.")
 * class SupportAgent extends Agent { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SystemPrompt {
    String value();
}
