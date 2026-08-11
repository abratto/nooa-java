package ai.nooa.strategy;

import ai.nooa.Agent;
import ai.nooa.llm.LLMResponse;
import ai.nooa.llm.Tool;
import ai.nooa.runtime.EventManager;
import java.util.List;
import java.util.Map;

/**
 * Services available to strategies during execution.
 * Implemented by {@code ActorRuntime}.
 */
public interface RuntimeServices {

    Agent agent();
    EventManager eventManager();
    String agentId();

    LLMResponse generate(List<Tool> tools, Class<?> outputModel, Map<String, Object> samplingParams);

    ExecutionResult executeCode(String code, Map<String, Object> builtins);

    Object executeNested(GenerationStrategy strategy, CurrentCall call);

    String expandVariables(String template);
}
