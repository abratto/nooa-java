package ai.nooa.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for LLM code generation at runtime.
 *
 * <p>The returned {@code CompletableFuture} resolves when the LLM
 * completes the method body. The method's Javadoc comment serves
 * as the prompt.</p>
 *
 * <pre>{@code
 * &#64;Generate
 * public CompletableFuture<String> greet(String name) {
 *     throw new UnsupportedOperationException("Generated at runtime");
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Generate {
}
