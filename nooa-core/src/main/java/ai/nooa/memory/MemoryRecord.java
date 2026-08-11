package ai.nooa.memory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A typed memory record. Persisted in SQLite.
 */
public record MemoryRecord(
    UUID id,
    Instant createdAt,
    Instant updatedAt,
    String owner,           // agent ID that owns this record
    String type,            // "fact", "episode", "insight", "preference"
    String content,         // human-readable text
    double importance,      // 0.0 - 1.0, higher = more likely to surface
    List<String> tags,
    Map<String, String> relationships,  // relType -> targetRecordId
    boolean active
) {
    public MemoryRecord withContent(String newContent) {
        return new MemoryRecord(id, createdAt, Instant.now(), owner, type,
            newContent, importance, tags, relationships, active);
    }

    public MemoryRecord withImportance(double v) {
        return new MemoryRecord(id, createdAt, Instant.now(), owner, type,
            content, Math.clamp(v, 0.0, 1.0), tags, relationships, active);
    }

    public MemoryRecord withRelationship(String relType, String targetId) {
        var rels = new java.util.LinkedHashMap<>(relationships);
        rels.put(relType, targetId);
        return new MemoryRecord(id, createdAt, Instant.now(), owner, type,
            content, importance, tags, rels, active);
    }

    public MemoryRecord deactivate() {
        return new MemoryRecord(id, createdAt, Instant.now(), owner, type,
            content, importance, tags, relationships, false);
    }

    public static MemoryRecord create(String owner, String type, String content,
                                       double importance, List<String> tags) {
        return new MemoryRecord(UUID.randomUUID(), Instant.now(), Instant.now(),
            owner, type, content, Math.clamp(importance, 0.0, 1.0),
            tags, Map.of(), true);
    }
}
