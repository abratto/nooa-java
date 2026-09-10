package ai.nooa.strategy;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

/**
 * Captures the context of a single {@code @Generate} method invocation:
 * the method, its arguments, return type, and the runtime prompt.
 */
public final class CurrentCall {

    private final Method method;
    private final UUID callId;
    private final Object[] args;
    private final Map<String, Object> namedArgs;
    private final Type genericReturnType;
    private final Class<?> returnType;
    private final String docstring;

    private CurrentCall(Method method, Object[] args, Map<String, Object> namedArgs) {
        this.method = method;
        this.callId = UUID.randomUUID();
        this.args = args.clone();
        this.namedArgs = namedArgs;
        this.genericReturnType = unwrapCompletableFuture(method);
        this.returnType = rawType(genericReturnType);
        this.docstring = extractDocstring(method);
    }

    /**
     * If the method return type is CompletableFuture&lt;T&gt;, extract T.
     * Otherwise return the raw return type.
     */
    private static Type unwrapCompletableFuture(Method method) {
        Class<?> raw = method.getReturnType();
        if (!CompletableFuture.class.isAssignableFrom(raw)) {
            return method.getGenericReturnType();
        }
        Type generic = method.getGenericReturnType();
        if (generic instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length == 1) {
                return args[0];
            }
        }
        return Object.class;
    }

    private static Class<?> rawType(Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) {
            return c;
        }
        return Object.class;
    }

    public static CurrentCall fromMethod(Method method, Object[] args) {
        Objects.requireNonNull(method);
        Parameter[] params = method.getParameters();
        Map<String, Object> named = new LinkedHashMap<>();
        if (args != null && params.length == args.length) {
            for (int i = 0; i < params.length; i++) {
                named.put(params[i].getName(), args[i]);
            }
        }
        return new CurrentCall(method, args != null ? args : new Object[0], named);
    }

    public Method method() { return method; }
    public UUID callId() { return callId; }
    public Object[] args() { return args.clone(); }
    public Map<String, Object> namedArgs() { return namedArgs; }
    public Class<?> returnType() { return returnType; }
    public Type genericReturnType() { return genericReturnType; }
    public String docstring() { return docstring; }

    public String userPrompt(boolean includeArgs, int maxArgChars) {
        StringBuilder builder = new StringBuilder(docstring);
        if (includeArgs && !namedArgs.isEmpty()) {
            builder.append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("Inputs:");
            for (var entry : namedArgs.entrySet()) {
                builder.append(System.lineSeparator())
                    .append("- ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(renderValue(entry.getValue(), maxArgChars));
            }
        }
        return builder.toString();
    }

    private static String renderValue(Object value, int maxArgChars) {
        if (value == null) return "null";
        String text = String.valueOf(value);
        if (text.length() > maxArgChars) {
            text = text.substring(0, Math.max(0, maxArgChars)).trim() + "...";
        }
        return text;
    }

    private static String extractDocstring(Method method) {
        // Java Javadoc is not available at runtime through standard reflection.
        // AgentFactory stores Generate.prompt; older methods without an
        // explicit prompt fall back to their method name and parameters.
        String docs = MethodDocStore.get(method);
        if (docs != null && !docs.isBlank()) {
            return docs.strip();
        }
        return "Execute " + method.getDeclaringClass().getSimpleName()
            + "." + method.getName()
            + "(" + describeParams(method) + ")";
    }

    private static String describeParams(Method method) {
        return Arrays.stream(method.getParameters())
            .map(p -> p.getType().getSimpleName() + " " + p.getName())
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }
}
