package ai.nooa.examples;

import ai.nooa.AgentFactory;

/** Installs a token-budget summarizer around a generated conversation. */
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