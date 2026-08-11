package ai.nooa.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a method out of tracing. Tracing is enabled by default on all
 * agent methods (both {@code @Generate} and regular). Apply this to
 * suppress trace spans.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NoTrace {
}
