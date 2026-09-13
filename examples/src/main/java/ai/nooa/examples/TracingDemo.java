package ai.nooa.examples;

import ai.nooa.AgentFactory;
import ai.nooa.tracing.Tracing;

import java.nio.file.Path;

/**
 * Enables JSONL tracing and makes one traced generated call.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>{@link Tracing#enable} with the JSONL exporter — agent, LLM, and code
 *       execution spans are appended to {@code traces_demo/traces.jsonl}.</li>
 *   <li>{@link Tracing#shutdown} in a finally block — the writer must be
 *       flushed before the process exits.</li>
 * </ul>
 *
 * <p><b>Run</b>:
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.TracingDemo
 * }</pre>
 * or {@code examples/run.sh TracingDemo}. Requires a model endpoint (local
 * Ollama by default). Inspect the JSONL afterwards; it contains prompt
 * metadata and event history, so avoid tracing prompts with credentials.</p>
 */
public final class TracingDemo {
    private TracingDemo() {}

    public static void main(String[] args) {
        Tracing.enable(Tracing.jsonl(Path.of("./traces_demo")));
        var agent = AgentFactory.create(TraceDemoAgent.class, ExampleLLM.create());
        try {
            System.out.println(agent.greet("Alice"));
            System.out.println("Trace output: ./traces_demo/traces.jsonl");
        } finally {
            agent.close();
            Tracing.shutdown();
        }
    }
}
