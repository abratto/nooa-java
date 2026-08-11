package ai.nooa;

import ai.nooa.annotations.Generate;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;

import java.util.concurrent.CompletableFuture;

@SystemPrompt("You are a test agent.")
public class TestPromptedAgent extends Agent {
    public TestPromptedAgent(UnifiedLLM llm) { super(llm); }
    @Generate public String generate(String x) {
        throw new UnsupportedOperationException();
    }
}
