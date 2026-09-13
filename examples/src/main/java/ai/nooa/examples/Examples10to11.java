package ai.nooa.examples;

/**
 * Compatibility launcher for the former combined memory and MCP entry point.
 * Prefer the focused demo classes listed in the examples README.
 *
 * <p>Kept so that older commands and notes continue to produce a helpful
 * message instead of a {@code ClassNotFoundException}. No model call, no side
 * effects.</p>
 */
public final class Examples10to11 {
    private Examples10to11() {}

    public static void main(String[] args) {
        System.out.println("Use focused demos: MemoryDemo and McpDemo.");
    }
}
