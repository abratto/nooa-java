package ai.nooa.runtime;

import ai.nooa.context.Event;
import ai.nooa.llm.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the conversation event history.
 * Thread-safe. Events are appended in order and available for context building.
 */
public final class EventManager {

    private static final Logger log = LoggerFactory.getLogger(EventManager.class);

    private final List<Event> events = new CopyOnWriteArrayList<>();
    private final List<Consumer<Event>> listeners = new CopyOnWriteArrayList<>();

    public void add(Event event) {
        events.add(event);
        for (Consumer<Event> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.debug("Event listener error", e);
            }
        }
    }

    public List<Event> all() {
        return List.copyOf(events);
    }

    public List<Event> since(int index) {
        if (index < 0) { index = 0; }
        var snapshot = events;
        if (index >= snapshot.size()) { return List.of(); }
        return List.copyOf(snapshot.subList(index, snapshot.size()));
    }

    public int size() {
        return events.size();
    }

    public void clear() {
        events.clear();
    }

    /** Clear events in range [from, to). */
    public void clearRange(int from, int to) {
        if (from < 0 || to > events.size() || from >= to) return;
        var snapshot = new ArrayList<>(events);
        snapshot.subList(from, to).clear();
        events.clear();
        events.addAll(snapshot);
    }

    /** Insert an event at a specific index. */
    public void insertAt(int index, Event event) {
        var snapshot = new ArrayList<>(events);
        snapshot.add(index, event);
        events.clear();
        events.addAll(snapshot);
    }

    public void onEvent(Consumer<Event> listener) {
        listeners.add(listener);
    }

    /**
     * Returns events as LLM messages for context building.
     */
    public List<Message> toMessages() {
        List<Message> messages = new ArrayList<>();
        for (Event e : events) {
            switch (e) {
                case Event.Task t -> messages.add(Message.user(t.content()));
                case Event.LLMOutput o -> messages.add(Message.assistant(
                    o.content() != null ? o.content() : ""));
                case Event.ExecutionOutput ex -> {
                    if (ex.stdout() != null && !ex.stdout().isBlank())
                        messages.add(Message.user("Output:\n" + ex.stdout()));
                    if (ex.error() != null && !ex.error().isBlank())
                        messages.add(Message.user("Error:\n" + ex.error()));
                }
                case Event.ErrorEvent err -> messages.add(Message.user("Error: " + err.message()));
                case Event.Feedback f -> messages.add(Message.user(f.content()));
                case Event.Summary s -> messages.add(Message.assistant(s.summaryText()));
                default -> { /* lifecycle events not rendered */ }
            }
        }
        return messages;
    }

    /**
     * Returns the count of events since the last checkpoint index.
     */
    public int eventsSince(int lastIndex) {
        return Math.max(0, events.size() - lastIndex);
    }

    /** Compact summary of events for token counting. */
    public String renderSummary() {
        var sb = new StringBuilder();
        for (Event e : events) {
            switch (e) {
                case Event.Task t -> sb.append(t.content());
                case Event.LLMOutput o -> sb.append(o.content() != null ? o.content() : "");
                default -> {}
            }
        }
        return sb.toString();
    }
}
