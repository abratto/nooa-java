package ai.nooa.strategy;

import ai.nooa.Agent;
import ai.nooa.llm.LLMResponse;
import ai.nooa.llm.Tool;
import ai.nooa.runtime.EventManager;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Type;

/**
 * Services available to strategies during execution.
 * Implemented by {@code ActorRuntime}.
 */
public interface RuntimeServices {

    /** Return the agent currently executing the strategy. */
    Agent agent();
    /** Return the agent's thread-safe event manager. */
    EventManager eventManager();
    /** Return the stable identifier assigned to the agent. */
    String agentId();

    /** Generate an LLM response using the current context, tools, and sampling parameters. */
    LLMResponse generate(List<Tool> tools, Class<?> outputModel, Map<String, Object> samplingParams);

    /** Generate a response for a reflective output type, preserving generic type information when supported. */
    default LLMResponse generate(List<Tool> tools, Type outputModel,
                                 Map<String, Object> samplingParams) {
        return generate(tools, rawClass(outputModel), samplingParams);
    }

    /** Generate a response with an additional system-prompt supplement. */
    LLMResponse generate(List<Tool> tools, Class<?> outputModel,
                         Map<String, Object> samplingParams,
                         String systemPromptSupplement);

    /** Generate a response with an additional system-prompt supplement and reflective output type. */
    default LLMResponse generate(List<Tool> tools, Type outputModel,
                                 Map<String, Object> samplingParams,
                                 String systemPromptSupplement) {
        return generate(tools, rawClass(outputModel), samplingParams, systemPromptSupplement);
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> clazz) return clazz;
        if (type instanceof java.lang.reflect.ParameterizedType parameterized
            && parameterized.getRawType() instanceof Class<?> clazz) return clazz;
        return Object.class;
    }

    /** Execute code in the configured sandbox and return its structured result. */
    ExecutionResult executeCode(String code, Map<String, Object> builtins);

    /** Optional hook for binding a generated method variable into the execution environment. */
    default void bindVariable(String name, String typeName, Object value) {
        // Optional hook; ActorRuntime binds method arguments into the sandbox.
    }

    /** Execute a nested strategy call within the current generation session. */
    Object executeNested(GenerationStrategy strategy, CurrentCall call);

    /** Expand supported expressions against the current agent instance. */
    String expandVariables(String template);
}
