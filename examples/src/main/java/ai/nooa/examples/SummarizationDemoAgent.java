package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.runtime.TokenBudgetSummarizer;

/**
 * Automatic context compaction with a token-budget summarizer.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>{@link TokenBudgetSummarizer} — installed once with
 *       {@code installSummarizer(budget)}, it subscribes to the event manager
 *       and, after every LLM call, collapses the oldest conversation events
 *       into a single {@code Event.Summary} when token usage crosses the
 *       threshold (default 85% of the budget).</li>
 *   <li>Long-running conversation shape — a chat agent can run indefinitely
 *       because history is bounded; the summarizer trades detail for continued
 *       operation instead of failing on context overflow.</li>
 *   <li>Reasoning effort — a one-tip reply: {@code "low"}. The summarizer, not
 *       the model, is the interesting machinery here.</li>
 * </ul>
 *
 * <p><b>Run</b> via {@link SummarizationDemo}:
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.SummarizationDemo
 * }</pre>
 * The demo uses a 100,000-token budget, far above a single message, so
 * compaction will not trigger in a one-turn run — the demo verifies
 * installation and a normal call. For a compaction you can observe, lower the
 * budget or loop several turns.</p>
 */
public class SummarizationDemoAgent extends Agent {
    public SummarizationDemoAgent(UnifiedLLM llm) {
        super(llm);
        ExampleLLM.tune(this, "low", 2048);
    }

    void installSummarizer(int tokenBudget) {
        var summarizer = new TokenBudgetSummarizer(this, tokenBudget);
        summarizer.install();
    }

    @Generate(prompt = "Respond helpfully to the message while keeping the conversation concise and allowing the installed token-budget summarizer to manage context.")
    public String chat(String message) {
        throw new UnsupportedOperationException();
    }
}
