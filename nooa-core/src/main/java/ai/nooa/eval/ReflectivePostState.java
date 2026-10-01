package ai.nooa.eval;

import ai.nooa.Agent;
import ai.nooa.context.ContextBlock;
import ai.nooa.memory.MemoryStore;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Default {@link PostState}: reflects over the agent's instance fields (across
 * the class hierarchy), adds static/structured context block values, and
 * finally merges any {@link EnvironmentSnapshot} supplied by the caller.
 */
public final class ReflectivePostState implements PostState {

    private final Map<String, Object> fields;
    private final MemoryStore memory;
    private final Path workspace;

    public ReflectivePostState(Agent agent, EnvironmentSnapshot environment) {
        Map<String, Object> values = new LinkedHashMap<>();
        collectReflectedFields(agent, values);
        collectContextBlocks(agent, values);
        MemoryStore memoryStore = findMemoryStore(values);
        Path workspacePath = null;
        if (environment != null) {
            Map<String, Object> snapshot = environment.snapshot();
            if (snapshot != null) {
                values.putAll(snapshot);
            }
        }
        Object ws = values.get("workspace");
        if (ws instanceof Path p) {
            workspacePath = p;
        }
        this.fields = Collections.unmodifiableMap(values);
        this.memory = memoryStore;
        this.workspace = workspacePath;
    }

    private static void collectReflectedFields(Agent agent, Map<String, Object> values) {
        for (Class<?> cls = agent.getClass(); cls != null && cls != Object.class;
                cls = cls.getSuperclass()) {
            for (Field field : cls.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    values.putIfAbsent(field.getName(), field.get(agent));
                } catch (ReflectiveOperationException | RuntimeException _) {
                    // Skip fields that cannot be read (module access, etc.).
                }
            }
        }
    }

    private static void collectContextBlocks(Agent agent, Map<String, Object> values) {
        try {
            for (ContextBlock block : agent.contextManager().allBlocks().values()) {
                switch (block) {
                    case ContextBlock.Static s -> values.putIfAbsent(s.key(), s.value());
                    case ContextBlock.Structured s -> values.putIfAbsent(s.key(), s.value());
                    case ContextBlock.Dynamic _ -> { }
                }
            }
        } catch (RuntimeException _) {
            // Context rendering may fail on a partially torn-down agent; ignore.
        }
    }

    private static MemoryStore findMemoryStore(Map<String, Object> values) {
        for (Object value : values.values()) {
            if (value instanceof MemoryStore store) {
                return store;
            }
        }
        return null;
    }

    @Override
    public Optional<Object> field(String name) {
        return Optional.ofNullable(fields.get(name));
    }

    @Override
    public Map<String, Object> fields() {
        return fields;
    }

    @Override
    public Optional<MemoryStore> memory() {
        return Optional.ofNullable(memory);
    }

    @Override
    public Optional<Path> workspace() {
        return Optional.ofNullable(workspace);
    }
}
