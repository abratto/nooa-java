package ai.nooa.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for LLM code generation at runtime.
 *
 * <p>The method may use any synchronous return type supported by its
 * generation strategy. The runtime invokes it synchronously while the
 * LLM work runs on a virtual thread. Use {@link #prompt()} to provide
 * the runtime instruction; Java Javadoc is not available through
 * reflection.</p>
 *
 * <pre>{@code
 * &#64;Generate(prompt = "Greet the person warmly.")
 * public String greet(String name) {
 *     throw new UnsupportedOperationException("Generated at runtime");
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Generate {
	/** Runtime instruction sent with the generated method call. */
	String prompt() default "";
}
