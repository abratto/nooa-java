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
           Event.Feedback, Event.Summary, Event.LLMComplete {

    UUID id();
    Instant timestamp();

    /** Role: "user", "assistant", or "system". */
    default String role() {
        return switch (this) {
            case Task t -> "user";
            case LLMOutput o -> "assistant";
            case ExecutionOutput eo -> "user";
            case ErrorEvent ee -> "user";
            case ToolCallEvent tc -> "assistant";
            case ToolResultEvent tr -> "user";
            case BeforeTurn bt -> "system";
            case AfterTurn at -> "system";
            case BeforeAgentCall bc -> "system";
            case AfterAgentCall ac -> "system";
            case LLMCallStart ls -> "system";
            case LLMCallEnd le -> "system";
            case Feedback f -> "user";
            case Summary s -> "assistant";
            case LLMComplete lc -> "system";
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
}
