package ai.nooa.context;

/**
 * A block of text rendered into the system prompt.
 * Static blocks are evaluated once; dynamic blocks re-evaluate each turn.
 */
public sealed interface ContextBlock
    permits ContextBlock.Static, ContextBlock.Dynamic, ContextBlock.Structured {

    String key();

    /** Static content, evaluated once at assignment time. */
    record Static(String key, String value) implements ContextBlock {}

    /** Dynamic content, re-evaluated each LLM turn from an expression. */
    record Dynamic(String key, String expression) implements ContextBlock {}

    /** Structured value rendered with a stable bounded representation. */
    record Structured(String key, Object value, String role) implements ContextBlock {
        public Structured(String key, Object value) { this(key, value, "system"); }
    }

    static ContextBlock staticBlock(String key, String value) {
        return new Static(key, value);
    }

    static ContextBlock dynamicBlock(String key, String expression) {
        return new Dynamic(key, expression);
    }

    static ContextBlock structuredBlock(String key, Object value) {
        return new Structured(key, value);
    }
}
