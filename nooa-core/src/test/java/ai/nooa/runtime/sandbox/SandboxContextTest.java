package ai.nooa.runtime.sandbox;

import ai.nooa.Agent;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.UnifiedLLM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxContextTest {

    static class TestAgent extends Agent {
        TestAgent(UnifiedLLM llm) { super(llm); }
    }

    @AfterEach
    void clearContext() {
        SandboxContext.clear();
    }

    @Test
    void sharesAgentVariablesAndReturnValuesAcrossThreads() throws Exception {
        var agent = new TestAgent(new FakeLLMClient());
        try {
            SandboxContext.setAgent(agent);
            SandboxContext.setVariable("answer", 42);

            var worker = new Thread(() -> {
                assertThat(SandboxContext.getAgent()).isSameAs(agent);
                assertThat(SandboxContext.getVariable("answer")).isEqualTo(42);
                SandboxContext.setReturnValue("done");
            });
            worker.start();
            worker.join();

            assertThat(SandboxContext.consumeReturnValue()).isEqualTo("done");
        } finally {
            agent.close();
        }
    }

    @Test
    void clearRemovesAgentReturnValueAndVariables() {
        SandboxContext.setAgent(new TestAgent(new FakeLLMClient()));
        SandboxContext.setVariable("key", "value");
        SandboxContext.setReturnValue("result");

        SandboxContext.clear();

        assertThat(SandboxContext.getAgent()).isNull();
        assertThat(SandboxContext.getVariable("key")).isNull();
        assertThat(SandboxContext.consumeReturnValue()).isNull();
    }
}