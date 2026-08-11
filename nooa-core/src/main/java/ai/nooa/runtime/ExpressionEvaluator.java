package ai.nooa.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates {@code {expression}} placeholders in template strings
 * using reflective method calls and field access.
 *
 * <p>Supports:
 * <ul>
 *   <li>{@code {self.field}} — field access on the agent instance</li>
 *   <li>{@code {self.method()}} — no-arg method call on agent</li>
 *   <li>{@code {self.method().field}} — chained access</li>
 *   <li>{@code {type.name}} — class-level name resolution</li>
 *   <li>{@code {Type.method(self)}} — static method with agent arg</li>
 *   <li>{@code {type(self)}} — resolves to the agent's Class object</li>
 *   <li>{@code {key}} — direct variable map lookup</li>
 * </ul>
 */
final class ExpressionEvaluator {

    private static final Pattern EXPR = Pattern.compile("\\{([^}]+)}");

    static String evaluate(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) { return ""; }
        if (!template.contains("{")) { return template; }

        Matcher m = EXPR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String expr = m.group(1).strip();
            Object result = resolve(expr, variables);
            String replacement = result != null ? result.toString() : "null";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolve an expression to an Object value (not just String).
     * This enables chained evaluation (method returns object, then .field on it).
     */
    static Object resolve(String expr, Map<String, Object> vars) {
        if (expr.contains("(")) {
            return resolveMethodCall(expr, vars);
        }
        return resolveFieldPath(expr, vars);
    }

    // ---- Method call resolution ----

    private static Object resolveMethodCall(String expr, Map<String, Object> vars) {
        // Parse: Receiver.methodName(arg1, arg2, ...)
        int parenOpen = expr.indexOf('(');
        int parenClose = expr.lastIndexOf(')');
        if (parenOpen < 0 || parenClose < 0 || parenClose < parenOpen) {
            return resolveFieldPath(expr, vars);
        }

        // Split into: "Receiver.methodName" and "arg1, arg2, ..."
        String beforeParen = expr.substring(0, parenOpen);
        String argsStr = expr.substring(parenOpen + 1, parenClose).strip();
        String remainder = expr.substring(parenClose + 1); // e.g., ".field" after method

        int lastDot = beforeParen.lastIndexOf('.');
        if (lastDot < 0) {
                // Function-style: `type(self)` — returns arg's Class
                if ("type".equals(beforeParen)) {
                    Object arg = resolveArg(argsStr, vars);
                    if (arg != null) {
                        return arg.getClass();
                    }
                    return "{" + expr + "}";
                }
            return "{" + expr + "}";
        }

        String receiverExpr = beforeParen.substring(0, lastDot);
        String methodName = beforeParen.substring(lastDot + 1);

        Object receiver;
        if (receiverExpr.startsWith("self")) {
            String fieldPath = receiverExpr.substring(4); // after "self"
            receiver = vars.get("self");
            if (!fieldPath.isEmpty() && fieldPath.startsWith(".")) {
                receiver = resolveFieldPathOn(receiver, fieldPath.substring(1));
            }
        } else if (receiverExpr.startsWith("type")) {
            // "type" is always vars.get("type")
            String fieldPath = receiverExpr.substring(4);
            receiver = vars.get("type");
            if (!fieldPath.isEmpty() && fieldPath.startsWith(".")) {
                receiver = resolveFieldPathOn(receiver, fieldPath.substring(1));
            }
        } else {
            // Static class name: "AgentDoc.of(...)"
            try {
                var cls = Class.forName(receiverExpr);
                Object[] args = resolveArgs(argsStr, vars);
                Object result = invokeStatic(cls, methodName, args);
                if (result != null && !remainder.isEmpty() && remainder.startsWith(".")) {
                    result = resolveFieldPathOn(result, remainder.substring(1));
                }
                return result;
            } catch (ClassNotFoundException e) {
                return "{" + expr + "}";
            }
        }

        // Resolve args and invoke
        Object[] args = resolveArgs(argsStr, vars);
        try {
            Object result = invoke(receiver, methodName, args);
            if (result != null && !remainder.isEmpty() && remainder.startsWith(".")) {
                result = resolveFieldPathOn(result, remainder.substring(1));
            }
            return result;
        } catch (Exception e) {
            return "{" + expr + "}";
        }
    }

    private static Object invokeStatic(Class<?> cls, String methodName, Object[] args) {
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(methodName)
                && Modifier.isStatic(m.getModifiers())
                && argsMatch(m.getParameterTypes(), args)) {
                try {
                    m.setAccessible(true);
                    return m.invoke(null, args);
                } catch (Exception e) {
                    break;
                }
            }
        }
        return null;
    }

    private static Object invoke(Object receiver, String methodName, Object[] args) {
        if (receiver == null) { return null; }
        for (Method m : receiver.getClass().getMethods()) {
            if (m.getName().equals(methodName) && argsMatch(m.getParameterTypes(), args)) {
                try {
                    m.setAccessible(true);
                    return m.invoke(receiver, args);
                } catch (Exception e) {
                    break;
                }
            }
        }
        return null;
    }

    private static boolean argsMatch(Class<?>[] paramTypes, Object[] args) {
        if (paramTypes.length != args.length) { return false; }
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) { continue; }
            Class<?> argClass = args[i].getClass();
            if (paramTypes[i] == Object.class) { continue; }
            if (paramTypes[i] == argClass) { continue; }
            // Check if arg is a subclass, or boxed equivalent
            if (paramTypes[i].isAssignableFrom(argClass)) { continue; }
            if (paramTypes[i].isPrimitive()) {
                var boxed = boxedType(paramTypes[i]);
                if (boxed != null && boxed.isAssignableFrom(argClass)) { continue; }
            }
            return false;
        }
        return true;
    }

    private static Class<?> boxedType(Class<?> primitive) {
        if (primitive == int.class) { return Integer.class; }
        if (primitive == long.class) { return Long.class; }
        if (primitive == double.class) { return Double.class; }
        if (primitive == float.class) { return Float.class; }
        if (primitive == boolean.class) { return Boolean.class; }
        if (primitive == char.class) { return Character.class; }
        if (primitive == short.class) { return Short.class; }
        if (primitive == byte.class) { return Byte.class; }
        return null;
    }

    private static Object resolveArg(String argStr, Map<String, Object> vars) {
        if (argStr.contains("(")) {
            return resolveMethodCall(argStr, vars);
        }
        return resolveFieldPath(argStr, vars);
    }

    private static Object[] resolveArgs(String argsStr, Map<String, Object> vars) {
        if (argsStr.isEmpty()) { return new Object[0]; }
        // Split on top-level commas (not inside parens)
        String[] parts = splitTopLevel(argsStr);
        Object[] result = new Object[parts.length];
        for (int i = 0; i < parts.length; i++) {
            Object resolved = resolve(parts[i].strip(), vars);
            result[i] = resolved;
        }
        return result;
    }

    private static String[] splitTopLevel(String s) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') { depth++; }
            else if (c == ')') { depth--; }
            else if (c == ',' && depth == 0) {
                parts.add(s.substring(start, i).strip());
                start = i + 1;
            }
        }
        parts.add(s.substring(start).strip());
        return parts.toArray(new String[0]);
    }

    // ---- Field path resolution ----

    private static Object resolveFieldPath(String expr, Map<String, Object> vars) {
        if (expr.startsWith("self")) {
            Object self = vars.get("self");
            if (self == null) { return "null"; }
            String path = expr.substring(4); // after "self"
            if (path.isEmpty()) { return self; }
            if (path.startsWith(".")) {
                try {
                    return resolveFieldPathOn(self, path.substring(1));
                } catch (ResolutionException e) {
                    return "{" + expr + "}";
                }
            }
        }
        if (expr.startsWith("type")) {
            Object type = vars.get("type");
            if (type == null) { return "null"; }
            String path = expr.substring(4);
            if (path.isEmpty()) { return type; }
            if (path.startsWith(".")) {
                try {
                    if (type instanceof Class<?> cls) {
                        return resolveFieldPathOn(cls, path.substring(1));
                    }
                    return resolveFieldPathOn(type, path.substring(1));
                } catch (ResolutionException e) {
                    return "{" + expr + "}";
                }
            }
        }
        if (vars.containsKey(expr)) {
            return vars.get(expr);
        }
        return "{" + expr + "}";
    }

    private static Object resolveFieldPathOn(Object obj, String path) {
        if (obj == null || path.isEmpty()) { return obj; }

        int dot = path.indexOf('.');
        String segment = dot > 0 ? path.substring(0, dot) : path;
        String rest = dot > 0 ? path.substring(dot + 1) : "";

        if (obj instanceof Class<?> cls) {
            Object value = switch (segment) {
                case "name" -> cls.getSimpleName();
                case "canonicalName" -> cls.getCanonicalName();
                case "simpleName" -> cls.getSimpleName();
                default -> tryGetOr(cls, segment);
            };
            if (rest.isEmpty()) { return value; }
            return resolveFieldPathOn(value, rest);
        }

        Object value = tryGetOr(obj, segment);
        if (rest.isEmpty()) { return value; }
        if (value == null) { return null; }
        return resolveFieldPathOn(value, rest);
    }

    private static Object tryGetOr(Object obj, String name) {
        Object value = tryGetter(obj, name);
        if (value != null) { return value; }
        value = tryField(obj, name);
        if (value instanceof String s && s.startsWith("{")) {
            throw new ResolutionException(name);
        }
        return value;
    }

    private static Object tryGetter(Object obj, String name) {
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            Method m = obj.getClass().getMethod(getter);
            return m.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object tryField(Object obj, String name) {
        try {
            Field f = obj.getClass().getField(name);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            return "{" + name + "}";
        }
    }

    private static class ResolutionException extends RuntimeException {
        ResolutionException(String msg) { super(msg); }
    }
}
