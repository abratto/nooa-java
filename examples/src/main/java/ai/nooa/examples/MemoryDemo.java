package ai.nooa.examples;

/**
 * Runs the SQLite memory lifecycle demonstration defined by
 * {@link MemoryDemoAgent}.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>The full {@link ai.nooa.memory.MemorySkill} lifecycle — seed, recall
 *       by tags, query by type, relate records, soft-delete, and reflect.</li>
 *   <li>Direct construction — unlike the other demos this one instantiates the
 *       agent directly because it exercises deterministic memory APIs, not
 *       {@code @Generate} interception.</li>
 * </ul>
 *
 * <p><b>Run</b> (Java-only; no model call):
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.MemoryDemo
 * }</pre>
 * or {@code examples/run.sh MemoryDemo}. Creates
 * {@code .nooa-demo-memory.db} in the working directory; delete it after the
 * run if you want a clean slate (memory is durable by design).</p>
 */
public final class MemoryDemo {
    private MemoryDemo() {}

    public static void main(String[] args) {
        var agent = new MemoryDemoAgent(ExampleLLM.create());
        try {
            agent.demonstrate();
        } finally {
            agent.close();
        }
    }
}
