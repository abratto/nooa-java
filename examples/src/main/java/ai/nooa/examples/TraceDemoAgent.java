package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;

/**
 * Tracing target: one generated call that shows up in JSONL trace output.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>Agent spans — {@link ai.nooa.tracing.Tracing#enable} plus this call
 *       produces an agent span for {@code greet} (tracing is on by default and
 *       can be opted out per method with {@code @NoTrace}).</li>
 *   <li>LLM and code spans — the underlying model call and any CodeAct
 *       sandbox executions are recorded as child spans, giving a full
 *       trajectory of the generated method.</li>
 * </ul>
 *
 * <p><b>Run</b> via {@link TracingDemo}:
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.TracingDemo
 * }</pre>
 * Writes {@code traces_demo/traces.jsonl}; inspect it to see span nesting for
 * the agent, LLM, and code execution. Remove the directory after the run if it
 * is not needed, and never place credentials in prompts that get traced.</p>
 */
public class TraceDemoAgent extends Agent {
    public TraceDemoAgent(UnifiedLLM llm) {
        super(llm);
        ExampleLLM.tune(this, "low", 1024);
    }

    @Generate(prompt = "Greet the person warmly and concisely.")
    public String greet(String name) {
        throw new UnsupportedOperationException();
    }
}
