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
        List<Message> messages = buildMessages();
        agent.eventManager().add(new Event.LLMCallStart(llm.model()));

        Span span = Tracing.startLLMSpan(llm.model());
        try (Scope ignored = span.makeCurrent()) {
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
            span.end();
        }
    }

    @Override
    public ExecutionResult executeCode(String code, Map<String, Object> builtins) {
        if (sandbox == null) { sandbox = new JShellSandbox(agent); }
        Span span = Tracing.startCodeExecutionSpan();
        try (Scope ignored = span.makeCurrent()) {
            ExecutionResult result = sandbox.execute(code);
            if (result.success()) span.setStatus(StatusCode.OK);
            else span.setStatus(StatusCode.ERROR, result.error());
            return result;
        } finally {
            span.end();
        }
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
        try (Scope ignored = span.makeCurrent()) {
            agent.eventManager().add(new Event.BeforeAgentCall(
                call.method().getName(), true));
            agent.eventManager().add(new Event.Task(call.docstring()));
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
            span.end();
            inGenerationSession.set(false);
            generationLock.unlock();
        }
    }

    public Object executeTask(GenerationStrategy strategy, CurrentCall call) {
        inGenerationSession.set(true);
        try {
            agent.eventManager().add(new Event.Task(call.docstring()));
            return strategy.execute(this, call);
        } finally {
            inGenerationSession.set(false);
        }
    }

    public String evaluateExpression(String expression) {
        Object result = ExpressionEvaluator.resolve(expression,
            Map.of("self", agent, "type", agent.getClass()));
        return result != null ? result.toString() : "";
    }

    private List<Message> buildMessages() {
        List<Message> messages = new ArrayList<>();
        String systemPrompt = agent.contextManager().render(agent);
        if (!systemPrompt.isEmpty()) messages.add(Message.system(systemPrompt));
        messages.addAll(agent.eventManager().toMessages());
        return messages;
    }

    private static int estimateChars(String s) {
        return s != null ? s.length() : 0;
    }

    @Override
    public void close() { if (sandbox != null) sandbox.close(); }
}
