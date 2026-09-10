package ai.nooa.runtime;

import ai.nooa.Agent;
import ai.nooa.NooaException;
import ai.nooa.annotations.NoTrace;
import ai.nooa.config.AgentConfig;
import ai.nooa.context.ContextWindowStats;
import ai.nooa.context.Event;
import ai.nooa.llm.LLMResponse;
import ai.nooa.llm.Message;
import ai.nooa.llm.Tool;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.runtime.sandbox.SandboxExecutor;
import ai.nooa.strategy.CurrentCall;
import ai.nooa.strategy.ExecutionResult;
import ai.nooa.strategy.GenerationStrategy;
import ai.nooa.strategy.MethodConditions;
import ai.nooa.strategy.RuntimeServices;
import ai.nooa.tracing.Tracing;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Type;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Execution engine for agent methods. Uses virtual threads for LLM
 * and code execution so @Generate methods can be synchronous.
 */
public final class ActorRuntime implements RuntimeServices, AutoCloseable {


    private final Agent agent;
    private final AgentConfig config;
    private final UnifiedLLM llm;
    private final ReentrantLock generationLock = new ReentrantLock();
    private SandboxExecutor sandbox;
    private ContextWindowStats stats = ContextWindowStats.empty();

    private final InheritableThreadLocal<Boolean> inGenerationSession =
        new InheritableThreadLocal<>() {
            @Override protected Boolean initialValue() { return false; }
        };

    private static final String PROMPT_LOGGING_PROP = "nooa.log.prompts";
    private static final String PROMPT_LOGGING_ENV = "NOOA_LOG_PROMPTS";
    private static final String PROMPT_RAW_PROP = "nooa.log.prompts.raw";
    private static final String PROMPT_RAW_ENV = "NOOA_LOG_PROMPTS_RAW";
    private static final String MAX_ARG_CHARS_PROP = "nooa.prompt.maxArgChars";
    // Large default: local models commonly expose 128k-256k token contexts, and a
    // single @Generate argument (e.g. a stage contract plus input artefacts) can
    // legitimately be tens of thousands of characters. The per-arg cap is a safety
    // valve, not a prompt-size target.
    private static final int DEFAULT_MAX_ARG_CHARS = 262_144;
    private static final Pattern KV_SECRET_PATTERN = Pattern.compile(
        "(?i)(api[_-]?key|token|secret|password)\\s*[:=]\\s*([^\\s,;]+)");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)bearer\\s+([a-z0-9._-]+)");

    public ActorRuntime(Agent agent, AgentConfig config, UnifiedLLM llm) {
        this.agent = agent;
        this.config = config;
        this.llm = llm;
    }

    @Override public Agent agent() { return agent; }
    @Override public EventManager eventManager() { return agent.eventManager(); }
    @Override public String agentId() { return agent.agentId(); }

    @Override
    public LLMResponse generate(List<Tool> tools, Class<?> outputModel, Map<String, Object> samplingParams) {
        return generate(tools, outputModel, samplingParams, null);
    }

    @Override
    public LLMResponse generate(List<Tool> tools, Type outputModel,
                                Map<String, Object> samplingParams) {
        return generate(tools, outputModel, samplingParams, null);
    }

    @Override
    public LLMResponse generate(List<Tool> tools, Class<?> outputModel, Map<String, Object> samplingParams, String systemPromptSupplement) {
        return generateWithType(tools, outputModel, samplingParams, systemPromptSupplement);
    }

    @Override
    public LLMResponse generate(List<Tool> tools, Type outputModel,
                                Map<String, Object> samplingParams,
                                String systemPromptSupplement) {
        return generateWithType(tools, outputModel, samplingParams, systemPromptSupplement);
    }

    private LLMResponse generateWithType(List<Tool> tools, Type outputModel,
                                         Map<String, Object> samplingParams,
                                         String systemPromptSupplement) {
        List<Message> messages = buildMessages(systemPromptSupplement);
        List<Tool> effectiveTools = tools;
        CallMiddleware.LlmRequest request = new CallMiddleware.LlmRequest(
            messages, effectiveTools, samplingParams);
        for (CallMiddleware hook : config.middleware()) {
            request = hook.beforeLlmRequest(agent, request);
        }
        messages = request.messages();
        effectiveTools = request.tools();
        samplingParams = request.samplingParams();

        if (isPromptLoggingEnabled()) {
            boolean rawEnabled = isPromptRawEnabled();
            List<Message> inspectedMessages = rawEnabled ? messages : redactMessages(messages);
            agent.eventManager().add(new Event.PromptBuilt(
                llm.model(),
                inspectedMessages,
                effectiveTools.stream().map(Tool::name).toList(),
                outputModel != null ? outputModel.getTypeName() : null,
                samplingParams,
                !rawEnabled));
        }

        agent.eventManager().add(new Event.LLMCallStart(llm.model()));

        Span span = Tracing.startLLMSpan(llm.model());
        Scope scope = span.makeCurrent();
        try {
            LLMResponse response = llm.chat(messages, effectiveTools,
                outputModel instanceof Class<?> clazz ? clazz : Object.class,
                samplingParams);
            for (CallMiddleware hook : config.middleware()) {
                response = hook.afterLlm(agent, response);
            }

            int blocksChars = estimateChars(agent.contextManager().render(agent));
            int eventsChars = estimateChars(agent.eventManager().renderSummary());
            stats = stats.accumulate(response.usage() != null ? response.usage()
                : new ai.nooa.llm.LLMResponse.Usage(0, 0, 0), blocksChars, eventsChars);

            agent.eventManager().add(new Event.LLMComplete(
                llm.model(),
                response.usage() != null ? response.usage().promptTokens() : 0,
                response.usage() != null ? response.usage().completionTokens() : 0,
                response.usage() != null ? response.usage().totalTokens() : 0));
            agent.eventManager().add(new Event.LLMCallEnd(true, null));
            span.setStatus(StatusCode.OK);
            return response;
        } catch (Exception e) {
            agent.eventManager().add(new Event.LLMCallEnd(false, e.getClass().getSimpleName()));
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            scope.close();
            span.end();
        }
    }

    @Override
    public ExecutionResult executeCode(String code, Map<String, Object> builtins) {
        if (sandbox == null) { sandbox = config.sandboxExecutorFactory().create(agent); }
        Span span = Tracing.startCodeExecutionSpan();
        Scope scope = span.makeCurrent();
        try {
            String effectiveCode = code;
            CallMiddleware.CodeRequest request = new CallMiddleware.CodeRequest(effectiveCode);
            for (CallMiddleware hook : config.middleware()) {
                request = hook.beforeCodeRequest(agent, request);
            }
            ExecutionResult result = sandbox.execute(request.code());
            for (CallMiddleware hook : config.middleware()) {
                result = hook.afterCode(agent, result);
            }
            if (result.success()) span.setStatus(StatusCode.OK);
            else span.setStatus(StatusCode.ERROR, result.error());
            return result;
        } finally {
            scope.close();
            span.end();
        }
    }

    @Override
    public void bindVariable(String name, String typeName, Object value) {
        if (sandbox == null) { sandbox = config.sandboxExecutorFactory().create(agent); }
        sandbox.bindVariable(name, typeName, value);
    }

    @Override
    public Object executeNested(GenerationStrategy strategy, CurrentCall call) {
        inGenerationSession.set(true);
        try {
            return strategy.execute(this, call);
        } finally {
            inGenerationSession.set(false);
        }
    }

    @Override
    public String expandVariables(String template) {
        return ExpressionEvaluator.evaluate(template, Map.of("self", agent, "type", agent.getClass()));
    }

    public boolean isInGenerationSession() { return Boolean.TRUE.equals(inGenerationSession.get()); }

    public ContextWindowStats stats() { return stats; }

    /**
     * Execute a @Generate method. Submits work to a virtual thread,
     * blocking the caller until completion. Virtual threads handle
     * blocking LLM/IO cheaply.
     */
    public Object callPlan(GenerationStrategy strategy, CurrentCall call) {
        generationLock.lock();
        agent.eventManager().beginScope(call.callId());
        inGenerationSession.set(true);
        boolean trace = config.enableTracing()
            && !call.method().isAnnotationPresent(NoTrace.class);
        Span span = trace
            ? Tracing.startAgentSpan(agent.getClass().getSimpleName(), call.method().getName())
            : Span.getInvalid();
        Scope scope = trace ? span.makeCurrent() : () -> { };
        try {
            checkPreconditions(call);
            agent.eventManager().add(new Event.BeforeAgentCall(
                call.method().getName(), true));
            agent.eventManager().add(new Event.Task(call.userPrompt(true, maxArgChars())));
            Object result = executeWithConditions(strategy, call);
            agent.eventManager().add(new Event.AfterAgentCall(
                call.method().getName(), true, true, null));
            if (trace) span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            agent.eventManager().add(new Event.AfterAgentCall(
                call.method().getName(), true, false, e.getClass().getSimpleName()));
            if (trace) {
                span.setStatus(StatusCode.ERROR, e.getMessage());
                span.recordException(e);
            }
            throw e;
        } finally {
            agent.eventManager().endScope();
            scope.close();
            span.end();
            inGenerationSession.remove();
            generationLock.unlock();
        }
    }

    public Object executeTask(GenerationStrategy strategy, CurrentCall call) {
        agent.eventManager().beginScope(call.callId());
        inGenerationSession.set(true);
        try {
            checkPreconditions(call);
            agent.eventManager().add(new Event.Task(call.userPrompt(true, maxArgChars())));
            return executeWithConditions(strategy, call);
        } finally {
            agent.eventManager().endScope();
            inGenerationSession.remove();
        }
    }

    private Object executeWithConditions(GenerationStrategy strategy, CurrentCall call) {
        MethodConditions conditions = config.conditionsFor(call.method());
        runBeforeMiddleware(call);
        int attempts = 0;
        while (true) {
            try {
                Object result = strategy.execute(this, call);
                if (conditions != null) {
                    conditions.checkPostconditions(agent, result);
                }
                return runAfterMiddleware(call, result);
            } catch (MethodConditions.InvariantError error) {
                attempts++;
                if (attempts >= 3) {
                    throw error;
                }
                agent.eventManager().add(new Event.Feedback(
                    "Previous result failed validation: " + error.getMessage()
                        + "\nPlease generate a corrected result."));
            } catch (RuntimeException error) {
                runErrorMiddleware(call, error);
                throw error;
            }
        }
    }

    private void runBeforeMiddleware(CurrentCall call) {
        for (CallMiddleware hook : config.middleware()) {
            hook.before(agent, call);
        }
    }

    private Object runAfterMiddleware(CurrentCall call, Object result) {
        for (CallMiddleware hook : config.middleware()) {
            result = hook.after(agent, call, result);
        }
        return result;
    }

    private void runErrorMiddleware(CurrentCall call, RuntimeException error) {
        for (CallMiddleware hook : config.middleware()) {
            hook.onError(agent, call, error);
        }
    }

    private void checkPreconditions(CurrentCall call) {
        MethodConditions conditions = config.conditionsFor(call.method());
        if (conditions != null) {
            conditions.checkPreconditions(agent, call.args());
        }
    }

    public String evaluateExpression(String expression) {
        Object result = ExpressionEvaluator.resolve(expression,
            Map.of("self", agent, "type", agent.getClass()));
        return result != null ? result.toString() : "";
    }

    private List<Message> buildMessages(String systemPromptSupplement) {
        List<Message> messages = new ArrayList<>();
        String systemPrompt = agent.contextManager().render(agent);
        if (systemPromptSupplement != null && !systemPromptSupplement.isBlank()) {
            systemPrompt = systemPrompt.isEmpty()
                ? systemPromptSupplement
                : systemPrompt + "\n\n" + systemPromptSupplement;
        }
        if (!systemPrompt.isEmpty()) messages.add(Message.system(systemPrompt));
        messages.addAll(agent.eventManager().toMessages());
        return messages;
    }

    private static int estimateChars(String s) {
        return s != null ? s.length() : 0;
    }

    private static int maxArgChars() {
        String raw = System.getProperty(MAX_ARG_CHARS_PROP);
        if (raw != null) {
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_MAX_ARG_CHARS;
    }

    private static boolean isPromptLoggingEnabled() {
        return isTruthy(System.getProperty(PROMPT_LOGGING_PROP))
            || isTruthy(System.getenv(PROMPT_LOGGING_ENV));
    }

    private static boolean isPromptRawEnabled() {
        return isTruthy(System.getProperty(PROMPT_RAW_PROP))
            || isTruthy(System.getenv(PROMPT_RAW_ENV));
    }

    private static boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }
        return switch (value.trim().toLowerCase()) {
            case "1", "true", "yes", "on" -> true;
            default -> false;
        };
    }

    private static List<Message> redactMessages(List<Message> messages) {
        List<Message> redacted = new ArrayList<>(messages.size());
        for (Message m : messages) {
            redacted.add(new Message(
                m.role(),
                redactText(m.content()),
                m.toolCalls(),
                m.toolCallId(),
                m.name()));
        }
        return redacted;
    }

    private static String redactText(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        String withoutSecrets = KV_SECRET_PATTERN.matcher(input)
            .replaceAll("$1=[REDACTED]");
        return BEARER_PATTERN.matcher(withoutSecrets)
            .replaceAll("Bearer [REDACTED]");
    }

    @Override
    public void close() { if (sandbox != null) sandbox.close(); }
}
