package ai.nooa.runtime;

import ai.nooa.Agent;
import ai.nooa.AbortError;
import ai.nooa.strategy.CurrentCall;
import ai.nooa.llm.LLMResponse;
import ai.nooa.llm.Message;
import ai.nooa.llm.Tool;
import ai.nooa.strategy.ExecutionResult;
import java.util.List;
import java.util.Map;

/** Hooks surrounding a generated method invocation. */
public interface CallMiddleware {

    record LlmRequest(List<Message> messages, List<Tool> tools,
                      Map<String, Object> samplingParams) {
        public LlmRequest {
            messages = List.copyOf(messages);
            tools = List.copyOf(tools);
            samplingParams = Map.copyOf(samplingParams);
        }

        public LlmRequest withMessages(List<Message> value) {
            return new LlmRequest(value, tools, samplingParams);
        }

        public LlmRequest withSamplingParams(Map<String, Object> value) {
            return new LlmRequest(messages, tools, value);
        }

        public static LlmRequest reject(String message) {
            throw new AbortError(message);
        }
    }

    record CodeRequest(String code) {
        public CodeRequest withCode(String value) { return new CodeRequest(value); }
        public static CodeRequest reject(String message) { throw new AbortError(message); }
    }

    default void before(Agent agent, CurrentCall call) {}

    default Object after(Agent agent, CurrentCall call, Object result) {
        return result;
    }

    default void onError(Agent agent, CurrentCall call, Throwable error) {}

    default void beforeLlm(Agent agent, List<Message> messages, List<Tool> tools,
                           Map<String, Object> samplingParams) {}

    default LlmRequest beforeLlmRequest(Agent agent, LlmRequest request) {
        beforeLlm(agent, request.messages(), request.tools(), request.samplingParams());
        return request;
    }

    default LLMResponse afterLlm(Agent agent, LLMResponse response) {
        return response;
    }

    default String beforeCode(Agent agent, String code) {
        return code;
    }

    default CodeRequest beforeCodeRequest(Agent agent, CodeRequest request) {
        return new CodeRequest(beforeCode(agent, request.code()));
    }

    default ExecutionResult afterCode(Agent agent, ExecutionResult result) {
        return result;
    }
}