package ai.nooa.cli;

import java.util.*;
import java.util.concurrent.*;

/**
 * Queue-based message manager for interactive agents.
 * Routes user input, slash commands, and system events to the agent.
 */
public final class QueueManager {

    private final BlockingQueue<QueueItem> queue = new LinkedBlockingQueue<>();
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();

    public record QueueItem(String channel, Object payload, long timestamp) {
        public QueueItem(String channel, Object payload) {
            this(channel, payload, System.currentTimeMillis());
        }
    }

    /** Submit a message to the default channel. */
    public void submit(Object payload) {
        queue.add(new QueueItem("default", payload));
    }

    /** Submit a message to a specific channel. */
    public void submit(String channel, Object payload) {
        queue.add(new QueueItem(channel, payload));
    }

    /** Get or create a named channel. */
    public Channel channel(String name) {
        return channels.computeIfAbsent(name, Channel::new);
    }

    /** Drain all pending messages. */
    public List<QueueItem> drain() {
        List<QueueItem> items = new ArrayList<>();
        queue.drainTo(items);
        return items;
    }

    /** Wait for the next message with optional timeout. */
    public Optional<QueueItem> poll(long timeoutMs) {
        try {
            return Optional.ofNullable(queue.poll(timeoutMs, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** Check if any channels have pending data. */
    public boolean hasPending() {
        return !queue.isEmpty();
    }

    public int size() { return queue.size(); }

    // ---- Channel ----

    public static class Channel {
        private final String name;
        private final BlockingQueue<Object> pending = new LinkedBlockingQueue<>();

        Channel(String name) { this.name = name; }

        public String name() { return name; }

        public void send(Object message) { pending.add(message); }

        public Optional<Object> receive(long timeoutMs) {
            try {
                return Optional.ofNullable(pending.poll(timeoutMs, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }

        public List<Object> drain() {
            List<Object> items = new ArrayList<>();
            pending.drainTo(items);
            return items;
        }

        public boolean isEmpty() { return pending.isEmpty(); }
        public int size() { return pending.size(); }
    }
}
