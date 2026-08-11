package ai.nooa;

import ai.nooa.llm.FakeLLMClient;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.*;

class AgentTest {

    @Test
    @DisplayName("Agent constructor initializes all framework fields")
    void initializesFrameworkFields() {
        var llm = new FakeLLMClient();
        var agent = AgentFactory.create(TestGenerateAgent.class, llm);
        assertThat(agent.agentId()).isNotNull();
        assertThat(agent.llm()).isSameAs(llm);
        assertThat(agent.runtime()).isNotNull();
        assertThat(agent.eventManager()).isNotNull();
        assertThat(agent.contextManager()).isNotNull();
        assertThat(agent.context()).isNotNull();
        assertThat(agent.events()).isNotNull();
        agent.close();
    }

    @Test
    @DisplayName("@Hidden annotations are present on framework fields")
    void frameworkFieldsAreHidden() {
        for (Field f : Agent.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            assertThat(f.isAnnotationPresent(ai.nooa.annotations.Hidden.class))
                .as("Field %s should be @Hidden", f.getName()).isTrue();
        }
    }
}
