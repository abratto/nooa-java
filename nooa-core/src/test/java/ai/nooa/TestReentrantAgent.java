package ai.nooa;

import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;

/**
 * Public top-level test agent. The sandbox preamble binds {@code __agent__}
 * with a cast to the agent type, and JShell rejects types whose canonical
 * name passes through a package-private enclosing class — so re-entrancy and
 * helper-call tests need a top-level public agent.
 */
public class TestReentrantAgent extends Agent {
    public TestReentrantAgent(UnifiedLLM llm) { super(llm); }

    @Generate public String generate(String x) { throw new UnsupportedOperationException(); }
}
