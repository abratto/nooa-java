package ai.nooa.llm;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fake LLM client for isolated tests and example-level verification.
 */
public final class FakeLLMClient extends UnifiedLLM {

    private final Queue<LLMResponse> responses = new ArrayDeque<>();
    private final List<CallRecord> calls = new CopyOnWriteArrayList<>();
    private final AtomicInteger callCount = new AtomicInteger(0);
    private LLMResponse lastResponse;

    public FakeLLMClient(String model) { super("sk-fake", "https://fake.local", model); }
    public FakeLLMClient() { this("fake-model"); }

    public void respondWith(LLMResponse response) { responses.add(response); }
    public void respondWith(String content) {
        responses.add(new LLMResponse(content, List.of(),
            new LLMResponse.Usage(1, 1, 2), "fake-model", "stop"));
    }
    public void respondWith(List<LLMResponse.ToolCall> toolCalls) {
        responses.add(new LLMResponse(null, toolCalls,
            new LLMResponse.Usage(1, 1, 2), "fake-model", "tool_calls"));
    }

    @Override
    public LLMResponse chat(List<Message> messages, List<Tool> tools,
                             Class<?> outputModel, Map<String, Object> samplingParams) {
        calls.add(new CallRecord(messages, tools, outputModel, samplingParams));
        callCount.incrementAndGet();

        LLMResponse response = responses.poll();
        if (response != null) lastResponse = response;
        if (response == null && lastResponse != null) response = lastResponse;
        if (response == null) {
            throw new RuntimeException("FakeLLMClient: no more scripted responses (call #"
                + callCount + ")");
        }
        return response;
    }

    public List<CallRecord> calls() { return List.copyOf(calls); }
    public int callCount() { return callCount.get(); }
    public List<Message> lastMessages() {
        return calls.isEmpty() ? List.of() : calls.get(calls.size() - 1).messages();
    }
    public List<Tool> lastTools() {
        return calls.isEmpty() ? List.of() : calls.get(calls.size() - 1).tools();
    }

    public record CallRecord(List<Message> messages, List<Tool> tools,
                              Class<?> outputModel, Map<String, Object> samplingParams) {}
}
