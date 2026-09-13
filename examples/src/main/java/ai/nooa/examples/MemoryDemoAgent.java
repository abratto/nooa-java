package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.memory.MemorySkill;
import ai.nooa.memory.MemoryStore;
import java.util.List;
import java.util.Locale;

/**
 * Durable, SQLite-backed memory: write, recall, relate, forget, reflect.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>{@link MemoryStore} — a human-readable SQLite file
 *       ({@code .nooa-demo-memory.db}) that outlives the agent instance.</li>
 *   <li>{@link MemorySkill} — the agent-callable capability pack wrapping the
 *       store; generated code could reach it via
 *       {@code __agent__.memory().write(...)} just as this demo calls it from
 *       Java.</li>
 *   <li>Typed records — {@code preference}, {@code fact}, {@code episode}, and
 *       {@code insight} entries with importance scores and tags.</li>
 *   <li>Tag-based recall — {@link MemorySkill#recall(List, int)} ranks by
 *       importance within a tag intersection.</li>
 *   <li>Relationships — {@link MemorySkill#relate(String, String, String)}
 *       links records ({@code supports}, {@code contradicts}, ...).</li>
 *   <li>Soft delete — {@link MemorySkill#forget(String)} deactivates a record
 *       without destroying history.</li>
 *   <li>Reflection — {@link MemorySkill#reflect()} merges duplicates, links
 *       records, and prunes stale entries; {@code scheduleReflection(300)}
 *       runs it periodically in the background.</li>
 *   <li>Resource lifecycle — {@link #close()} releases the store; the demo
 *       also shows a {@code @Generate} method ({@code analyze}) that could
 *       consume recalled context.</li>
 * </ul>
 *
 * <p><b>Run</b> (Java-only; no model call is made):
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.MemoryDemo
 * }</pre>
 * Creates {@code .nooa-demo-memory.db} in the working directory; delete it
 * after the run if you do not want the state to persist.</p>
 */
public class MemoryDemoAgent extends Agent {
    private static final String PREFERENCE_TAG = "preference";
    private final MemorySkill memory;

    public MemoryDemoAgent(UnifiedLLM llm) {
        super(llm);
        var store = new MemoryStore(".nooa-demo-memory.db");
        store.scheduleReflection(300);
        this.memory = new MemorySkill(this, store);
        ExampleLLM.tune(this, "low", 1024);
    }

    void seedMemories() {
        memory.write(PREFERENCE_TAG, "User prefers dark mode in editor", 0.8,
            List.of(PREFERENCE_TAG, "ui", "editor"));
        memory.write("fact", "Project uses Java 21 with virtual threads", 0.9,
            List.of("tech", "java", "project"));
        memory.write("episode", "Fixed NullPointerException in AuthService.login()", 0.7,
            List.of("bugfix", "auth", "java"));
        memory.write("insight", "Virtual threads eliminated 90% of CompletableFuture usage", 0.85,
            List.of("tech", "java", "performance"));
        memory.write(PREFERENCE_TAG, "User wants error messages in plain English, not stacktraces", 0.6,
            List.of(PREFERENCE_TAG, "ui", "error"));
    }

    void demonstrate() {
        System.out.println("=== Memory lifecycle ===");
        seedMemories();

        var techMemories = memory.recall(List.of("tech", "java"), 5);
        System.out.println("\nTech memories (" + techMemories.size() + "):");
        for (var m : techMemories) {
            var importance = String.format(Locale.ROOT, "%.2f", m.importance());
            System.out.println("  [" + m.type() + "] " + m.content() + " (importance: " + importance + ")");
        }

        var preferences = memory.query(PREFERENCE_TAG, null, 10);
        System.out.println("\nPreferences (" + preferences.size() + "):");
        preferences.forEach(p -> System.out.println("  - " + p.content()));

        if (techMemories.size() >= 2) {
            var r1 = techMemories.getFirst();
            var r2 = techMemories.get(1);
            memory.relate(r1.id().toString(), "supports", r2.id().toString());
            System.out.println("\nLinked: " + r1.type() + " → supports → " + r2.type());
        }

        if (!preferences.isEmpty()) {
            var toForget = preferences.getFirst();
            memory.forget(toForget.id().toString());
            var stillActive = memory.recall(List.of(PREFERENCE_TAG), 5);
            System.out.println("\nAfter forget: " + stillActive.size() + " active preferences (was " + preferences.size() + ")");
        }

        memory.reflect();
        var allActive = memory.query(null, null, 20);
        System.out.println("\nActive records after reflection: " + allActive.size());
    }

    public MemorySkill memory() { return memory; }

    @Override public void close() { memory.close(); super.close(); }

    @Generate(prompt = "Answer the input using relevant remembered context. Prefer stored facts over guesses and keep the response concise.")
    public String analyze(String input) {
        throw new UnsupportedOperationException();
    }
}
