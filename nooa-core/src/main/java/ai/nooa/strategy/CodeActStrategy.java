package ai.nooa.strategy;

import ai.nooa.GenerationError;
import ai.nooa.config.CodeActConfig;
import ai.nooa.context.Event;
import ai.nooa.llm.LLMResponse;
import ai.nooa.llm.Message;
import ai.nooa.llm.Tool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CodeAct strategy — REPL loop with executeJava + returnResult tools.
 */
public final class CodeActStrategy implements GenerationStrategy {

    private static final Logger log = LoggerFactory.getLogger(CodeActStrategy.class);

    static final Tool EXECUTE_JAVA_TOOL = Tool.builder()
        .name("executeJava").description("Execute Java code. Variables persist across calls.")
        .parameter("code", "string", "Java source code to execute").build();

    static final Tool RETURN_RESULT_TOOL = Tool.builder()
        .name("returnResult").description("Return the final result matching the required schema.")
        .parameter("value", "object", "The final result value").build();

    private static final List<Tool> TOOLS = List.of(EXECUTE_JAVA_TOOL, RETURN_RESULT_TOOL);

    private final CodeActConfig config;

    public CodeActStrategy(CodeActConfig config) { this.config = config; }

    @Override
    public Object execute(RuntimeServices runtime, CurrentCall call) {
        int iteration = 0;
        int textOnlyCount = 0;
        Exception lastError = null;

        while (iteration < config.maxIterations()) {
            iteration++;
            try {
                runtime.eventManager().add(new Event.BeforeTurn(iteration));

                List<Message> messages = new ArrayList<>();
                String systemPrompt = runtime.agent().contextManager().render(runtime.agent());
                String userPrompt = call.userPrompt(true, 800);
                messages.add(Message.system(systemPrompt));
                messages.add(Message.user(userPrompt));
                messages.addAll(runtime.eventManager().toMessages());

                LLMResponse response = runtime.generate(TOOLS, call.returnType(), Map.of());

                runtime.eventManager().add(new Event.LLMOutput(
                    response.content() != null ? response.content() : "",
                    response.toolCalls()));

                if (response.hasToolCalls()) {
                    textOnlyCount = 0;
                    for (var tc : response.toolCalls()) {
                        Object result = processToolCall(tc, runtime);
                        if (result == _RETURN_SENTINEL) {
                            return runtime.executeCode("return _lastReturnValue;", Map.of())
                                .returnValue();
                        }
                        if (result != null) {
                            runtime.eventManager().add(new Event.AfterTurn(iteration, true, true, null));
                            return result;
                        }
                    }
                    runtime.eventManager().add(new Event.AfterTurn(iteration, false, true, null));
                } else {
                    textOnlyCount++;
                    if (textOnlyCount >= config.maxConsecutiveTextOnly()) {
                        throw new GenerationError("Too many text-only responses (" + textOnlyCount + ")");
                    }
                    runtime.eventManager().add(new Event.AfterTurn(iteration, false, true, null));
                }
            } catch (Exception e) {
                lastError = e;
                runtime.eventManager().add(new Event.ErrorEvent(e.getMessage()));
                runtime.eventManager().add(new Event.AfterTurn(
                    iteration, false, false, e.getClass().getSimpleName()));
                if (iteration >= config.maxRetries()) {
                    throw new GenerationError("CodeActStrategy failed after " + iteration + " iterations", lastError);
                }
            }
        }
        throw new GenerationError("Max iterations exceeded (" + config.maxIterations() + ")", lastError);
    }

    private static final Object _RETURN_SENTINEL = new Object();

    private Object processToolCall(LLMResponse.ToolCall tc, RuntimeServices runtime) {
        runtime.eventManager().add(new Event.ToolCallEvent(tc.name(), tc.arguments()));
        return switch (tc.name()) {
            case "executeJava" -> {
                String code = (String) tc.arguments().getOrDefault("code", "");
                ExecutionResult result = runtime.executeCode(code, Map.of());
                runtime.eventManager().add(new Event.ExecutionOutput(
                    result.stdout(), result.stderr(), result.error()));
                yield null;
            }
            case "returnResult" -> {
                Object value = tc.arguments().get("value");
                runtime.eventManager().add(new Event.ToolResultEvent(
                    tc.id(), tc.name(), value != null ? value.toString() : "null"));
                yield value != null ? value : _RETURN_SENTINEL;
            }
            default -> { log.warn("Unknown tool call: {}", tc.name()); yield null; }
        };
    }
}
