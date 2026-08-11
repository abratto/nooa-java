package ai.nooa;

import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Hidden;
import ai.nooa.annotations.NoTrace;
import ai.nooa.annotations.Strategy;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.FakeLLMClient;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Visibility System")
class VisibilityTest {

    @Test
    @DisplayName("Agent framework fields are @Hidden")
    void agentFrameworkFieldsHidden() {
        var fields = java.util.Arrays.stream(Agent.class.getDeclaredFields())
            .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
            .toList();
        assertThat(fields).isNotEmpty();
        for (var f : fields) {
            assertThat(f.isAnnotationPresent(Hidden.class))
                .as("Field %s should be @Hidden", f.getName()).isTrue();
        }
    }

    @Test
    @DisplayName("@SystemPrompt annotation resolved")
    void systemPromptResolved() {
        var llm = new FakeLLMClient();
        var agent = new TestPromptedAgent(llm);
        assertThat(agent.resolveSystemPrompt()).contains("You are a test agent");
        agent.close();
    }

    @Test
    @DisplayName("no @SystemPrompt returns class name")
    void noSystemPromptReturnsClassName() {
        var llm = new FakeLLMClient();
        var agent = new TestDeterministicAgent(llm);
        assertThat(agent.resolveSystemPrompt()).isEqualTo("TestDeterministicAgent");
        agent.close();
    }

    @Test
    @DisplayName("All core annotations loadable")
    void annotationsLoadable() {
        assertThat(Generate.class).isNotNull();
        assertThat(Strategy.class).isNotNull();
        assertThat(Hidden.class).isNotNull();
        assertThat(NoTrace.class).isNotNull();
        assertThat(SystemPrompt.class).isNotNull();
    }
}
