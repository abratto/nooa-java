package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;

/**
 * Static context blocks steering the model without exposing raw state.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>Static context block — {@link #setFocus(String)} writes a
 *       {@code focus} block that is rendered into the system prompt of every
 *       subsequent call until removed. Unlike a dynamic block it is evaluated
 *       once when set.</li>
 *   <li>Context lifecycle — {@link #clearFocus()} removes the block with
 *       {@code context().remove("focus")}, showing that context is runtime
 *       state the application controls, not prompt text baked into the class.</li>
 *   <li>Guidance over exposure — the model is told the current priority
 *       through the prompt, while the issue list and other state stay in Java.
 *       This is the recommended pattern for operator controls.</li>
 *   <li>Reasoning effort — root-cause triage from a short description:
 *       {@code "low"} keeps it fast; raise it for genuinely hard diagnoses.</li>
 * </ul>
 *
 * <p><b>Run</b> via {@link QuickstartAdvanced} (Example 08):
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.QuickstartAdvanced
 * }</pre>
 */
@SystemPrompt("You are a debugging assistant. Use the issue description and current focus as the only inputs. Identify likely root causes, list the most probable next checks in order, and keep the answer concise and practical. Do not speculate beyond the evidence.")
public class DebugAgent extends Agent {
    public DebugAgent(UnifiedLLM llm) {
        super(llm);
        ExampleLLM.tune(this, "low", 2048);
    }

    void setFocus(String topic) { context().put("focus", "Priority: " + topic); }
    void clearFocus() { context().remove("focus"); }

    @Generate(prompt = "Identify likely root causes, list the most probable next checks in order, and keep the answer concise and practical. Use only the issue and current focus.")
    public String analyze(String issue) { throw new UnsupportedOperationException(); }
}
