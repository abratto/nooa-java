package ai.nooa.context;

/**
 * Context window utilization snapshot — tracks token usage and block sizes
 * across a generation session. Updated after each LLM call.
 */
public record ContextWindowStats(
    int promptTokens,       // provider-reported prompt tokens from last call
    int completionTokens,   // provider-reported completion tokens
    int totalTokens,        // cumulative total across all calls this session
    int contextBlocksChars, // approximate char size of all context blocks
    int eventsChars,        // approximate char size of all conversation events
    int overallUtilizationPercent  // 0-100, based on a 200k window default
) {
    /** Merge the latest LLM response stats into a running total. */
    public ContextWindowStats accumulate(ai.nooa.llm.LLMResponse.Usage usage,
                                          int blocksChars, int eventsChars) {
        int newTotal = totalTokens + usage.totalTokens();
        int totalChars = blocksChars + eventsChars;
        int utilization = estimateUtilization(newTotal, totalChars);

        return new ContextWindowStats(
            usage.promptTokens(),
            usage.completionTokens(),
            newTotal,
            blocksChars,
            eventsChars,
            utilization
        );
    }

    /** Create initial empty stats. */
    public static ContextWindowStats empty() {
        return new ContextWindowStats(0, 0, 0, 0, 0, 0);
    }

    /**
     * Estimate context window utilization. Weights tokens more heavily
     * than character counts (tokens are ~4 chars each on average).
     */
    private static int estimateUtilization(int totalTokens, int totalChars) {
        double tokenRatio = totalTokens / 200_000.0;   // 200k window
        double charRatio = (totalChars / 4.0) / 200_000.0;
        double combined = Math.max(tokenRatio, charRatio);
        return (int) Math.min(100, combined * 100);
    }

    /** Human-readable summary. */
    public String summary() {
        return String.format(
            "Tokens: %,d prompt / %,d completion / %,d total | "
            + "Context: %,d chars blocks + %,d chars events | "
            + "Window: %d%% used",
            promptTokens, completionTokens, totalTokens,
            contextBlocksChars, eventsChars,
            overallUtilizationPercent
        );
    }
}
