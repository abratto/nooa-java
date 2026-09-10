package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.runtime.TokenBudgetSummarizer;

public class SummarizationDemoAgent extends Agent {
    public SummarizationDemoAgent(UnifiedLLM llm) { super(llm); }

    void installSummarizer(int tokenBudget) {
        var summarizer = new TokenBudgetSummarizer(this, tokenBudget);
        summarizer.install();
    }

    @Generate(prompt = "Respond helpfully to the message while keeping the conversation concise and allowing the installed token-budget summarizer to manage context.")
    public String chat(String message) {
        throw new UnsupportedOperationException();
    }
}
