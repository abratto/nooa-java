package ai.nooa.runtime;

import ai.nooa.Agent;
import ai.nooa.context.Event;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Durable event and agent-state session storage backed by snapshot files. */
public final class SessionStore {
    private final Path directory;

    public SessionStore(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory must not be null");
    }

    public Path directory() { return directory; }

    public void save(String sessionId, Agent agent) {
        validateId(sessionId);
        AgentSnapshot.save(AgentSnapshot.take(agent), path(sessionId));
    }

    public AgentSnapshot.Snapshot load(String sessionId) {
        validateId(sessionId);
        return AgentSnapshot.load(path(sessionId));
    }

    public void restore(String sessionId, Agent agent) {
        AgentSnapshot.Snapshot snapshot = load(sessionId);
        AgentSnapshot.restoreEvents(agent, snapshot);
        AgentSnapshot.restoreContext(agent, snapshot);
    }

    /** Append one event to an existing snapshot without replacing its context. */
    public void appendEvent(String sessionId, Event event) {
        validateId(sessionId);
        Objects.requireNonNull(event, "event must not be null");
        AgentSnapshot.Snapshot current = load(sessionId);
        var events = new ArrayList<>(current.events());
        events.add(AgentSnapshot.serializeEvent(event));
        saveSnapshot(sessionId, new AgentSnapshot.Snapshot(
            current.agentClass(), current.agentId(), current.createdAt(), events,
            new LinkedHashMap<>(current.contextBlocks()), current.dynamicContextBlocks(),
            current.instanceValues(), current.model()));
    }

    public boolean exists(String sessionId) {
        validateId(sessionId);
        return Files.isRegularFile(path(sessionId));
    }

    public void delete(String sessionId) {
        validateId(sessionId);
        try {
            Files.deleteIfExists(path(sessionId));
        } catch (Exception e) {
            throw new SessionStorageError("Failed to delete session: " + sessionId, e);
        }
    }

    private void saveSnapshot(String sessionId, AgentSnapshot.Snapshot snapshot) {
        AgentSnapshot.save(snapshot, path(sessionId));
    }

    private Path path(String sessionId) { return directory.resolve(sessionId + ".json"); }

    private static void validateId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()
            || !sessionId.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid session id");
        }
    }
}