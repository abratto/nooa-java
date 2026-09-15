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

        /** Return an immutable request with a replacement message list. */
        public LlmRequest withMessages(List<Message> value) {
            return new LlmRequest(value, tools, samplingParams);
        }

        /** Return an immutable request with replacement sampling parameters. */
        public LlmRequest withSamplingParams(Map<String, Object> value) {
            return new LlmRequest(messages, tools, value);
        }

        /** Abort the current generation with the supplied message. */
        public static LlmRequest reject(String message) {
            throw new AbortError(message);
        }
    }

    record CodeRequest(String code) {
        /** Return a request containing replacement code. */
        public CodeRequest withCode(String value) { return new CodeRequest(value); }
        /** Abort the current code-generation flow with the supplied message. */
        public static CodeRequest reject(String message) { throw new AbortError(message); }
    }

    /** Hook invoked before a generated method call. */
    default void before(Agent agent, CurrentCall call) {}

    /** Hook invoked after a generated method call; the returned value replaces the result. */
    default Object after(Agent agent, CurrentCall call, Object result) {
        return result;
    }

    /** Hook invoked when a generated method call fails. */
    default void onError(Agent agent, CurrentCall call, Throwable error) {}

    /** Legacy hook invoked before an LLM request is sent. */
    default void beforeLlm(Agent agent, List<Message> messages, List<Tool> tools,
                           Map<String, Object> samplingParams) {}

    /** Hook invoked before an LLM request; return a replacement request to modify it. */
    default LlmRequest beforeLlmRequest(Agent agent, LlmRequest request) {
        beforeLlm(agent, request.messages(), request.tools(), request.samplingParams());
        return request;
    }

    /** Hook invoked after an LLM response is received; return the response to expose downstream. */
    default LLMResponse afterLlm(Agent agent, LLMResponse response) {
        return response;
    }

    /** Legacy hook invoked before generated code executes. */
    default String beforeCode(Agent agent, String code) {
        return code;
    }

    /** Hook invoked before code execution; return a replacement request to modify the code. */
    default CodeRequest beforeCodeRequest(Agent agent, CodeRequest request) {
        return new CodeRequest(beforeCode(agent, request.code()));
    }

    /** Hook invoked after sandbox execution; return the result to expose downstream. */
    default ExecutionResult afterCode(Agent agent, ExecutionResult result) {
        return result;
    }
}