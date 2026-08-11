package ai.nooa.runtime;

import ai.nooa.context.Event;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicInteger;

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
    @DisplayName("Listeners are notified on event add")
    void listenersNotified() {
        var em = new EventManager();
        var counter = new AtomicInteger(0);
        em.onEvent(e -> counter.incrementAndGet());
        em.add(new Event.Task("test"));
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
}
