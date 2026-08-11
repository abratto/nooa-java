package ai.nooa.agentdoc;

import ai.nooa.Agent;
import ai.nooa.annotations.Hidden;
import ai.nooa.annotations.NoTrace;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates structured documentation for an agent class, including
 * visible methods, fields, and the system prompt.
 *
 * <p>Used internally by context blocks and made available to generated
 * code via {@code doc(self)}.</p>
 */
public final class AgentDoc {

    private AgentDoc() {}

    /**
     * Returns a structured description of the agent's API: methods,
     * fields, and the system prompt.
     */
    public static String of(Agent agent) {
        return of(agent.getClass());
    }

    public static String of(Class<?> agentClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("class ").append(agentClass.getSimpleName());

        Class<?> superClass = agentClass.getSuperclass();
        if (superClass != null && superClass != Object.class && superClass != Agent.class) {
            sb.append(" extends ").append(superClass.getSimpleName());
        }
        sb.append(":\n");

        // Methods
        List<Method> methods = visibleMethods(agentClass);
        if (!methods.isEmpty()) {
            sb.append("\n  Methods:\n");
            for (Method m : methods) {
                sb.append("    ");
                if (m.isAnnotationPresent(ai.nooa.annotations.Generate.class)) {
                    sb.append("[generated] ");
                }
                sb.append(m.getReturnType().getSimpleName())
                  .append(" ").append(m.getName()).append("(");
                var params = m.getParameters();
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(params[i].getType().getSimpleName())
                      .append(" ").append(params[i].getName());
                }
                sb.append(")");
                String docs = javadocSummary(m);
                if (!docs.isEmpty()) {
                    sb.append("  — ").append(docs);
                }
                sb.append("\n");
            }
        }

        // Fields
        List<Field> fields = visibleFields(agentClass);
        if (!fields.isEmpty()) {
            sb.append("\n  Fields:\n");
            for (Field f : fields) {
                sb.append("    ").append(f.getType().getSimpleName())
                  .append(" ").append(f.getName()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Returns visible instance field values for the "state" context block.
     */
    public static String instanceValues(Agent agent) {
        StringBuilder sb = new StringBuilder();
        List<Field> fields = visibleFields(agent.getClass());
        for (Field f : fields) {
            try {
                f.setAccessible(true);
                Object value = f.get(agent);
                sb.append(f.getName()).append(" = ").append(truncate(value)).append("\n");
            } catch (IllegalAccessException e) {
                sb.append(f.getName()).append(" = <inaccessible>\n");
            }
        }
        return sb.toString().stripTrailing();
    }

    public static List<Method> visibleMethods(Class<?> agentClass) {
        List<Method> result = new ArrayList<>();
        for (Method m : agentClass.getMethods()) {
            if (m.getDeclaringClass() == Object.class) { continue; }
            if (m.isAnnotationPresent(Hidden.class)) { continue; }
            if (m.isAnnotationPresent(NoTrace.class) && isPrivateLike(m)) { continue; }
            if (isPrivateLike(m) && !isExplicitlyVisible(m)) { continue; }
            if (isFrameworkMethod(m)) { continue; }
            result.add(m);
        }
        return result;
    }

    public static List<Field> visibleFields(Class<?> agentClass) {
        List<Field> result = new ArrayList<>();
        for (Field f : agentClass.getDeclaredFields()) {
            if (f.isAnnotationPresent(Hidden.class)) { continue; }
            if (Modifier.isStatic(f.getModifiers())) { continue; }
            result.add(f);
        }
        return result;
    }

    private static boolean isPrivateLike(Method m) {
        String name = m.getName();
        return name.startsWith("_") || name.startsWith("$");
    }

    private static boolean isExplicitlyVisible(Method m) {
        return false; // No Java equivalent of @spec(hidden=False) yet
    }

    private static boolean isFrameworkMethod(Method m) {
        return m.getDeclaringClass() == Agent.class
            || m.getDeclaringClass() == Object.class;
    }

    private static String javadocSummary(Method m) {
        String docs = ai.nooa.strategy.MethodDocStore.get(m);
        if (docs == null || docs.isBlank()) { return ""; }
        int newline = docs.indexOf('\n');
        return newline > 0 ? docs.substring(0, newline).strip() : docs.strip();
    }

    private static String truncate(Object value) {
        if (value == null) { return "null"; }
        String s = value.toString();
        if (s.length() > 80) {
            return s.substring(0, 77) + "...";
        }
        return s;
    }
}
