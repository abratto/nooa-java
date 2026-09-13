package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;

/**
 * The smallest possible NOOA agent: one class, one {@code @Generate} method.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>{@code @SystemPrompt} — the class-level persona and output contract
 *       (3-line haiku, mention the name once) that is prepended to every model
 *       call for this agent.</li>
 *   <li>{@code @Generate(prompt = ...)} — the runtime instruction for the
 *       method. The method body ({@code throw}) is never executed; ByteBuddy
 *       instruments the method and routes the call through the configured
 *       strategy instead.</li>
 *   <li>Default {@code CodeActStrategy} — with a plain {@code String} return
 *       type the model can answer directly via the {@code returnResult} tool,
 *       which is exactly the "simple answers determinable from the inputs"
 *       case in the strategy prompt.</li>
 *   <li>Typed I/O by signature — the parameter {@code name} is bound into the
 *       prompt (and into the sandbox as a variable), so the model always sees
 *       the actual value, not a vague instruction.</li>
 *   <li>Reasoning effort — this task needs no deliberation, so the example
 *       requests {@code "low"} to keep local-model latency down.</li>
 * </ul>
 *
 * <p><b>Run</b> (from the repository root, or via {@code examples/run.sh}):
 * <pre>{@code
 * mvn -pl examples -am clean install -DskipTests && \
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.QuickstartExamples
 * }</pre>
 * {@link QuickstartExamples} invokes this agent as Example 1. There is no
 * {@code main} here on purpose: agent classes hold capabilities, launcher
 * classes own the workflow.</p>
 */
@SystemPrompt("You are a greeting agent. Write exactly 3 lines in haiku style. Mention the provided name once. No extra commentary, no markdown, no explanation.")
public class GreetingAgent extends Agent {
    public GreetingAgent(UnifiedLLM llm) {
        super(llm);
        // A haiku needs no deliberation: low reasoning effort keeps local
        // model latency minimal. Override with NOOA_REASONING_EFFORT.
        ExampleLLM.tune(this, "low", 1024);
    }

    @Generate(prompt = "Write exactly 3 lines in haiku style. Mention the provided name once. No extra commentary, markdown, or explanation.")
    public String greet(String name) { throw new UnsupportedOperationException(); }
}
