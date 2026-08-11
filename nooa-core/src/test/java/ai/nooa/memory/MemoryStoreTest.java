package ai.nooa.memory;

import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MemoryStore")
class MemoryStoreTest {

    private Path tempDb;
    private MemoryStore store;

    @BeforeEach
    void setUp() throws Exception {
        tempDb = Files.createTempFile("nooa-memory-test", ".db");
        store = new MemoryStore(tempDb.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        store.close();
        Files.deleteIfExists(tempDb);
    }

    @Test
    @DisplayName("write and retrieve a memory record")
    void writeAndRetrieve() {
        var record = MemoryRecord.create("agent-1", "fact",
            "User prefers dark mode", 0.8, List.of("preference", "ui"));
        store.write(record);

        var retrieved = store.get(record.id().toString());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().content()).isEqualTo("User prefers dark mode");
        assertThat(retrieved.get().type()).isEqualTo("fact");
        assertThat(retrieved.get().importance()).isCloseTo(0.8, within(0.01));
    }

    @Test
    @DisplayName("query by owner returns active records")
    void queryByOwner() {
        store.write(MemoryRecord.create("agent-1", "fact", "A", 0.9, List.of("t1")));
        store.write(MemoryRecord.create("agent-1", "episode", "B", 0.5, List.of("t2")));
        store.write(MemoryRecord.create("agent-2", "fact", "C", 0.7, List.of("t1")));

        var results = store.query("agent-1", null, null, 10);
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("query filters by type")
    void queryByType() {
        store.write(MemoryRecord.create("agent-1", "fact", "A", 0.9, List.of()));
        store.write(MemoryRecord.create("agent-1", "episode", "B", 0.5, List.of()));

        var facts = store.query("agent-1", "fact", null, 10);
        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).type()).isEqualTo("fact");
    }

    @Test
    @DisplayName("recall by tags returns intersection matches")
    void recallByTags() {
        store.write(MemoryRecord.create("agent-1", "fact", "A", 0.9,
            List.of("preference", "ui")));
        store.write(MemoryRecord.create("agent-1", "fact", "B", 0.5,
            List.of("security")));
        store.write(MemoryRecord.create("agent-1", "fact", "C", 0.7,
            List.of("preference")));

        var results = store.recall("agent-1", List.of("preference"), 10);
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("forget deactivates a record")
    void forgetDeactivates() {
        var record = MemoryRecord.create("agent-1", "fact", "test", 0.5, List.of());
        store.write(record);
        store.forget(record.id().toString());

        var retrieved = store.get(record.id().toString());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().active()).isFalse();
    }

    @Test
    @DisplayName("records are ordered by importance descending")
    void orderedByImportance() {
        store.write(MemoryRecord.create("agent-1", "fact", "Low", 0.1, List.of()));
        store.write(MemoryRecord.create("agent-1", "fact", "High", 0.9, List.of()));
        store.write(MemoryRecord.create("agent-1", "fact", "Mid", 0.5, List.of()));

        var results = store.query("agent-1", null, null, 10);
        assertThat(results).hasSize(3);
        assertThat(results.get(0).importance()).isGreaterThan(results.get(1).importance());
        assertThat(results.get(1).importance()).isGreaterThan(results.get(2).importance());
    }

    @Test
    @DisplayName("relationship links two records")
    void relationships() {
        var r1 = MemoryRecord.create("agent-1", "fact", "Python is popular", 0.8, List.of("lang"));
        var r2 = MemoryRecord.create("agent-1", "fact", "Java is fast", 0.7, List.of("lang"));
        store.write(r1);
        store.write(r2);
        store.write(r2.withRelationship("related-to", r1.id().toString()));

        var retrieved = store.get(r2.id().toString());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().relationships()).containsEntry("related-to", r1.id().toString());
    }

    @Test
    @DisplayName("reflect prunes low-importance stale records")
    void reflectPrunes() throws Exception {
        // Write a low-importance record with an old timestamp
        var old = new MemoryRecord(java.util.UUID.randomUUID(),
            java.time.Instant.now().minus(java.time.Duration.ofDays(60)),
            java.time.Instant.now().minus(java.time.Duration.ofDays(60)),
            "agent-1", "fact", "stale", 0.1, List.of(), java.util.Map.of(), true);
        store.write(old);
        var fresh = MemoryRecord.create("agent-1", "fact", "fresh", 0.9, List.of());
        store.write(fresh);

        store.reflect("agent-1");

        var oldRetrieved = store.get(old.id().toString());
        assertThat(oldRetrieved).isPresent();
        assertThat(oldRetrieved.get().active()).isFalse();

        var freshRetrieved = store.get(fresh.id().toString());
        assertThat(freshRetrieved).isPresent();
        assertThat(freshRetrieved.get().active()).isTrue();
    }
}
