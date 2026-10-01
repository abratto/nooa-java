package ai.nooa.eval;

import ai.nooa.memory.MemoryStore;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only view of an agent's post-run state. Backed by reflection over the
 * agent's fields and context blocks, optionally merged with a user-supplied
 * {@link EnvironmentSnapshot}.
 */
public interface PostState {

    /** Value of a named state field, if present. */
    Optional<Object> field(String name);

    /** All state fields as an immutable map. */
    Map<String, Object> fields();

    /** Memory store, when the agent exposes one. */
    Optional<MemoryStore> memory();

    /** Workspace path, when the environment snapshot provides one. */
    Optional<Path> workspace();
}
