package ai.nooa.clad.runtime.engine;

import java.util.Map;

/**
 * Runtime context provided to concept agents.
 * Implemented by the CLAD runtime engine (SyncDispatcher, ActionLog, Storage).
 */
public interface ConceptContext {
    void writeCompletion(ActionRecord invocation, Map<String, String> outputs);
    void writeRefusal(ActionRecord invocation, String reason);
    void writeError(ActionRecord invocation, String message);
}
