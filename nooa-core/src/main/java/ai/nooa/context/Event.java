package ai.nooa.context;

import java.time.Instant;
import java.util.UUID;

/**
 * Sealed hierarchy for conversation and lifecycle events.
 */
public sealed interface Event
    permits Event.Task, Event.LLMOutput, Event.ExecutionOutput,
           Event.ErrorEvent, Event.ToolCallEvent, Event.ToolResultEvent,
           Event.BeforeTurn, Event.AfterTurn,
           Event.BeforeAgentCall, Event.AfterAgentCall,
           Event.LLMCallStart, Event.LLMCallEnd,
           Event.Feedback, Event.Summary, Event.LLMComplete,
           Event.PromptBuilt, Event.Retry {

    UUID id();
    Instant timestamp();

    /** Role: "user", "assistant", or "system". */
    default String role() {
        return switch (this) {
            case Task _ -> "user";
            case LLMOutput _ -> "assistant";
            case ExecutionOutput _ -> "user";
            case ErrorEvent _ -> "user";
            case ToolCallEvent _ -> "assistant";
            case ToolResultEvent _ -> "user";
            case BeforeTurn _ -> "system";
            case AfterTurn _ -> "system";
            case BeforeAgentCall _ -> "system";
            case AfterAgentCall _ -> "system";
            case LLMCallStart _ -> "system";
            case LLMCallEnd _ -> "system";
            case Feedback _ -> "user";
            case Summary _ -> "assistant";
            case LLMComplete _ -> "system";
            case PromptBuilt _ -> "system";
            case Retry _ -> "system";
        };
    }

    // --- Conversation events ---

    record Task(UUID id, Instant timestamp, String content) implements Event {
        public Task(String content) { this(UUID.randomUUID(), Instant.now(), content); }
    }

    record LLMOutput(UUID id, Instant timestamp, String content,
                     java.util.List<ai.nooa.llm.LLMResponse.ToolCall> toolCalls)
                     implements Event {
        public LLMOutput(String content) { this(UUID.randomUUID(), Instant.now(), content, java.util.List.of()); }
        public LLMOutput(String content, java.util.List<ai.nooa.llm.LLMResponse.ToolCall> toolCalls) {
            this(UUID.randomUUID(), Instant.now(), content, toolCalls);
        }
    }

    record ExecutionOutput(UUID id, Instant timestamp, String stdout,
                           String stderr, String error) implements Event {
        public ExecutionOutput(String stdout, String stderr, String error) {
            this(UUID.randomUUID(), Instant.now(), stdout, stderr, error);
        }
    }

    record ErrorEvent(UUID id, Instant timestamp, String message) implements Event {
        public ErrorEvent(String message) { this(UUID.randomUUID(), Instant.now(), message); }
    }

    record ToolCallEvent(UUID id, Instant timestamp, String toolName,
                         java.util.Map<String, Object> arguments) implements Event {
        public ToolCallEvent(String toolName, java.util.Map<String, Object> args) {
            this(UUID.randomUUID(), Instant.now(), toolName, args);
        }
    }

    record ToolResultEvent(UUID id, Instant timestamp, String toolCallId,
                           String toolName, String result) implements Event {
        public ToolResultEvent(String toolCallId, String toolName, String result) {
            this(UUID.randomUUID(), Instant.now(), toolCallId, toolName, result);
        }
    }

    // --- Turn lifecycle ---

    record BeforeTurn(UUID id, Instant timestamp, int turnNumber) implements Event {
        public BeforeTurn(int turnNumber) { this(UUID.randomUUID(), Instant.now(), turnNumber); }
    }

    record AfterTurn(UUID id, Instant timestamp, int turnNumber,
                     boolean isFinal, boolean success, String exceptionType) implements Event {
        public AfterTurn(int turnNumber, boolean isFinal, boolean success, String exceptionType) {
            this(UUID.randomUUID(), Instant.now(), turnNumber, isFinal, success, exceptionType);
        }
    }

    // --- Agent call lifecycle ---

    record BeforeAgentCall(UUID id, Instant timestamp, String methodName,
                           boolean needsGeneration) implements Event {
        public BeforeAgentCall(String methodName, boolean needsGeneration) {
            this(UUID.randomUUID(), Instant.now(), methodName, needsGeneration);
        }
    }

    record AfterAgentCall(UUID id, Instant timestamp, String methodName,
                          boolean needsGeneration, boolean success, String exceptionType)
                          implements Event {
        public AfterAgentCall(String methodName, boolean needsGeneration,
                               boolean success, String exceptionType) {
            this(UUID.randomUUID(), Instant.now(), methodName, needsGeneration, success, exceptionType);
        }
    }

    // --- LLM call lifecycle ---

    record LLMCallStart(UUID id, Instant timestamp, String model) implements Event {
        public LLMCallStart(String model) { this(UUID.randomUUID(), Instant.now(), model); }
    }

    record LLMCallEnd(UUID id, Instant timestamp, boolean success, String exceptionType)
                      implements Event {
        public LLMCallEnd(boolean success, String exceptionType) {
            this(UUID.randomUUID(), Instant.now(), success, exceptionType);
        }
    }

    // --- Other events ---

    record Feedback(UUID id, Instant timestamp, String content) implements Event {
        public Feedback(String content) { this(UUID.randomUUID(), Instant.now(), content); }
    }

    record Summary(UUID id, Instant timestamp, String summaryText,
                   java.util.List<String> replacedTags) implements Event {
        public Summary(String summaryText, java.util.List<String> replacedTags) {
            this(UUID.randomUUID(), Instant.now(), summaryText, replacedTags);
        }
    }

    record LLMComplete(UUID id, Instant timestamp, String modelName,
                       int promptTokens, int completionTokens, int totalTokens)
                       implements Event {
        public LLMComplete(String modelName, int promptTokens, int completionTokens, int totalTokens) {
            this(UUID.randomUUID(), Instant.now(), modelName, promptTokens, completionTokens, totalTokens);
        }
    }

    record PromptBuilt(UUID id, Instant timestamp, String modelName,
                       java.util.List<ai.nooa.llm.Message> messages,
                       java.util.List<String> toolNames,
                       String outputModel,
                       java.util.Map<String, Object> samplingParams,
                       boolean redacted) implements Event {
        public PromptBuilt(String modelName,
                           java.util.List<ai.nooa.llm.Message> messages,
                           java.util.List<String> toolNames,
                           String outputModel,
                           java.util.Map<String, Object> samplingParams,
                           boolean redacted) {
            this(UUID.randomUUID(), Instant.now(), modelName,
                messages != null ? java.util.List.copyOf(messages) : java.util.List.of(),
                toolNames != null ? java.util.List.copyOf(toolNames) : java.util.List.of(),
                outputModel,
                samplingParams != null ? java.util.Map.copyOf(samplingParams) : java.util.Map.of(),
                redacted);
        }
    }

    /** Emitted when a strategy or validation condition retries a generation attempt. */
    record Retry(UUID id, Instant timestamp, String methodName, int attempt,
                 String reason, String exceptionType) implements Event {
        public Retry(String methodName, int attempt, String reason, String exceptionType) {
            this(UUID.randomUUID(), Instant.now(), methodName, attempt, reason, exceptionType);
        }
    }
}
