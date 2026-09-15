package ai.nooa.runtime;

import ai.nooa.context.Event;
import ai.nooa.llm.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private record Scope(int start, UUID callId) {}

    private final ThreadLocal<Deque<Scope>> scopeStarts =
        ThreadLocal.withInitial(ArrayDeque::new);
    private final Map<UUID, UUID> eventCallIds = new ConcurrentHashMap<>();

    /** Append an event, associate it with the active scope, and notify listeners. */
    public void add(Event event) {
        events.add(event);
        Deque<Scope> scopes = scopeStarts.get();
        if (!scopes.isEmpty()) {
            eventCallIds.put(event.id(), scopes.peek().callId());
        }
        for (Consumer<Event> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.debug("Event listener error", e);
            }
        }
    }

    /** Return an immutable snapshot of the complete event history. */
    public List<Event> all() {
        return List.copyOf(events);
    }

    /** Return an immutable snapshot from an index onward; out-of-range indexes yield an empty list. */
    public List<Event> since(int index) {
        if (index < 0) { index = 0; }
        var snapshot = events;
        if (index >= snapshot.size()) { return List.of(); }
        return List.copyOf(snapshot.subList(index, snapshot.size()));
    }

    /** Return the number of events currently stored. */
    public int size() {
        return events.size();
    }

    /** Begin a nested event scope using a generated call ID. */
    public void beginScope() {
        beginScope(UUID.randomUUID());
    }

    /** Begin a nested event scope associated with the supplied call ID. */
    public void beginScope(UUID callId) {
        scopeStarts.get().push(new Scope(events.size(), callId));
    }

    /** End the current scope; calling this outside a scope is a no-op. */
    public void endScope() {
        Deque<Scope> scopes = scopeStarts.get();
        if (!scopes.isEmpty()) {
            scopes.pop();
        }
        if (scopes.isEmpty()) {
            scopeStarts.remove();
        }
    }

    /** Return events emitted in the current scope, or all events outside a scope. */
    public List<Event> current() {
        Deque<Scope> scopes = scopeStarts.get();
        return scopes.isEmpty() ? all() : since(scopes.peek().start());
    }

    /** Return the current scope's call ID, or {@code null} outside a scope. */
    public UUID currentCallId() {
        Deque<Scope> scopes = scopeStarts.get();
        return scopes.isEmpty() ? null : scopes.peek().callId();
    }

    /** Return the call ID associated with an event, or {@code null} if it was outside a scope. */
    public UUID callId(Event event) {
        return eventCallIds.get(event.id());
    }

    /** Return events associated with a call ID; {@code null} returns an empty list. */
    public List<Event> forCall(UUID callId) {
        if (callId == null) {
            return List.of();
        }
        return events.stream()
            .filter(event -> callId.equals(eventCallIds.get(event.id())))
            .toList();
    }

    /** Remove all events and their call associations. */
    public void clear() {
        events.clear();
        eventCallIds.clear();
    }

    /** Remove events in the half-open range {@code [from, to)}. Invalid ranges are ignored. */
    public void clearRange(int from, int to) {
        if (from < 0 || to > events.size() || from >= to) return;
        var snapshot = new ArrayList<>(events);
        snapshot.subList(from, to).clear();
        events.clear();
        events.addAll(snapshot);
        eventCallIds.keySet().retainAll(snapshot.stream().map(Event::id).toList());
    }

    /** Insert an event at a specific valid index; the inserted event has no call association. */
    public void insertAt(int index, Event event) {
        var snapshot = new ArrayList<>(events);
        snapshot.add(index, event);
        events.clear();
        events.addAll(snapshot);
        eventCallIds.keySet().retainAll(snapshot.stream().map(Event::id).toList());
    }

    /** Register a listener notified synchronously after each event is appended. */
    public void onEvent(Consumer<Event> listener) {
        listeners.add(listener);
    }

    /** Removes a previously registered listener (no-op if absent). */
    public void removeListener(Consumer<Event> listener) {
        listeners.remove(listener);
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
                default -> {
                    // Lifecycle events do not contribute text to the summary.
                }
            }
        }
        return sb.toString();
    }
}
