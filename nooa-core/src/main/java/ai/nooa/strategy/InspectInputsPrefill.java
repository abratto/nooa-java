package ai.nooa.strategy;

/** Builds a bounded, named input map in the persistent CodeAct session. */
public final class InspectInputsPrefill implements Prefill {

    @Override
    public String code(CurrentCall call) {
        var code = new StringBuilder("__inputs.clear();");
        for (String name : call.namedArgs().keySet()) {
            code.append(" __inputs.put(\"")
                .append(escape(name))
                .append("\", ")
                .append(name)
                .append(");");
        }
        return code.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}