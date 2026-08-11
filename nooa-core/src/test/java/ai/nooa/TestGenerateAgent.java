package ai.nooa;

import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;

public class TestGenerateAgent extends Agent {
    public TestGenerateAgent(UnifiedLLM llm) { super(llm); }
    @Generate
    public String generate(String input) { throw new UnsupportedOperationException(); }
}
