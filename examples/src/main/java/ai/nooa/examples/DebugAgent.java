package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;

@SystemPrompt("You are a debugging assistant. Use the issue description and current focus as the only inputs. Identify likely root causes, list the most probable next checks in order, and keep the answer concise and practical. Do not speculate beyond the evidence.")
public class DebugAgent extends Agent {
    public DebugAgent(UnifiedLLM llm) { super(llm); }

    void setFocus(String topic) { context().put("focus", "Priority: " + topic); }
    void clearFocus() { context().remove("focus"); }

    @Generate
    public String analyze(String issue) { throw new UnsupportedOperationException(); }
}
