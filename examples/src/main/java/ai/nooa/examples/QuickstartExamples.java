package ai.nooa.examples;

import ai.nooa.AgentFactory;
import ai.nooa.llm.UnifiedLLM;

import java.util.Map;

/**
 * The four core patterns in one runnable tour.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ol>
 *   <li>{@link GreetingAgent} — plain {@code @Generate} with the default
 *       CodeAct strategy answering directly via {@code returnResult}.</li>
 *   <li>{@link SentimentAgent} — {@link ai.nooa.strategy.PredictStrategy}
 *       validating a structured result record.</li>
 *   <li>{@link SupportAgent} — deterministic Java helper facts
 *       ({@code __agent__.getStock(...)}) feeding a grounded model answer.</li>
 *   <li>{@link NewsDigestAgent} — a Java-owned workflow: fetch facts, then
 *       delegate the wording to a typed generated capability.</li>
 * </ol>
 *
 * <p><b>Run</b>:
 * <pre>{@code
 * mvn -pl examples -am clean install -DskipTests && \
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.QuickstartExamples
 * }</pre>
 * or {@code examples/run.sh QuickstartExamples}. Requires a model endpoint
 * (local Ollama by default, see {@link ExampleLLM}). Each agent is closed in a
 * try/finally, demonstrating the {@link Agent} {@code AutoCloseable} lifecycle
 * that releases sandbox resources.</p>
 */
public final class QuickstartExamples {

    public static void main(String[] args) throws Exception {
        var llm = ExampleLLM.create();

        // Example 1: the most basic pattern — plain @Generate method.
        System.out.println("--- Example 1: Greeting ---");
        var greetingAgent = AgentFactory.create(GreetingAgent.class, llm);
        System.out.println("Pattern: plain @Generate + simple text response");
        System.out.println(greetingAgent.greet("Alice"));
        greetingAgent.close();

        // Example 2: strict output schema, enforced by the runtime.
        System.out.println("\n--- Example 2: Sentiment ---");
        var sentimentAgent = AgentFactory.create(SentimentAgent.class, llm);
        System.out.println("Pattern: PredictStrategy validates a structured result record");
        System.out.println(sentimentAgent.analyze("This is fantastic!"));
        sentimentAgent.close();

        // Example 3: a detached business helper + model-generated customer response.
        System.out.println("\n--- Example 3: Support Agent ---");
        var supportAgent = AgentFactory.create(SupportAgent.class, llm,
            Map.of("widget", 5, "gadget", 0));
        System.out.println("Pattern: Java helper methods provide facts; the model answers with context");
        System.out.println(supportAgent.checkOrder("widget", 3));
        supportAgent.close();

        // Example 4: orchestrator-driven workflow built from Java + generation step.
        System.out.println("\n--- Example 4: Periodic News Digest ---");
        var newsAgent = AgentFactory.create(NewsDigestAgent.class, llm);
        System.out.println("Pattern: Java fetches input, model summarizes it, orchestrator owns the workflow");
        System.out.println(newsAgent.digestCurrentNews());
        newsAgent.close();
    }
}
