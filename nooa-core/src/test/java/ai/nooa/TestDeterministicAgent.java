package ai.nooa;

import ai.nooa.llm.UnifiedLLM;

public class TestDeterministicAgent extends Agent {
    public TestDeterministicAgent(UnifiedLLM llm) { super(llm); }
    public String helper(String x) { return "helped: " + x; }
}
