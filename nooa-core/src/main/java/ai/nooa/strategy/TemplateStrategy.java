package ai.nooa.strategy;

import java.util.Map;

/**
 * Renders a generation method prompt without making an LLM call.
 * Supports named method arguments and the runtime's standard expressions.
 */
public final class TemplateStrategy implements GenerationStrategy {

    @Override
    public String name() {
        return "TemplateStrategy";
    }

    @Override
    public Object execute(RuntimeServices runtime, CurrentCall call) {
        String rendered = runtime.expandVariables(call.docstring());
        for (Map.Entry<String, Object> entry : call.namedArgs().entrySet()) {
            String value = String.valueOf(entry.getValue());
            rendered = rendered.replace("{" + entry.getKey() + "}", value);
        }
        return rendered;
    }
}
