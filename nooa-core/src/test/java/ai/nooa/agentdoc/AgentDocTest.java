package ai.nooa.agentdoc;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

class AgentDocTest {

    static class DocAgent extends Agent {
        public DocAgent(UnifiedLLM llm) { super(llm); }
        @Generate public String generate(String x) { throw new UnsupportedOperationException(); }
    }

    @Test
    @DisplayName("visibleMethods includes @Generate methods")
    void visibleMethods() {
        var methods = AgentDoc.visibleMethods(DocAgent.class);
        assertThat(methods).anyMatch(m -> m.getName().equals("generate"));
    }

    @Test
    @DisplayName("Object methods excluded from visibleMethods")
    void objectMethodsExcluded() {
        var methods = AgentDoc.visibleMethods(DocAgent.class);
        var names = methods.stream().map(java.lang.reflect.Method::getName).toList();
        assertThat(names).doesNotContain("equals", "hashCode", "toString", "notify", "wait", "getClass");
    }

    @Test
    @DisplayName("of() produces structured documentation")
    void ofProducesDoc() {
        var doc = AgentDoc.of(DocAgent.class);
        assertThat(doc).contains("DocAgent").contains("generate");
    }
}
