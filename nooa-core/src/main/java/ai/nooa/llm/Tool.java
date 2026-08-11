package ai.nooa.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Tool(
    String name,
    String description,
    @JsonProperty("input_schema") Map<String, Object> inputSchema
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private String description;
        private BuilderSchema schema = new BuilderSchema();

        public Builder name(String v) { this.name = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder parameter(String name, String type, String description) {
            schema.addProperty(name, type, description);
            return this;
        }
        public Builder parameter(String name, String type, String description, boolean required) {
            schema.addProperty(name, type, description, required);
            return this;
        }

        public Tool build() {
            return new Tool(name, description, schema.build());
        }
    }

    private static class BuilderSchema {
        private final java.util.LinkedHashMap<String, Object> properties = new java.util.LinkedHashMap<>();
        private final java.util.ArrayList<String> required = new java.util.ArrayList<>();

        void addProperty(String name, String type, String description) {
            addProperty(name, type, description, true);
        }

        void addProperty(String name, String type, String description, boolean isRequired) {
            properties.put(name, Map.of("type", type, "description", description));
            if (isRequired) { required.add(name); }
        }

        Map<String, Object> build() {
            return Map.of(
                "type", "object",
                "properties", properties,
                "required", required
            );
        }
    }
}
