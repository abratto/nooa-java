package ai.nooa.runtime;

import ai.nooa.Agent;
import ai.nooa.context.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Monitors token usage and automatically collapses old conversation
 * events into summaries when approaching the model's context limit.
 *
 * <pre>{@code
 * var summarizer = new TokenBudgetSummarizer(agent, 180_000);
 * summarizer.install(); // checks after each LLM call
 * }</pre>
 */
public final class TokenBudgetSummarizer {

    private static final Logger log = LoggerFactory.getLogger(TokenBudgetSummarizer.class);

    private final Agent agent;
    private final int tokenBudget;   // total window size in tokens
    private final double threshold;  // fraction at which to collapse (0.0-1.0)

    /**
     * @param agent       the agent to monitor
     * @param tokenBudget total context window in tokens
     * @param threshold   collapse when utilization > threshold (default 0.85)
     */
    public TokenBudgetSummarizer(Agent agent, int tokenBudget, double threshold) {
        this.agent = agent;
        this.tokenBudget = tokenBudget;
        this.threshold = threshold;
    }

    public TokenBudgetSummarizer(Agent agent, int tokenBudget) {
        this(agent, tokenBudget, 0.85);
    }

    /** Subscribe to the agent's event manager to auto-check after LLM calls. */
    public void install() {
        agent.eventManager().onEvent(event -> {
            if (event instanceof Event.LLMComplete) {
                checkAndCompact();
            }
        });
    }

    /** Manually trigger a compaction check. */
    public void checkAndCompact() {
        var stats = agent.runtime().stats();
        int currentTokens = stats.totalTokens();

        if (currentTokens > tokenBudget * threshold) {
            compact();
        }
    }

    private void compact() {
        var events = agent.eventManager().all();
        if (events.size() < 4) return; // too few to compact

        // Find the oldest conversation events (skip first Task, keep last 3 events)
        int keepFromEnd = 3;
        int collapseCount = events.size() - keepFromEnd - 1; // minus initial Task
        if (collapseCount <= 0) return;

        List<String> replacedTags = new ArrayList<>();
        List<Event> collapsed = new ArrayList<>();

        int startIdx = 1; // skip Task
        int endIdx = events.size() - keepFromEnd;

        StringBuilder summaryBuilder = new StringBuilder("Summary of previous conversation:\n");
        for (int i = startIdx; i < endIdx; i++) {
            Event e = events.get(i);
            if (e instanceof Event.Task t) {
                summaryBuilder.append("- User: ").append(truncate(t.content(), 100)).append("\n");
            } else if (e instanceof Event.LLMOutput o) {
                String content = o.content() != null ? o.content() : "";
                summaryBuilder.append("- Assistant: ").append(truncate(content, 200)).append("\n");
            } else if (e instanceof Event.ErrorEvent ee) {
                summaryBuilder.append("- Error: ").append(truncate(ee.message(), 100)).append("\n");
            }
            collapsed.add(e);
        }

        // Clear old events and insert summary
        agent.eventManager().clearRange(startIdx, endIdx);
        agent.eventManager().insertAt(startIdx,
            new Event.Summary(summaryBuilder.toString().strip(), replacedTags));

        log.debug("Compacted {} events into summary ({} tokens → {} chars)",
            collapseCount,
            agent.runtime().stats().totalTokens(),
            summaryBuilder.length());
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
