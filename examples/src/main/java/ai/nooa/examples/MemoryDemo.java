package ai.nooa.examples;

/** Runs the SQLite memory lifecycle demonstration. */
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