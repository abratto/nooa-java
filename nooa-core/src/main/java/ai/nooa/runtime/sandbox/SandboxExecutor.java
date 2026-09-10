package ai.nooa.runtime.sandbox;

import ai.nooa.Agent;
import ai.nooa.strategy.ExecutionResult;

/**
 * Pluggable execution boundary for CodeAct-generated code.
 *
 * <p>The default implementation runs in-process. Applications that execute
 * untrusted code can provide a factory backed by a worker process, container,
 * VM, or remote execution service.</p>
 */
public interface SandboxExecutor extends AutoCloseable {

    /** Bind a typed value for later code execution. */
    void bindVariable(String name, String typeName, Object value);

    /** Execute generated code and return its structured result. */
    ExecutionResult execute(String code);

    /** Release resources owned by this executor. */
    @Override
    void close();

    /** Creates an executor for a specific agent instance. */
    @FunctionalInterface
    interface Factory {
        SandboxExecutor create(Agent agent);
    }
}