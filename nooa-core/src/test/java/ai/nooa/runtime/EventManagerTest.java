package ai.nooa.runtime;

import ai.nooa.context.Event;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EventManagerTest {

    @Test
    @DisplayName("Events are appended and retrievable")
    void appendsAndRetrieves() {
        var em = new EventManager();
        em.add(new Event.Task("task 1"));
        em.add(new Event.Task("task 2"));
        assertThat(em.size()).isEqualTo(2);
        assertThat(em.all()).hasSize(2);
    }

    @Test
    @DisplayName("since() returns events after index")
    void sinceReturnsAfterIndex() {
        var em = new EventManager();
        em.add(new Event.Task("task 1"));
        em.add(new Event.Task("task 2"));
        em.add(new Event.Task("task 3"));
        var since = em.since(1);
        assertThat(since).hasSize(2);
        assertThat(((Event.Task) since.get(0)).content()).isEqualTo("task 2");
    }

    @Test
    @DisplayName("since clamps negative indexes and returns empty at the end")
    void sinceHandlesBoundaries() {
        var em = new EventManager();
        em.add(new Event.Task("task"));

        assertThat(em.since(-1)).hasSize(1);
        assertThat(em.since(1)).isEmpty();
        assertThat(em.since(2)).isEmpty();
    }

    @Test
    @DisplayName("Listeners are notified on event add")
    void listenersNotified() {
        var em = new EventManager();
        var counter = new AtomicInteger(0);
        java.util.function.Consumer<Event> listener = e -> counter.incrementAndGet();
        em.onEvent(listener);
        em.add(new Event.Task("test"));
        em.removeListener(listener);
        em.add(new Event.Task("ignored"));
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("eventsSince returns correct count delta")
    void eventsSinceCount() {
        var em = new EventManager();
        em.add(new Event.Task("a"));
        int idx = em.size();
        em.add(new Event.Task("b"));
        em.add(new Event.Task("c"));
        assertThat(em.eventsSince(idx)).isEqualTo(2);
    }

    @Test
    @DisplayName("toMessages converts events to LLM messages")
    void convertsToMessages() {
        var em = new EventManager();
        em.add(new Event.Task("hello"));
        em.add(new Event.LLMOutput("world"));
        em.add(new Event.ErrorEvent("oops"));
        var msgs = em.toMessages();
        assertThat(msgs).hasSize(3);
        assertThat(msgs.get(0).content()).isEqualTo("hello");
        assertThat(msgs.get(1).content()).isEqualTo("world");
        assertThat(msgs.get(2).content()).contains("oops");
    }

    @Test
    @DisplayName("clear empties all events")
    void clearEmpties() {
        var em = new EventManager();
        em.add(new Event.Task("test"));
        em.clear();
        assertThat(em.size()).isZero();
    }

    @Test
    @DisplayName("range clearing and insertion preserve event order")
    void rangeClearingAndInsertion() {
        var em = new EventManager();
        em.add(new Event.Task("first"));
        em.add(new Event.Task("remove"));
        em.add(new Event.Task("last"));

        em.clearRange(1, 2);
        em.insertAt(1, new Event.Feedback("middle"));

        assertThat(em.toMessages()).extracting("content")
            .containsExactly("first", "middle", "last");
    }

    @Test
    @DisplayName("current scope excludes events from prior calls")
    void currentScopeIsNestedAndRestoresParent() {
        var em = new EventManager();
        em.add(new Event.Task("prior"));
        em.beginScope();
        em.add(new Event.Task("outer"));
        em.beginScope();
        em.add(new Event.Task("inner"));

        assertThat(em.current()).extracting(Event::role)
            .containsExactly("user");
        assertThat(((Event.Task) em.current().get(0)).content()).isEqualTo("inner");

        em.endScope();
        assertThat(em.current()).extracting(Event.class::cast)
            .extracting(event -> ((Event.Task) event).content())
            .containsExactly("outer", "inner");
        em.endScope();
        assertThat(em.current()).extracting(Event.class::cast)
            .extracting(event -> ((Event.Task) event).content())
            .containsExactly("prior", "outer", "inner");
    }

    @Test
    @DisplayName("events retain their generated call identity")
    void eventsRetainCallIdentity() {
        var em = new EventManager();
        UUID firstCall = UUID.randomUUID();
        UUID nestedCall = UUID.randomUUID();

        em.beginScope(firstCall);
        Event.Task first = new Event.Task("first");
        em.add(first);
        em.beginScope(nestedCall);
        Event.Task nested = new Event.Task("nested");
        em.add(nested);

        assertThat(em.currentCallId()).isEqualTo(nestedCall);
        assertThat(em.callId(first)).isEqualTo(firstCall);
        assertThat(em.callId(nested)).isEqualTo(nestedCall);
        assertThat(em.forCall(firstCall)).containsExactly(first);
        assertThat(em.forCall(nestedCall)).containsExactly(nested);

        em.endScope();
        em.endScope();
    }
}
