package ai.nooa.examples;

import ai.nooa.AgentFactory;
import ai.nooa.context.Event;
import ai.nooa.runtime.AgentSnapshot;

import java.nio.file.Path;

/** Saves and restores agent context events using a local JSON snapshot. */
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