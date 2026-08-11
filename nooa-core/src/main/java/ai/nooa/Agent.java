package ai.nooa;

import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Hidden;
import ai.nooa.annotations.NoTrace;
import ai.nooa.annotations.Strategy;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.config.AgentConfig;
import ai.nooa.config.CodeActConfig;
import ai.nooa.context.ContextBlock;
import ai.nooa.runtime.ActorRuntime;
import ai.nooa.runtime.ContextApi;
import ai.nooa.runtime.ContextManager;
import ai.nooa.runtime.EventManager;
import ai.nooa.runtime.EventsApi;
import ai.nooa.agentdoc.AgentDoc;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.security.PermissionCallback;
import ai.nooa.security.Permissions;
import ai.nooa.strategy.CodeActStrategy;
import ai.nooa.strategy.GenerationStrategy;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Base class for all NOOA agents.
 *
 * <p>Extend this class, annotate generation methods with {@code @Generate},
 * and add deterministic helpers as regular methods. The factory
 * {@link AgentFactory} instruments {@code @Generate} methods at creation time.</p>
 *
 * <pre>{@code
 * class MyAgent extends Agent {
 *     private final Database db;
 *
 *     public MyAgent(UnifiedLLM llm, Database db) {
 *         super(llm);
 *         this.db = db;
 *     }
 *
 *     // Deterministic helper — LLM can call this
 *     int getStock(String item) {
 *         return db.count(item);
 *     }
 *
 *     // Generation method — doc comment = prompt, body replaced at runtime
 *     &#64;Generate
 *     public String analyze(String topic) {
 *         throw new UnsupportedOperationException("Generated at runtime");
 *     }
 * }
 * }</pre>
 */
public abstract class Agent implements AutoCloseable {

    @Hidden private final String agentId;
    @Hidden private final UnifiedLLM llm;
    @Hidden private final AgentConfig config;
    @Hidden private final ActorRuntime runtime;
    @Hidden private final ContextManager contextManager;
    @Hidden private final EventManager eventManager;
    @Hidden private final ContextApi contextApi;
    @Hidden private final EventsApi eventsApi;
    @Hidden private Permissions permissions = new Permissions();
    @Hidden private PermissionCallback permissionCallback;

    protected Agent(UnifiedLLM llm) {
        this(llm, AgentConfig.defaults());
    }

    protected Agent(UnifiedLLM llm, AgentConfig config) {
        this.agentId = UUID.randomUUID().toString();
        this.llm = Objects.requireNonNull(llm, "llm must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.eventManager = new EventManager();
        this.contextManager = new ContextManager();
        this.runtime = new ActorRuntime(this, config, llm);
        this.contextApi = new ContextApi(this);
        this.eventsApi = new EventsApi(this);

        registerFrameworkBlocks();
    }

    private void registerFrameworkBlocks() {
        var cm = contextManager;
        cm.registerProtected("system_prompt",
            ContextBlock.Dynamic.class.cast(
                ContextBlock.dynamicBlock("system_prompt", "self.resolveSystemPrompt()")));
        cm.registerProtected("self",
            ContextBlock.Dynamic.class.cast(
                ContextBlock.dynamicBlock("self", "AgentDoc.of(type(self))")));
        cm.registerProtected("state",
            ContextBlock.Dynamic.class.cast(
                ContextBlock.dynamicBlock("state", "AgentDoc.instanceValues(self)")));
    }

    // ---- Public accessors (hidden from LLM by default) ----

    @Hidden public String agentId() { return agentId; }
    @Hidden public UnifiedLLM llm() { return llm; }
    @Hidden public AgentConfig config() { return config; }
    @Hidden public ActorRuntime runtime() { return runtime; }
    @Hidden public ContextManager contextManager() { return contextManager; }
    @Hidden public EventManager eventManager() { return eventManager; }
    @Hidden public ContextApi context() { return contextApi; }
    @Hidden public EventsApi events() { return eventsApi; }

    public void setPermissions(Permissions permissions) {
        this.permissions = permissions;
    }
    public Permissions permissions() { return permissions; }

    public void setPermissionCallback(PermissionCallback callback) {
        this.permissionCallback = callback;
    }
    public PermissionCallback permissionCallback() { return permissionCallback; }

    @Hidden
    public String resolveSystemPrompt() {
        var cls = getClass();
        SystemPrompt ann = cls.getAnnotation(SystemPrompt.class);
        if (ann != null) {
            return runtime.evaluateExpression(ann.value());
        }
        return cls.getSimpleName();
    }

    @Override
    public void close() {
        runtime.close();
    }
}
