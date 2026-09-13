package ai.nooa.examples;

import ai.nooa.AgentFactory;
import ai.nooa.context.Event;
import ai.nooa.runtime.AgentSnapshot;

import java.nio.file.Path;

/**
 * Saves and restores agent context events using a local JSON snapshot.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>{@link AgentSnapshot#take(Agent)} — captures the agent's context
 *       blocks and event history in one serializable value.</li>
 *   <li>{@code AgentSnapshot.save}/{@code load} — JSON persistence to
 *       {@code snapshot_demo.json}.</li>
 *   <li>{@code AgentSnapshot.restoreEvents} — replays the saved events into a
 *       (possibly fresh) agent, which is the basis for resumable workers and
 *       durable sessions via {@code SessionStore}.</li>
 *   <li>Event seeding — {@code new Event.Task("analyse input data")} shows
 *       that the event history is ordinary application data you can
 *       construct.</li>
 * </ul>
 *
 * <p><b>Run</b> (Java-only; no model call):
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.SnapshotDemo
 * }</pre>
 * or {@code examples/run.sh SnapshotDemo}. Writes {@code snapshot_demo.json}
 * in the working directory; remove it after the run. Snapshots can contain
 * prompt content — treat them as application data with an appropriate
 * retention policy.</p>
 */
public final class SnapshotDemo {
    private static final Path SNAPSHOT_PATH = Path.of("./snapshot_demo.json");

    private SnapshotDemo() {}

    public static void main(String[] args) {
        var agent = AgentFactory.create(SnapshotDemoAgent.class, ExampleLLM.create());
        try {
            agent.context().put("session", "demo-123");
            agent.eventManager().add(new Event.Task("analyse input data"));

            AgentSnapshot.save(AgentSnapshot.take(agent), SNAPSHOT_PATH);
            var loaded = AgentSnapshot.load(SNAPSHOT_PATH);
            agent.eventManager().clear();
            AgentSnapshot.restoreEvents(agent, loaded);
            System.out.println("Restored " + agent.eventManager().size() + " event(s) from " + SNAPSHOT_PATH);
        } finally {
            agent.close();
        }
    }
}
