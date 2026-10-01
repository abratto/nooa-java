package ai.nooa.eval;

import ai.nooa.Agent;
import ai.nooa.context.Event;
import ai.nooa.llm.Message;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Captures a single agent invocation by reading the agent's event stream.
 *
 * <p>Attach before invoking the target method, then call {@link #trace} after
 * it returns. Mirrors the {@code PromptRecorder}/{@code AtifExporter} pattern:
 * the runtime emits events, this subscriber aggregates them into a
 * {@link RunTrace}.</p>
 *
 * <p>Latency is not carried on events; the caller supplies the wall-clock
 * duration (or the recorder derives it from the first and last event
 * timestamps when the caller reports {@code 0}). Tokens come from
 * {@link Event.LLMComplete}, tool activity from {@link Event.ToolCallEvent} /
 * {@link Event.ToolResultEvent}, and errors from the call-lifecycle events.</p>
 */
public final class RunRecorder implements AutoCloseable {

    private final Agent agent;
    private final ModelPricing pricing;
    private final int startIndex;

    private Object output;
    private boolean outputPresent;
    private long durationMs;
    private boolean terminatedCleanly = true;

    private RunRecorder(Agent agent, ModelPricing pricing) {
        this.agent = agent;
        this.pricing = pricing == null ? ModelPricing.of() : pricing;
        this.startIndex = agent.eventManager().size();
    }

    public static RunRecorder attach(Agent agent, ModelPricing pricing) {
        return new RunRecorder(agent, pricing);
    }

    public static RunRecorder attach(Agent agent) {
        return new RunRecorder(agent, ModelPricing.of());
    }

    /** Record the value returned by the target method. */
    public RunRecorder output(Object value, boolean present) {
        this.output = value;
        this.outputPresent = present;
        return this;
    }

    /** Record wall-clock duration; {@code 0} falls back to event timestamps. */
    public RunRecorder durationMs(long ms) {
        this.durationMs = ms;
        return this;
    }

    /** Mark whether the invocation completed without error. */
    public RunRecorder terminatedCleanly(boolean value) {
        this.terminatedCleanly = value;
        return this;
    }

    /** Aggregate the events observed since attach into a {@link RunTrace}. */
    public RunTrace trace(String caseId, int trial) {
        List<Event> events = agent.eventManager().since(startIndex);

        RunTrace.Builder builder = RunTrace.builder(caseId, trial)
            .output(output, outputPresent)
            .terminatedCleanly(terminatedCleanly);

        int llmCalls = 0;
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;
        int toolCalls = 0;
        int toolErrors = 0;
        int steps = 0;
        int retries = 0;
        double cost = 0.0;
        boolean anyLlmComplete = false;
        boolean pricingKnown = true;
        long firstTimestamp = 0;
        long lastTimestamp = 0;

        for (Event event : events) {
            long ts = event.timestamp().toEpochMilli();
            if (firstTimestamp == 0) {
                firstTimestamp = ts;
            }
            lastTimestamp = ts;

            switch (event) {
                case Event.LLMCallStart _ -> llmCalls++;
                case Event.LLMCallEnd e -> {
                    if (!e.success()) {
                        builder.terminatedCleanly(false);
                        builder.errorClass(e.exceptionType());
                    }
                }
                case Event.LLMComplete e -> {
                    anyLlmComplete = true;
                    promptTokens += e.promptTokens();
                    completionTokens += e.completionTokens();
                    totalTokens += e.totalTokens();
                    var price = pricing.cost(e.modelName(), e.promptTokens(), e.completionTokens());
                    if (price.isPresent()) {
                        cost += price.get();
                    } else {
                        pricingKnown = false;
                    }
                }
                case Event.AfterAgentCall e -> {
                    if (!e.success()) {
                        builder.terminatedCleanly(false);
                        builder.errorClass(e.exceptionType());
                    }
                }
                case Event.AfterTurn e -> {
                    steps = Math.max(steps, e.turnNumber());
                    if (!e.success()) {
                        builder.errorClass(e.exceptionType());
                    }
                }
                case Event.ErrorEvent e -> {
                    builder.terminatedCleanly(false);
                    builder.errorClass(errorClassFrom(e.message()));
                }
                case Event.ToolCallEvent e -> {
                    toolCalls++;
                    builder.toolName(e.toolName());
                }
                case Event.ToolResultEvent e -> {
                    if (looksLikeError(e.result())) {
                        toolErrors++;
                    }
                }
                case Event.PromptBuilt e -> builder.promptFingerprint(fingerprint(e));
                case Event.Retry _ -> retries++;
                default -> { }
            }
        }

        long effectiveDuration = durationMs > 0
            ? durationMs
            : (lastTimestamp > firstTimestamp ? lastTimestamp - firstTimestamp : 0);

        Double costUsd = (anyLlmComplete && pricingKnown) ? cost : null;
        return builder
            .durationMs(effectiveDuration)
            .llmCalls(llmCalls)
            .tokens(promptTokens, completionTokens, totalTokens)
            .cost(costUsd, anyLlmComplete && pricingKnown)
            .toolCalls(toolCalls, toolErrors)
            .steps(steps)
            .retries(retries)
            .build();
    }

    private static boolean looksLikeError(String result) {
        if (result == null || result.isBlank()) {
            return true;
        }
        String lower = result.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("error")
            || lower.contains("exception")
            || lower.contains("failed");
    }

    private static String errorClassFrom(String message) {
        if (message == null || message.isBlank()) {
            return "Error";
        }
        int colon = message.indexOf(':');
        String head = colon > 0 ? message.substring(0, colon) : message;
        return head.strip();
    }

    private static String fingerprint(Event.PromptBuilt event) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder material = new StringBuilder();
            material.append(event.modelName()).append('|')
                .append(event.outputModel()).append('|')
                .append(event.toolNames()).append('|');
            for (Message message : event.messages()) {
                material.append(message.role()).append(':').append(message.content()).append(';');
            }
            byte[] hash = digest.digest(material.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "";
        }
    }

    @Override
    public void close() {
        // No listener registration to detach; kept for try-with-resources symmetry.
    }
}
