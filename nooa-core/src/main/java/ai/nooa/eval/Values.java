package ai.nooa.eval;

import java.util.Map;
import java.util.Objects;

/** Shared value-comparison helpers for scorers and goal verification. */
public final class Values {

    private Values() {}

    /**
     * Deterministic equality: strings compare case-sensitively after trimming;
     * numbers compare numerically; everything else uses {@link Objects#equals}.
     */
    public static boolean equal(Object actual, Object expected) {
        if (actual instanceof String a && expected instanceof String e) {
            return a.strip().equals(e.strip());
        }
        if (actual instanceof Number a && expected instanceof Number e) {
            return Double.compare(a.doubleValue(), e.doubleValue()) == 0;
        }
        return Objects.equals(actual, expected);
    }

    /** Read a metadata value as a list of strings; empty when absent. */
    @SuppressWarnings("unchecked")
    public static java.util.List<String> stringList(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof java.util.Collection<?> collection) {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (Object item : collection) {
                out.add(String.valueOf(item));
            }
            return out;
        }
        if (value instanceof String s) {
            return java.util.List.of(s);
        }
        return java.util.List.of();
    }

    /** Read a metadata value as a number, or {@code null} when absent/invalid. */
    public static Double number(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.strip());
            } catch (NumberFormatException _) {
                return null;
            }
        }
        return null;
    }

    /** Read a metadata value as a boolean, or {@code null} when absent. */
    public static Boolean bool(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s.strip());
        }
        return null;
    }
}
