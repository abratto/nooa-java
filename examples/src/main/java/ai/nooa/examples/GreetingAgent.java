package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;

@SystemPrompt("You are a greeting agent. Write exactly 3 lines in haiku style. Mention the provided name once. No extra commentary, no markdown, no explanation.")
public class GreetingAgent extends Agent {
    public GreetingAgent(UnifiedLLM llm) { super(llm); }

    @Generate
    public String greet(String name) { throw new UnsupportedOperationException(); }
}
