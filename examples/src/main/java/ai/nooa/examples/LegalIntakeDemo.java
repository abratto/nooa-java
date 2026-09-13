package ai.nooa.examples;

import ai.nooa.AgentFactory;

/**
 * Structured intake triage over four representative messages.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>{@link StructuredOutputHelper} via
 *       {@code LegalIntakeAgent.handleStructured(msg)} — the standalone,
 *       provider-agnostic extraction path with built-in retry and validation.</li>
 *   <li>{@link LegalIntakeAgent#handle(String)} — the equivalent
 *       {@code @Generate} + {@code PredictStrategy} capability, invoked for the
 *       first message so both paths are visible side by side.</li>
 *   <li>Typed field access on the returned {@link LegalIntakeResult} record —
 *       no JSON parsing in application code.</li>
 * </ul>
 *
 * <p><b>Safety.</b> This is an intake classifier, not legal advice; the
 * prompts forbid definitive legal conclusions.</p>
 *
 * <p><b>Run</b>:
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.LegalIntakeDemo
 * }</pre>
 * or {@code examples/run.sh LegalIntakeDemo}. Requires a model endpoint (local
 * Ollama by default).</p>
 */
public final class LegalIntakeDemo {
    public static void main(String[] args) throws Exception {
        var llm = ExampleLLM.create();
        var agent = AgentFactory.create(LegalIntakeAgent.class, llm);

        String[] messages = {
            "My landlord hasn't fixed the heating for 3 months. It's freezing.",
            "URGENT: I'm being evicted tomorrow with no notice!",
            "I need help drafting a non-compete clause for my startup.",
            "My ex-spouse is violating the custody agreement. Again.",
        };

        System.out.println("=== @Generate + PredictStrategy path ===");
        LegalIntakeResult generated = agent.handle(messages[0]);
        System.out.println("Result: " + generated);

        System.out.println("\n=== StructuredOutputHelper path ===");
        for (String msg : messages) {
            System.out.println("\n=== Incoming: " + msg);
            LegalIntakeResult response = agent.handleStructured(msg);
            System.out.println("Result: " + response);
            System.out.println("category=" + response.category());
            System.out.println("urgency=" + response.urgency());
            System.out.println("nextStep=" + response.nextStep());
            System.out.println("summary=" + response.summary());
        }

        agent.close();
    }
}
