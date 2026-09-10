package ai.nooa.annotations;

import ai.nooa.strategy.GenerationStrategy;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the default generation strategy for a {@code @Generate} method.
 * The strategy class must provide an accessible no-argument constructor;
 * configure the agent-level default strategy when constructor arguments
 * are required.
 *
 * <pre>{@code
 * &#64;Generate
 * &#64;Strategy(PredictStrategy.class)
 * public CompletableFuture<TicketKind> classify(String message) {
 *     throw new UnsupportedOperationException("Generated at runtime");
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Strategy {
    Class<? extends GenerationStrategy> value();
}
