package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;

public class TraceDemoAgent extends Agent {
    public TraceDemoAgent(UnifiedLLM llm) { super(llm); }

    @Generate
    public String greet(String name) {
        throw new UnsupportedOperationException();
    }
}
