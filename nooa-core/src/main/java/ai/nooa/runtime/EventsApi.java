package ai.nooa.runtime;

import ai.nooa.Agent;
import ai.nooa.context.Event;
import java.util.List;
import java.util.UUID;

/**
 * LLM-facing events API. Lets generated code query past events
 * by type or content.
 */
public final class EventsApi {

    private final Agent agent;

    public EventsApi(Agent agent) {
        this.agent = agent;
    }

    /** Return a snapshot of all events currently held by the agent. */
    public List<Event> all() {
        return agent.eventManager().all();
    }

    /** Return events emitted by the active generation call, excluding earlier calls. */
    public List<Event> current() {
        return agent.eventManager().current();
    }

    /** Return the active generation call ID, or {@code null} outside a call scope. */
    public UUID currentCallId() {
        return agent.eventManager().currentCallId();
    }

    /** Return a snapshot of events associated with the supplied call ID. */
    public List<Event> forCall(UUID callId) {
        return agent.eventManager().forCall(callId);
    }

    /** Return events from the given index onward; negative indexes are treated as zero. */
    public List<Event> since(int index) {
        return agent.eventManager().since(index);
    }

    /** Return the current number of stored events. */
    public int size() {
        return agent.eventManager().size();
    }

    /**
     * Find events matching a type name substring (case-insensitive).
     */
    @SuppressWarnings("unchecked")
    public <T extends Event> List<T> findByType(String typeName) {
        return (List<T>) agent.eventManager().all().stream()
            .filter(e -> e.getClass().getSimpleName().toLowerCase()
                .contains(typeName.toLowerCase()))
            .toList();
    }

    @Override
    public String toString() {
        return "EventsApi[agent=" + agent.getClass().getSimpleName() + "]";
    }
}
