package ai.nooa.examples;

import ai.nooa.AgentFactory;

/**
 * Installs a token-budget summarizer around a generated conversation.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>{@link ai.nooa.runtime.TokenBudgetSummarizer#install()} — subscribes
 *       to the event manager so context compaction is checked after every LLM
 *       call.</li>
 *   <li>A normal generated call with the summarizer active.</li>
 * </ul>
 *
 * <p><b>Run</b>:
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.SummarizationDemo
 * }</pre>
 * or {@code examples/run.sh SummarizationDemo}. Requires a model endpoint
 * (local Ollama by default). The 100,000-token budget means a single turn will
 * not trigger compaction; see {@link SummarizationDemoAgent} for how to
 * observe an actual collapse.</p>
 */
public final class SummarizationDemo {
    private SummarizationDemo() {}

    public static void main(String[] args) {
        var agent = AgentFactory.create(SummarizationDemoAgent.class, ExampleLLM.create());
        try {
            agent.installSummarizer(100_000);
            System.out.println(agent.chat("Give me one concise tip for reviewing Java code."));
        } finally {
            agent.close();
        }
    }
}
