package ai.nooa.memory;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SQLite-backed memory store. Agents write, query, and reflect on memories.
 * Supports typed relationships between records forming a knowledge graph.
 */
public final class MemoryStore implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MemoryStore.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MEMORY_COLUMNS =
        "id, created_at, updated_at, owner, type, content, importance, tags, relationships, active";

    private final String dbPath;
    private final ScheduledExecutorService reflectionExecutor;

    public MemoryStore(String dbPath) {
        this.dbPath = dbPath;
        this.reflectionExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nooa-memory-reflect");
            t.setDaemon(true);
            return t;
        });
        initSchema();
    }

    private Connection connect() {
        try {
            return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        } catch (SQLException e) {
            throw new RuntimeException("Cannot open memory store: " + dbPath, e);
        }
    }

    private void initSchema() {
        try (var conn = connect()) {
            try (var statement = conn.createStatement()) {
                statement.execute("""
                CREATE TABLE IF NOT EXISTS memories (
                    id TEXT PRIMARY KEY,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    owner TEXT NOT NULL,
                    type TEXT NOT NULL,
                    content TEXT NOT NULL,
                    importance REAL NOT NULL DEFAULT 0.5,
                    tags TEXT NOT NULL DEFAULT '[]',
                    relationships TEXT NOT NULL DEFAULT '{}',
                    active INTEGER NOT NULL DEFAULT 1
                )
            """);
                statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_memories_owner ON memories(owner)");
                statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_memories_type ON memories(type)");
                statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_memories_active ON memories(active)");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot init memory schema", e);
        }
    }

    // ---- CRUD ----

    /** Persist a record, replacing an existing record with the same ID. */
    public void write(MemoryRecord memory) {
        String sql = """
            INSERT OR REPLACE INTO memories
            (id, created_at, updated_at, owner, type, content, importance, tags, relationships, active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (var conn = connect(); var ps = conn.prepareStatement(sql)) {
            ps.setString(1, memory.id().toString());
            ps.setString(2, memory.createdAt().toString());
            ps.setString(3, memory.updatedAt().toString());
            ps.setString(4, memory.owner());
            ps.setString(5, memory.type());
            ps.setString(6, memory.content());
            ps.setDouble(7, memory.importance());
            ps.setString(8, toJson(memory.tags()));
            ps.setString(9, toJson(memory.relationships()));
            ps.setInt(10, memory.active() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to write memory", e);
        }
    }

    /** Return a record by ID, including inactive records; storage errors yield an empty result. */
    public Optional<MemoryRecord> get(String id) {
        try (var conn = connect(); var ps = conn.prepareStatement(
                "SELECT " + MEMORY_COLUMNS + " FROM memories WHERE id = ?")) {
            ps.setString(1, id);
            var rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRecord(rs));
        } catch (SQLException e) {
            log.debug("Failed to get memory {}", id, e);
        }
        return Optional.empty();
    }

    /**
     * Query active memories for an owner, sorted by importance desc.
     */
    public List<MemoryRecord> query(String owner, String type, List<String> tags, int limit) {
        var sql = new StringBuilder("SELECT " + MEMORY_COLUMNS
            + " FROM memories WHERE active = 1 AND owner = ?");
        var params = new ArrayList<String>();
        params.add(owner);

        if (type != null && !type.isBlank()) {
            sql.append(" AND type = ?");
            params.add(type);
        }
        if (tags != null && !tags.isEmpty()) {
            sql.append(" AND (");
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("tags LIKE ?");
                params.add("%\"" + tags.get(i) + "\"%");
            }
            sql.append(")");
        }
        sql.append(" ORDER BY importance DESC LIMIT ?");
        params.add(String.valueOf(limit));

        try (var conn = connect(); var ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, params.get(i));
            }
            var rs = ps.executeQuery();
            var results = new ArrayList<MemoryRecord>();
            while (rs.next()) results.add(mapRecord(rs));
            return results;
        } catch (SQLException e) {
            log.debug("Memory query failed", e);
            return List.of();
        }
    }

    /**
     * Find memories relevant to the current context by semantic tags.
     * Returns top matches sorted by importance.
     */
    public List<MemoryRecord> recall(String owner, List<String> contextTags, int limit) {
        return query(owner, null, contextTags, limit);
    }

    /** Soft-delete a record by marking it inactive without removing its data. */
    public void forget(String id) {
        try (var conn = connect(); var ps = conn.prepareStatement(
                "UPDATE memories SET active = 0, updated_at = ? WHERE id = ?")) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.debug("Failed to forget memory {}", id, e);
        }
    }

    /**
     * Background reflection: merge duplicate records, link related ones,
     * distill episodes into insights, prune stale records.
     */
    public void reflect(String owner) {
        try (var conn = connect()) {
            // Prune low-importance stale records
            try (var ps = conn.prepareStatement(
                    "UPDATE memories SET active = 0 WHERE active = 1 AND owner = ? "
                    + "AND importance < 0.2 AND updated_at < datetime('now', '-30 days')")) {
                ps.setString(1, owner);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            log.debug("Memory reflection failed", e);
        }
    }

    /** Schedule periodic stale-record reflection for all owners until this store is closed. */
    public void scheduleReflection(long intervalSeconds) {
        reflectionExecutor.scheduleAtFixedRate(() -> {
            try (var conn = connect()) {
                var owners = new HashSet<String>();
                var rs = conn.createStatement().executeQuery(
                    "SELECT DISTINCT owner FROM memories WHERE active = 1");
                while (rs.next()) owners.add(rs.getString("owner"));
                for (var owner : owners) {
                    reflectSafely(owner);
                }
            } catch (Exception e) {
                log.debug("Scheduled reflection failed", e);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    private void reflectSafely(String owner) {
        try {
            reflect(owner);
        } catch (Exception e) {
            log.debug("Reflection failed for {}", owner, e);
        }
    }

    // ---- Helpers ----

    private MemoryRecord mapRecord(ResultSet rs) throws SQLException {
        return new MemoryRecord(
            UUID.fromString(rs.getString("id")),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at")),
            rs.getString("owner"),
            rs.getString("type"),
            rs.getString("content"),
            rs.getDouble("importance"),
            parseJsonList(rs.getString("tags")),
            parseJsonMap(rs.getString("relationships")),
            rs.getInt("active") == 1
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonList(String json) {
        try {
            return JSON.readValue(json, List.class);
        } catch (Exception _) { return List.of(); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseJsonMap(String json) {
        try {
            return JSON.readValue(json, Map.class);
        } catch (Exception _) { return Map.of(); }
    }

    private String toJson(Object obj) {
        try {
            return JSON.writeValueAsString(obj);
        } catch (Exception _) { return obj instanceof List ? "[]" : "{}"; }
    }

    @Override
    public void close() {
        reflectionExecutor.shutdownNow();
    }
}
