package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;

@SystemPrompt("You are a research assistant. Use the Java helper methods as source-of-truth. Answer the user question with a brief, grounded summary that includes the most relevant fact, a short explanation, and the current time when useful. Do not invent sources or citations.")
public class ResearchAgent extends Agent {
    public record SearchResult(String title, String url, String snippet) {}

    public ResearchAgent(UnifiedLLM llm) { super(llm); }

    SearchResult search(String query) {
        return new SearchResult(query, "https://example.com/" + query, "Result for: " + query);
    }

    String getCurrentTime() { return java.time.LocalDateTime.now().toString(); }

    @Generate
    public String research(String question) { throw new UnsupportedOperationException(); }
}
