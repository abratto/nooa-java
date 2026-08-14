package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;

public class McpDemoAgent extends Agent {
    public McpDemoAgent(UnifiedLLM llm) { super(llm); }

    @Generate
    public String analyze(String input) {
        throw new UnsupportedOperationException();
    }
}
