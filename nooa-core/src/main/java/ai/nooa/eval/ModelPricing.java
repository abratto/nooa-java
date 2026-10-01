package ai.nooa.eval;

import java.util.Map;
import java.util.Optional;

/**
 * Token pricing used to convert usage into USD cost.
 *
 * <p>Overrides are programmatic via {@link #with(String, double, double)} and
 * environment-based via {@code NOOA_PRICE_<MODEL>=<promptPer1k>/<completionPer1k>},
 * where {@code <MODEL>} is upper-cased with non-alphanumeric characters
 * replaced by {@code _} (e.g. {@code NOOA_PRICE_GPT_4O=0.0025/0.01}).</p>
 */
public final class ModelPricing {

    private final Map<String, double[]> table;

    private ModelPricing(Map<String, double[]> table) {
        this.table = table;
    }

    /** A small default table for common hosted models; local models are free. */
    public static ModelPricing standard() {
        return of()
            .with("gpt-4o", 0.0025, 0.01)
            .with("gpt-4o-mini", 0.00015, 0.0006)
            .with("gpt-4.1", 0.002, 0.008)
            .with("gpt-4.1-mini", 0.0004, 0.0016)
            .with("o3", 0.002, 0.008)
            .with("claude-3-7-sonnet", 0.003, 0.015)
            .with("claude-3-5-sonnet", 0.003, 0.015)
            .with("claude-3-5-haiku", 0.0008, 0.004)
            .with("gemini-2.5-pro", 0.00125, 0.01)
            .with("gemini-2.5-flash", 0.0003, 0.0025);
    }

    public static ModelPricing of() {
        return new ModelPricing(new java.util.concurrent.ConcurrentHashMap<>());
    }

    /** Add or replace a price entry (USD per 1K tokens). */
    public ModelPricing with(String model, double promptPer1k, double completionPer1k) {
        table.put(normalize(model), new double[]{promptPer1k, completionPer1k});
        return this;
    }

    /**
     * Compute USD cost for a generation, or {@link Optional#empty()} when the
     * model is unknown. Exact matches win; otherwise the longest key that the
     * model name starts with wins.
     */
    public Optional<Double> cost(String model, int promptTokens, int completionTokens) {
        if (model == null) {
            return Optional.empty();
        }
        double[] price = lookup(model);
        if (price == null) {
            return Optional.empty();
        }
        double cost = promptTokens / 1000.0 * price[0]
            + completionTokens / 1000.0 * price[1];
        return Optional.of(cost);
    }

    private double[] lookup(String model) {
        String normalized = normalize(model);
        double[] exact = table.get(normalized);
        if (exact != null) {
            return exact;
        }
        double[] best = null;
        int bestLen = -1;
        for (var entry : table.entrySet()) {
            if (normalized.startsWith(entry.getKey()) && entry.getKey().length() > bestLen) {
                best = entry.getValue();
                bestLen = entry.getKey().length();
            }
        }
        return best;
    }

    private static String normalize(String model) {
        return model.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** {@link #standard()} plus {@code NOOA_PRICE_*} environment overrides. */
    public static ModelPricing fromEnv() {
        ModelPricing pricing = standard();
        for (var entry : System.getenv().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("NOOA_PRICE_")) {
                continue;
            }
            String model = key.substring("NOOA_PRICE_".length())
                .toLowerCase(java.util.Locale.ROOT)
                .replace('_', '-');
            String[] parts = entry.getValue().trim().split("/");
            if (parts.length != 2) {
                continue;
            }
            try {
                pricing.with(model,
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()));
            } catch (NumberFormatException _) {
                // Ignore malformed override; keep the default table entry.
            }
        }
        return pricing;
    }
}
