package ai.nooa.examples;

import ai.nooa.AgentFactory;
import ai.nooa.tracing.Tracing;

import java.nio.file.Path;

/** Enables JSONL tracing and makes one traced generated call. */
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