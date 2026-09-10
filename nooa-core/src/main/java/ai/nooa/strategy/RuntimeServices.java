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

    Agent agent();
    EventManager eventManager();
    String agentId();

    LLMResponse generate(List<Tool> tools, Class<?> outputModel, Map<String, Object> samplingParams);

    default LLMResponse generate(List<Tool> tools, Type outputModel,
                                 Map<String, Object> samplingParams) {
        return generate(tools, rawClass(outputModel), samplingParams);
    }

    LLMResponse generate(List<Tool> tools, Class<?> outputModel,
                         Map<String, Object> samplingParams,
                         String systemPromptSupplement);

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

    ExecutionResult executeCode(String code, Map<String, Object> builtins);

    default void bindVariable(String name, String typeName, Object value) {
        // Optional hook; ActorRuntime binds method arguments into the sandbox.
    }

    Object executeNested(GenerationStrategy strategy, CurrentCall call);

    String expandVariables(String template);
}
