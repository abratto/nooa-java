package ai.nooa.strategy;

import ai.nooa.GenerationError;
import ai.nooa.RestrictedCodeError;
import ai.nooa.config.CodeActConfig;
import ai.nooa.context.Event;
import ai.nooa.llm.LLMResponse;
import ai.nooa.llm.Tool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CodeAct strategy — REPL loop with executeJava + returnResult tools.
 */
public final class CodeActStrategy implements GenerationStrategy {

    private static final Logger log = LoggerFactory.getLogger(CodeActStrategy.class);

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String EXECUTE_JAVA = "executeJava";
    private static final String RETURN_RESULT = "returnResult";
    private static final String STRING_TYPE = "string";
    private static final String OBJECT_TYPE = "object";
    private static final String VALUE_FIELD = "value";

    static final Tool EXECUTE_JAVA_TOOL = Tool.builder()
        .name(EXECUTE_JAVA)
        .description("""
            Execute Java code in the agent's environment. Variables and helper
            methods persist across calls. Access the live agent via `__agent__`
            (call `__agent__.methodName(...)`), and submit the final answer via
            `returnResult(value)`.""")
        .parameter("code", STRING_TYPE, "Java source code to execute").build();

    static final Tool RETURN_RESULT_TOOL = Tool.builder()
        .name(RETURN_RESULT)
        .description("""
            Return the final result for the task. Call this ONLY when you have
            computed the final answer. The result must match the expected return
            type.""")
        .parameter(VALUE_FIELD, OBJECT_TYPE, "The final result value").build();

    private static final String STRATEGY_PROMPT = """
        ## Strategy

        JShell-like Java session. State persists across cells. You MUST call a
        tool each turn — plain-text responses do NOT end the session. To finish,
        call `returnResult(value)`. Repeated text-only responses will abort the
        run with an error.

        Your two tools:
        - `executeJava(code)` — run a Java code cell
        - `returnResult(value)` — submit your final answer (also callable from
          inside `executeJava`)

        Agent methods are NOT tools. `__agent__.methodName(...)` can only be
        called from inside an `executeJava` code cell — never emit a tool call
        whose name looks like `__agent__...`; it will be rejected.

        When to use which tool:
        - Use `returnResult(...)` directly for simple answers determinable from
          the inputs alone (yes/no, one field, a single lookup).
        - Use `executeJava(...)` for lists/batches, arithmetic, multi-step
          computation, transforms, or iteration.
        - For language tasks (classification, extraction, interpretation), reason
          in the model and answer directly via `returnResult`.

        Returning computed results:
        - After computing in code, call `returnResult(variable)` from within
          `executeJava()`. This passes the variable directly. Do NOT re-type
          computed values in a separate `returnResult` tool call.

        Helpers:
        - Define helper methods at the top of the cell and call them by name.
          Existing methods on the agent are usable via `__agent__.method(...)`.
          Helpers persist as REPL locals across cells in this session.
        """;

    private static final String EXECUTION_CONTEXT = """
        ## Execution Context

        These names are already in scope inside `executeJava()` (state persists
        across cells) — call them, don't re-declare.

        - `__agent__` — the live agent instance; call its methods via
          `__agent__.methodName(...)`
        - `__context__` — the agent context API
        - `__events__` — the agent events API
        - `returnResult(Object value)` — submit the final answer

        Standard imports already available: `java.util.*`, `java.util.stream.*`,
        `java.util.concurrent.*`, `com.fasterxml.jackson.databind.ObjectMapper`

        Typing tips:
        - Use `var` for helper results (e.g. `var r = __agent__.search(q);`).
          Nested record types returned by agent methods cannot be referenced
          by their simple name inside a cell — `var` always works.
        - Do not re-declare variables that already exist in the session; reuse
          them or pick a fresh name.
        - Reflection and system APIs are blocked; compute with plain Java and
          return values via `returnResult`.
        """;

    private static String strategySystemPrompt() {
        return STRATEGY_PROMPT + "\n\n" + EXECUTION_CONTEXT;
    }

    static Tool returnResultTool(Class<?> returnType) {
        if (returnType == null || returnType == Object.class
            || returnType == void.class || returnType == Void.class) {
            return RETURN_RESULT_TOOL;
        }
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", OBJECT_TYPE);
        inputSchema.put("properties", Map.of(VALUE_FIELD, schemaFor(returnType)));
        inputSchema.put("required", List.of(VALUE_FIELD));
        String description = "Return the final result for the task. Call this ONLY when you "
            + "have computed the final answer. Expected return type: "
            + returnType.getSimpleName() + ".";
        return new Tool(RETURN_RESULT, description, inputSchema);
    }

    private static Map<String, Object> schemaFor(Class<?> type) {
        if (type == String.class || type == Character.class || type == char.class) {
            return Map.of("type", STRING_TYPE);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Map.of("type", "boolean");
        }
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class
            || type == short.class || type == Short.class || type == byte.class || type == Byte.class
            || type == java.math.BigInteger.class) {
            return Map.of("type", "integer");
        }
        if (type == double.class || type == Double.class || type == float.class || type == Float.class
            || type == java.math.BigDecimal.class || type == Number.class) {
            return Map.of("type", "number");
        }
        if (type.isEnum()) {
            return Map.of("type", STRING_TYPE,
                "enum", java.util.Arrays.stream(type.getEnumConstants())
                    .map(String::valueOf).toList());
        }
        // Records are the common structured-output contract. Build the schema
        // from the record components so the model knows the required fields,
        // rather than relying on jackson-module-jsonSchema (not a dependency).
        if (type.isRecord()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new java.util.ArrayList<>();
            for (var component : type.getRecordComponents()) {
                properties.put(component.getName(), schemaFor(component.getType()));
                required.add(component.getName());
            }
            Map<String, Object> recordSchema = new LinkedHashMap<>();
            recordSchema.put("type", OBJECT_TYPE);
            recordSchema.put("properties", properties);
            recordSchema.put("required", required);
            return recordSchema;
        }
        if (type.isArray()) {
            return Map.of("type", "array", "items", schemaFor(type.getComponentType()));
        }
        if (java.util.Collection.class.isAssignableFrom(type)) {
            return Map.of("type", "array", "items", Map.of());
        }
        if (java.util.Map.class.isAssignableFrom(type)) {
            return Map.of("type", OBJECT_TYPE);
        }
        try {
            @SuppressWarnings("deprecation")
            var schema = JSON.generateJsonSchema(type);
            return JSON.convertValue(JSON.valueToTree(schema),
                new TypeReference<Map<String, Object>>() {});
        } catch (Exception _) {
            return Map.of("type", OBJECT_TYPE);
        }
    }

    private static Object convertToReturnType(Object value, Class<?> returnType) {
        if (returnType == null || returnType == Object.class
            || returnType == void.class || returnType == Void.class) {
            return value;
        }
        if (returnType.isInstance(value)) {
            return value;
        }
        if (returnType == String.class || returnType == CharSequence.class) {
            return String.valueOf(value);
        }
        // Models sometimes double-encode structured results: the tool
        // argument arrives as a String containing JSON. Parse it before
        // treating the value as a plain Java String.
        if (value instanceof String s && !s.isBlank()
            && (s.startsWith("{") || s.startsWith("["))) {
            try {
                return JSON.readValue(s, JSON.constructType(returnType));
            } catch (Exception _) {
                // Fall through to generic conversion.
            }
        }
        try {
            return JSON.convertValue(value, returnType);
        } catch (Exception e) {
            throw new GenerationError("returnResult value '" + value
                + "' does not match the declared return type " + returnType.getSimpleName()
                + ". Return a value shaped like the expected type"
                + (returnType.isRecord()
                    ? " (JSON object with fields: " + recordFields(returnType) + ")"
                    : "")
                + ".", e);
        }
    }

    private static String recordFields(Class<?> recordType) {
        return String.join(", ", java.util.Arrays.stream(recordType.getRecordComponents())
            .map(c -> c.getName())
            .toList());
    }

    private final CodeActConfig config;

    public CodeActStrategy(CodeActConfig config) { this.config = config; }

    @Override
    @SuppressWarnings("java:S3776")
    public Object execute(RuntimeServices runtime, CurrentCall call) {
        int iteration = 0;
        int textOnlyCount = 0;
        Exception lastError = null;

        bindMethodArguments(runtime, call);

        runPrefill(runtime, call);

        while (iteration < config.maxIterations()) {
            iteration++;
            try {
                runtime.eventManager().add(new Event.BeforeTurn(iteration));

                LLMResponse response = runtime.generate(
                    List.of(EXECUTE_JAVA_TOOL, returnResultTool(call.returnType())),
                    call.returnType(), Map.of(), strategySystemPrompt());

                runtime.eventManager().add(new Event.LLMOutput(
                    response.content() != null ? response.content() : "",
                    response.toolCalls()));

                if (response.hasToolCalls()) {
                    textOnlyCount = 0;
                    for (var tc : response.toolCalls()) {
                        Object result = processToolCall(tc, runtime, call.returnType());
                        if (result == _RETURN_SENTINEL) {
                            runtime.eventManager().add(new Event.AfterTurn(iteration, true, true, null));
                            return null;
                        }
                        if (result != null) {
                            runtime.eventManager().add(new Event.AfterTurn(iteration, true, true, null));
                            return result;
                        }
                    }
                    runtime.eventManager().add(new Event.AfterTurn(iteration, false, true, null));
                } else {
                    String content = response.content() != null ? response.content().trim() : "";
                    if (config.allowTextFallback() && !content.isEmpty()) {
                        runtime.eventManager().add(new Event.AfterTurn(iteration, false, true, null));
                        return content;
                    }
                    textOnlyCount++;
                    if (textOnlyCount >= config.maxConsecutiveTextOnly()) {
                        throw new GenerationError("Too many text-only responses (" + textOnlyCount + ")");
                    }
                    runtime.eventManager().add(new Event.ErrorEvent(
                        "Your last reply was plain text with no tool call, so it was dropped — "
                        + "a bare message cannot end the turn or run code. To finish, call "
                        + "`returnResult(value)`. To do more work, call `executeJava(code)`. "
                        + "Re-issue your response now as one of those tool calls."));
                    runtime.eventManager().add(new Event.AfterTurn(iteration, false, true, null));
                }
            } catch (Exception e) {
                lastError = e;
                runtime.eventManager().add(new Event.ErrorEvent(e.getMessage()));
                runtime.eventManager().add(new Event.AfterTurn(
                    iteration, false, false, e.getClass().getSimpleName()));
                if (iteration >= config.maxRetries()) {
                    throw new GenerationError("CodeActStrategy failed after " + iteration
                        + " attempts: " + describeError(lastError), lastError);
                }
            }
        }
        throw new GenerationError("Max iterations exceeded (" + config.maxIterations() + "): "
            + describeError(lastError), lastError);
    }

    private static String describeError(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        return (message == null || message.isBlank())
            ? error.getClass().getSimpleName()
            : message;
    }

    private static final Object _RETURN_SENTINEL = new Object();

    private static void bindMethodArguments(RuntimeServices runtime, CurrentCall call) {
        var params = call.method().getParameters();
        Object[] args = call.args();
        for (int i = 0; i < params.length && i < args.length; i++) {
            String name = params[i].getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            runtime.bindVariable(name, params[i].getParameterizedType().getTypeName(), args[i]);
        }
        runtime.bindVariable("__inputs", "java.util.Map",
            new LinkedHashMap<>(call.namedArgs()));
    }

    private void runPrefill(RuntimeServices runtime, CurrentCall call) {
        if (config.prefill() == null) {
            return;
        }
        String code = config.prefill().code(call);
        if (code == null || code.isBlank()) {
            return;
        }
        runtime.eventManager().add(new Event.Feedback("Running CodeAct prefill."));
        ExecutionResult result = runtime.executeCode(code, Map.of());
        runtime.eventManager().add(new Event.ExecutionOutput(
            result.stdout(), result.stderr(), result.error()));
        if (!result.success()) {
            if (result.error() != null && result.error().startsWith("Blocked")) {
                throw new RestrictedCodeError("CodeAct prefill failed: " + result.error());
            }
            throw new GenerationError("CodeAct prefill failed: " + result.error());
        }
    }

    private Object processToolCall(LLMResponse.ToolCall tc, RuntimeServices runtime, Class<?> returnType) {
        String normalizedTool = normalizeToolName(tc.name());
        runtime.eventManager().add(new Event.ToolCallEvent(normalizedTool, tc.arguments()));
        return switch (normalizedTool) {
            case EXECUTE_JAVA -> {
                String code = (String) tc.arguments().getOrDefault("code", "");
                ExecutionResult result = runtime.executeCode(code, Map.of());
                runtime.eventManager().add(new Event.ExecutionOutput(
                    result.stdout(), result.stderr(), result.error()));
                if (!result.success() && result.error() != null
                    && result.error().startsWith("Blocked")) {
                    throw new RestrictedCodeError(result.error());
                }
                if (result.explicitReturn() && result.success()) {
                    yield convertToReturnType(result.returnValue(), returnType);
                }
                yield null;
            }
            case RETURN_RESULT -> {
                Object value = tc.arguments().get(VALUE_FIELD);
                runtime.eventManager().add(new Event.ToolResultEvent(
                    tc.id(), normalizedTool, value != null ? value.toString() : "null"));
                if (value == null) {
                    if (returnType == null || returnType == void.class || returnType == Void.class) {
                        yield _RETURN_SENTINEL;
                    }
                    runtime.eventManager().add(new Event.ErrorEvent(
                        "returnResult was called without a value. Call returnResult(value) "
                            + "with the final " + returnType.getSimpleName() + " result."));
                    yield null;
                }
                yield convertToReturnType(value, returnType);
            }
            default -> {
                String allowed = "executeJava, returnResult";
                String message = "Unknown tool call '" + tc.name()
                    + "'. Allowed tools: " + allowed
                    + ". Use executeJava for code execution and returnResult for the final output. "
                    + (tc.name().contains("__agent__") || tc.name().contains(".")
                        ? "Agent methods are not tools: put `__agent__.method(...)` inside an executeJava code cell. "
                        : "")
                    + "Re-issue your action as one of those tools.";
                log.warn(message);
                runtime.eventManager().add(new Event.ToolResultEvent(tc.id(), tc.name(), message));
                runtime.eventManager().add(new Event.ErrorEvent(message));
                yield null;
            }
        };
    }

    private String normalizeToolName(String name) {
        if (name == null) {
            return "";
        }

        String normalized = name.trim().toLowerCase(Locale.ROOT)
            .replace("_", "")
            .replace("-", "");

        return switch (normalized) {
            case "executejava", "executepython", "java", "runjava", "execjava" -> EXECUTE_JAVA;
            case "returnresult", "return", "finalresult", "respond", "response" -> RETURN_RESULT;
            default -> name;
        };
    }
}
