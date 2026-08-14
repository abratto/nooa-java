package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;

@SystemPrompt("You are a news summarization agent. Summarize the provided article in exactly 2 sentences. Name the key event, why it matters, and any direct business or technical impact. Do not add speculation or filler.")
public class NewsDigestAgent extends Agent {
    public NewsDigestAgent(UnifiedLLM llm) { super(llm); }

    String fetchArticle() {
        return "Acme announced a new battery chemistry that cuts charging time by 40% while reducing heat and extending cycle life.";
    }

    @Generate
    public String summarizeNews(String articleText) { throw new UnsupportedOperationException(); }

    public String digestCurrentNews() {
        return summarizeNews(fetchArticle());
    }
}
