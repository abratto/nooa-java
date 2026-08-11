package ai.nooa.tools;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory task tracking for agents. Generated code can manage a
 * structured task list.
 *
 * <pre>{@code
 * var todos = new TodoManager();
 * todos.add("Write tests", "pending");
 * todos.markCompleted(todos.active().get(0).id());
 * }</pre>
 */
public final class TodoManager {

    public enum Status { PENDING, IN_PROGRESS, COMPLETED, CANCELLED }

    public record Task(UUID id, String title, Status status, String notes,
                        Instant createdAt, Instant updatedAt) {
        public Task withStatus(Status s) {
            return new Task(id, title, s, notes, createdAt, Instant.now());
        }
        public Task withNotes(String n) {
            return new Task(id, title, status, n, createdAt, Instant.now());
        }
    }

    private final Map<UUID, Task> tasks = new ConcurrentHashMap<>();

    public Task add(String title, String priority) {
        var task = new Task(UUID.randomUUID(), title,
            Status.PENDING, "", Instant.now(), Instant.now());
        tasks.put(task.id(), task);
        return task;
    }

    public Task update(UUID id, Status status, String notes) {
        var task = tasks.get(id);
        if (task == null) throw new NoSuchElementException("Task not found: " + id);
        var updated = task;
        if (status != null) updated = updated.withStatus(status);
        if (notes != null) updated = updated.withNotes(notes);
        tasks.put(id, updated);
        return updated;
    }

    public void markCompleted(UUID id) { update(id, Status.COMPLETED, null); }
    public void markCancelled(UUID id) { update(id, Status.CANCELLED, null); }
    public void markInProgress(UUID id) { update(id, Status.IN_PROGRESS, null); }
    public void delete(UUID id) { tasks.remove(id); }

    public Optional<Task> get(UUID id) { return Optional.ofNullable(tasks.get(id)); }
    public List<Task> all() { return List.copyOf(tasks.values()); }

    public List<Task> active() {
        return tasks.values().stream()
            .filter(t -> t.status() != Status.COMPLETED && t.status() != Status.CANCELLED)
            .toList();
    }

    public List<Task> byStatus(Status status) {
        return tasks.values().stream().filter(t -> t.status() == status).toList();
    }

    public String showActive() {
        var sb = new StringBuilder("Active tasks:\n");
        var active = active();
        if (active.isEmpty()) { sb.append("  (none)\n"); return sb.toString(); }
        for (var t : active) {
            sb.append("  [").append(t.status().name().charAt(0)).append("] ")
              .append(t.title()).append("\n");
        }
        return sb.toString();
    }

    public int size() { return tasks.size(); }
}
