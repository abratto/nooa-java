package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;

public class SnapshotDemoAgent extends Agent {
    public SnapshotDemoAgent(UnifiedLLM llm) { super(llm); }

    @Generate
    public String analyze(String input) {
        throw new UnsupportedOperationException();
    }
}
