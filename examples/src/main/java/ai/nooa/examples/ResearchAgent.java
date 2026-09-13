package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;

/**
 * Model-visible helper methods: progressive disclosure in practice.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>Helpers as tools — {@link #search(String)} and
 *       {@link #getCurrentTime()} are package-private, ordinary methods. The
 *       runtime documents them in {@code AgentDoc} and the CodeAct strategy
 *       exposes them to generated code through {@code __agent__.search(...)}.
 *       The model decides when to call them; Java decides what they return.</li>
 *   <li>Progressive disclosure — the model starts from the task and pulls
 *       facts on demand, instead of every fact being stuffed into the prompt
 *       up front. This keeps prompts small and grounded.</li>
 *   <li>Nested record as tool result — {@link SearchResult} is a public
 *       record, so its shape is part of the capability surface the model can
 *       rely on.</li>
 *   <li>Anti-hallucination prompt — the system prompt forbids invented
 *       sources; grounding comes from the helpers, not the model's memory.</li>
 *   <li>Reasoning effort — two cheap helper calls and a short summary:
 *       {@code "low"}.</li>
 * </ul>
 *
 * <p><b>Run</b> via {@link QuickstartAdvanced} (Example 05):
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.QuickstartAdvanced
 * }</pre>
 */
@SystemPrompt("You are a research assistant. The Java helper methods search(question) and getCurrentTime() are your only sources of truth. You MUST call __agent__.search(question) and __agent__.getCurrentTime() in executeJava before answering. Ground your summary in the search result snippet and include the current time. Do not invent sources, citations, or facts from your own knowledge.")
public class ResearchAgent extends Agent {
    public record SearchResult(String title, String url, String snippet) {}

    public ResearchAgent(UnifiedLLM llm) {
        super(llm);
        ExampleLLM.tune(this, "low", 4096);
    }

    public SearchResult search(String query) {
        return new SearchResult(query, "https://example.com/" + query, "Result for: " + query);
    }

    public String getCurrentTime() { return java.time.LocalDateTime.now().toString(); }

    @Generate(prompt = """
        In executeJava, run exactly this pattern (use var, do not re-declare):
            var result = __agent__.search(question);
            var time = __agent__.getCurrentTime();
        Then call returnResult with a brief summary grounded in result.snippet()
        and time. Do not invent sources, citations, or facts from your own
        knowledge.
        """)
    public String research(String question) { throw new UnsupportedOperationException(); }
}
