package ai.nooa.runtime;

import ai.nooa.Agent;
import ai.nooa.config.AgentConfig;
import ai.nooa.context.ContextWindowStats;
import ai.nooa.context.Event;
import ai.nooa.llm.LLMResponse;
import ai.nooa.llm.Message;
import ai.nooa.llm.Tool;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.runtime.sandbox.JShellSandbox;
import ai.nooa.strategy.CurrentCall;
import ai.nooa.strategy.ExecutionResult;
import ai.nooa.strategy.GenerationStrategy;
import ai.nooa.strategy.RuntimeServices;
import ai.nooa.tracing.Tracing;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private JShellSandbox sandbox;
    private ContextWindowStats stats = ContextWindowStats.empty();

    private final ThreadLocal<Boolean> inGenerationSession = ThreadLocal.withInitial(() -> false);

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
    public LLMResponse generate(List<Tool> tools, Class<?> outputModel, Map<String, Object> samplingParams, String systemPromptSupplement) {
        List<Message> messages = buildMessages(systemPromptSupplement);

        if (isPromptLoggingEnabled()) {
            boolean rawEnabled = isPromptRawEnabled();
            List<Message> inspectedMessages = rawEnabled ? messages : redactMessages(messages);
            agent.eventManager().add(new Event.PromptBuilt(
                llm.model(),
                inspectedMessages,
                tools.stream().map(Tool::name).toList(),
                outputModel != null ? outputModel.getName() : null,
                samplingParams,
                !rawEnabled));
        }

        agent.eventManager().add(new Event.LLMCallStart(llm.model()));

        Span span = Tracing.startLLMSpan(llm.model());
        Scope scope = span.makeCurrent();
        try {
            LLMResponse response = llm.chat(messages, tools, outputModel, samplingParams);

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
        if (sandbox == null) { sandbox = new JShellSandbox(agent); }
        Span span = Tracing.startCodeExecutionSpan();
        Scope scope = span.makeCurrent();
        try {
            ExecutionResult result = sandbox.execute(code);
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
        if (sandbox == null) { sandbox = new JShellSandbox(agent); }
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
        inGenerationSession.set(true);
        Span span = Tracing.startAgentSpan(
            agent.getClass().getSimpleName(), call.method().getName());
        Scope scope = span.makeCurrent();
        try {
            agent.eventManager().add(new Event.BeforeAgentCall(
                call.method().getName(), true));
            agent.eventManager().add(new Event.Task(call.userPrompt(true, maxArgChars())));
            Object result = strategy.execute(this, call);
            agent.eventManager().add(new Event.AfterAgentCall(
                call.method().getName(), true, true, null));
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            agent.eventManager().add(new Event.AfterAgentCall(
                call.method().getName(), true, false, e.getClass().getSimpleName()));
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            scope.close();
            span.end();
            inGenerationSession.remove();
            generationLock.unlock();
        }
    }

    public Object executeTask(GenerationStrategy strategy, CurrentCall call) {
        inGenerationSession.set(true);
        try {
            agent.eventManager().add(new Event.Task(call.userPrompt(true, maxArgChars())));
            return strategy.execute(this, call);
        } finally {
            inGenerationSession.remove();
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
