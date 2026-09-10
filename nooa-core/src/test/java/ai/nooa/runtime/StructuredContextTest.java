package ai.nooa.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import ai.nooa.Agent;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.UnifiedLLM;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StructuredContextTest {
    static class TestAgent extends Agent {
        TestAgent(UnifiedLLM llm) { super(llm); }
    }

    @Test
    void rendersStructuredValuesWithACharacterBound() {
        var agent = new TestAgent(new FakeLLMClient());
        agent.context().put("payload", Map.of("kind", "input", "value", "abcdefghij"));
        assertThat(agent.contextManager().render(agent, 500)).contains("<payload>");
        assertThat(agent.contextManager().render(agent, 120)).contains("truncated");
        agent.close();
    }
}