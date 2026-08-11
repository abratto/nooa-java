package ai.nooa.runtime;

import ai.nooa.Agent;
import ai.nooa.context.Event;
import java.util.List;

/**
 * LLM-facing events API. Lets generated code query past events
 * by type or content.
 */
public final class EventsApi {

    private final Agent agent;

    public EventsApi(Agent agent) {
        this.agent = agent;
    }

    public List<Event> all() {
        return agent.eventManager().all();
    }

    public List<Event> since(int index) {
        return agent.eventManager().since(index);
    }

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
