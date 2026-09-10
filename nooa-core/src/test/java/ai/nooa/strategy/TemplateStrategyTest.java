package ai.nooa.strategy;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.UnifiedLLM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateStrategyTest {

    static class TestAgent extends Agent {
        TestAgent(UnifiedLLM llm) {
            super(llm);
        }

        @Generate(prompt = "Hello {value} from {type.name}")
        public String generate(String value) {
            throw new UnsupportedOperationException();
        }
    }

    private final TestAgent agent = new TestAgent(new FakeLLMClient());

    @AfterEach
    void tearDown() {
        agent.close();
    }

    @Test
    void rendersArgumentsAndRuntimeExpressions() throws Exception {
        var method = TestAgent.class.getDeclaredMethod("generate", String.class);
        MethodDocStore.put(method, "Hello {value} from {type.name}");
        var call = CurrentCall.fromMethod(method, new Object[]{"Ada"});

        var result = new TemplateStrategy().execute(agent.runtime(), call);

        assertThat(result).isEqualTo("Hello Ada from TestAgent");
    }
}