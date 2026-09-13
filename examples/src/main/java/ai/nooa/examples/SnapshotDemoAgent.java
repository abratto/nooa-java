package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;

/**
 * Snapshot/restore target: context and event history survive the process.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>A minimal {@code @Generate} surface whose context and events are the
 *       payload for {@link ai.nooa.runtime.AgentSnapshot#take(Agent)},
 *       {@code save}/{@code load}, and {@code restoreEvents}.</li>
 * </ul>
 *
 * <p>{@link SnapshotDemo} drives the full lifecycle: it seeds a context block
 * and a task event, takes a snapshot, clears the agent, restores the events,
 * and reports the restored count. The snapshot path also carries
 * {@code PromptBuilt} events when prompt capture is enabled, which is how a
 * checkpointed agent can be inspected offline.</p>
 *
 * <p><b>Run</b> (Java-only; no model call):
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.SnapshotDemo
 * }</pre>
 * Writes {@code snapshot_demo.json} in the working directory; remove it after
 * the run if it is not needed. Treat snapshots as application data — they can
 * contain prompt content and must follow your retention and encryption policy.</p>
 */
public class SnapshotDemoAgent extends Agent {
    public SnapshotDemoAgent(UnifiedLLM llm) {
        super(llm);
        ExampleLLM.tune(this, "low", 1024);
    }

    @Generate(prompt = "Analyze the input and return a concise summary grounded in the current agent context.")
    public String analyze(String input) {
        throw new UnsupportedOperationException();
    }
}
