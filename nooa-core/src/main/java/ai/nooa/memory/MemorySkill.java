package ai.nooa.memory;

import ai.nooa.Agent;

import java.util.List;

/**
 * Agent-callable memory skill. Exposes write/recall/forget/reflect to the LLM.
 * Attach to an agent via: {@code new MemorySkill(agent, store)}.
 *
 * <p>Usage from generated code:
 * <pre>{@code
 * memory().write("fact", "User prefers dark mode", 0.8, List.of("preference", "ui"));
 * var relevant = memory().recall(List.of("preference"), 5);
 * memory().reflect();
 * }</pre>
 */
public final class MemorySkill {

    private final Agent agent;
    private final MemoryStore store;

    public MemorySkill(Agent agent, MemoryStore store) {
        this.agent = agent;
        this.store = store;
    }

    /** Write a new memory record. */
    public MemoryRecord write(String type, String content, double importance, List<String> tags) {
        var record = MemoryRecord.create(agent.agentId(), type, content, importance, tags);
        store.write(record);
        return record;
    }

    /** Find relevant memories by tag intersection, ordered by importance. */
    public List<MemoryRecord> recall(List<String> contextTags, int limit) {
        return store.recall(agent.agentId(), contextTags, limit);
    }

    /** Query memories with optional type filter and tag filter. */
    public List<MemoryRecord> query(String type, List<String> tags, int limit) {
        return store.query(agent.agentId(), type, tags, limit);
    }

    /** Update a memory's content or importance. */
    public void update(String id, String newContent, Double newImportance) {
        var opt = store.get(id);
        if (opt.isEmpty()) return;
        var record = opt.get();
        if (newContent != null) record = record.withContent(newContent);
        if (newImportance != null) record = record.withImportance(newImportance);
        store.write(record);
    }

    /** Link two memories with a typed relationship. */
    public void relate(String sourceId, String relationship, String targetId) {
        var opt = store.get(sourceId);
        if (opt.isEmpty()) return;
        store.write(opt.get().withRelationship(relationship, targetId));
    }

    /** Mark a memory as inactive (soft delete). */
    public void forget(String id) {
        store.forget(id);
    }

    /** Run reflection: merge duplicates, link records, prune stale. */
    public void reflect() {
        store.reflect(agent.agentId());
    }

    /** Schedule automatic periodic reflection. */
    public void scheduleReflection(long intervalSeconds) {
        store.scheduleReflection(intervalSeconds);
    }

    public void close() { store.close(); }
}
