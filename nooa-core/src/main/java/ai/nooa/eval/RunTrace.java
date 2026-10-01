package ai.nooa.eval;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raw, per-trial signals captured from one agent invocation. Aggregated from
 * the event stream by {@link RunRecorder}.
 *
 * @param caseId              owning case
 * @param trial               zero-based trial index
 * @param output              returned value, or {@code null} on failure
 * @param outputPresent       whether the invocation returned normally
 * @param durationMs          wall-clock duration of the invocation
 * @param llmCallCount        number of LLM calls
 * @param promptTokens        total prompt tokens
 * @param completionTokens    total completion tokens
 * @param totalTokens         total tokens
 * @param costUsd             computed cost, or {@code null} when pricing is unknown
 * @param pricingKnown        whether cost could be computed for all LLM calls
 * @param retryCount          retries consumed (populated once {@code Event.Retry} exists)
 * @param toolCallCount       number of tool invocations
 * @param toolErrorCount      number of tool invocations that reported an error
 * @param stepCount           highest observed turn number
 * @param terminatedCleanly   whether the run finished without a recorded error
 * @param errorClasses        exception class names captured during the run
 * @param executedToolNames   tool names in call order
 * @param promptFingerprints  stable fingerprints of built prompts
 * @param signals             extensible payload (e.g. post-run state fields)
 */
public record RunTrace(
    String caseId,
    int trial,
    Object output,
    boolean outputPresent,
    long durationMs,
    int llmCallCount,
    int promptTokens,
    int completionTokens,
    int totalTokens,
    Double costUsd,
    boolean pricingKnown,
    int retryCount,
    int toolCallCount,
    int toolErrorCount,
    int stepCount,
    boolean terminatedCleanly,
    List<String> errorClasses,
    List<String> executedToolNames,
    List<String> promptFingerprints,
    Map<String, Object> signals) {

    public RunTrace {
        errorClasses = errorClasses == null ? List.of() : List.copyOf(errorClasses);
        executedToolNames = executedToolNames == null ? List.of() : List.copyOf(executedToolNames);
        promptFingerprints = promptFingerprints == null ? List.of() : List.copyOf(promptFingerprints);
        signals = signals == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(signals));
    }

    /** Copy this trace with one extra signal attached. */
    public RunTrace withSignal(String key, Object value) {
        Map<String, Object> merged = new LinkedHashMap<>(signals);
        merged.put(key, value);
        return new RunTrace(caseId, trial, output, outputPresent, durationMs,
            llmCallCount, promptTokens, completionTokens, totalTokens, costUsd,
            pricingKnown, retryCount, toolCallCount, toolErrorCount, stepCount,
            terminatedCleanly, errorClasses, executedToolNames, promptFingerprints,
            merged);
    }

    public static Builder builder(String caseId, int trial) {
        return new Builder(caseId, trial);
    }

    /** Mutable accumulator used by {@link RunRecorder}. */
    public static final class Builder {
        private final String caseId;
        private final int trial;
        private Object output;
        private boolean outputPresent;
        private long durationMs;
        private int llmCallCount;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private Double costUsd;
        private boolean pricingKnown;
        private int retryCount;
        private int toolCallCount;
        private int toolErrorCount;
        private int stepCount;
        private boolean terminatedCleanly = true;
        private final List<String> errorClasses = new java.util.ArrayList<>();
        private final List<String> executedToolNames = new java.util.ArrayList<>();
        private final List<String> promptFingerprints = new java.util.ArrayList<>();
        private final Map<String, Object> signals = new LinkedHashMap<>();

        private Builder(String caseId, int trial) {
            this.caseId = caseId;
            this.trial = trial;
        }

        public Builder output(Object value, boolean present) {
            this.output = value;
            this.outputPresent = present;
            return this;
        }

        public Builder durationMs(long ms) {
            this.durationMs = ms;
            return this;
        }

        public Builder llmCalls(int count) {
            this.llmCallCount = count;
            return this;
        }

        public Builder tokens(int prompt, int completion, int total) {
            this.promptTokens = prompt;
            this.completionTokens = completion;
            this.totalTokens = total;
            return this;
        }

        public Builder cost(Double usd, boolean known) {
            this.costUsd = usd;
            this.pricingKnown = known;
            return this;
        }

        public Builder retries(int count) {
            this.retryCount = count;
            return this;
        }

        public Builder toolCalls(int count, int errors) {
            this.toolCallCount = count;
            this.toolErrorCount = errors;
            return this;
        }

        public Builder steps(int count) {
            this.stepCount = count;
            return this;
        }

        public Builder terminatedCleanly(boolean value) {
            this.terminatedCleanly = value;
            return this;
        }

        public Builder errorClass(String errorClass) {
            if (errorClass != null && !errorClass.isBlank()) {
                this.errorClasses.add(errorClass);
            }
            return this;
        }

        public Builder toolName(String name) {
            if (name != null && !name.isBlank()) {
                this.executedToolNames.add(name);
            }
            return this;
        }

        public Builder promptFingerprint(String fingerprint) {
            if (fingerprint != null && !fingerprint.isBlank()) {
                this.promptFingerprints.add(fingerprint);
            }
            return this;
        }

        public Builder signal(String key, Object value) {
            this.signals.put(key, value);
            return this;
        }

        public RunTrace build() {
            return new RunTrace(caseId, trial, output, outputPresent, durationMs,
                llmCallCount, promptTokens, completionTokens, totalTokens, costUsd,
                pricingKnown, retryCount, toolCallCount, toolErrorCount, stepCount,
                terminatedCleanly, errorClasses, executedToolNames, promptFingerprints,
                signals);
        }
    }
}
