package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.annotations.Strategy;
import ai.nooa.strategy.PredictStrategy;
import ai.nooa.llm.UnifiedLLM;

/**
 * Smallest useful orchestration example: Java supplies facts, and one NOOA
 * capability turns those facts into a concise reader-facing summary.
 *
 * <p>Run it from the repository root with:
 * <pre>{@code
 * mvn -pl examples -am compile && \
 * mvn -pl examples exec:java \
 *     -Dexec.mainClass=ai.nooa.examples.NewsDigestAgent
 * }</pre>
 * The shared {@link ExampleLLM} configuration uses the local Ollama model by
 * default; set {@code NOOA_MODEL} or the provider environment variables when
 * using a different model or endpoint.
 *
 * <p>The boundary is intentional: fetching or constructing source facts is
 * deterministic Java, while wording and prioritizing the summary is the part
 * delegated to the model. Keeping those responsibilities separate makes the
 * workflow predictable and easy to replace with a real news client later.
 */
@SystemPrompt("You are a news summarization agent. Summarize the provided article in exactly 2 sentences. Name the key event, why it matters, and any direct business or technical impact. Do not add speculation or filler.")
public class NewsDigestAgent extends Agent {
    public NewsDigestAgent(UnifiedLLM llm) {
        super(llm);
    }

    /**
     * Deterministic source data. This is a plain Java method rather than a
     * model capability because the agent should not invent or retrieve facts
     * while preparing the input for summarization.
     */
    String fetchArticle() {
        return "Acme announced a new battery chemistry that cuts charging time by 40% while reducing heat and extending cycle life.";
    }

    /**
     * Model capability: {@code @Generate} lets NOOA route this call through
     * the configured LLM and return the generated text.
     */
    @Generate(prompt = """
        Summarize the provided article in exactly 2 sentences.
        Name the key event, why it matters, and any direct business or technical impact.
        Do not add speculation or filler.
        """)
    @Strategy(PredictStrategy.class)
    public String summarizeNews(String articleText) {
        throw new UnsupportedOperationException("Generated at runtime");
    }

    /** Plain Java owns the workflow and decides which capability to call. */
    public String digestCurrentNews() {
        return summarizeNews(fetchArticle());
    }

    public static void main(String[] args) {
        var llm = ExampleLLM.create();
        // AgentFactory creates the instrumented subclass that activates
        // @Generate; constructing NewsDigestAgent directly would not do that.
        try (var agent = AgentFactory.create(NewsDigestAgent.class, llm)) {
            System.out.println(agent.digestCurrentNews());
        }
    }
}
