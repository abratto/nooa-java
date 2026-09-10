package ai.nooa.cli;

import static org.assertj.core.api.Assertions.assertThat;

import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.UnifiedLLM;
import org.junit.jupiter.api.Test;

class InteractiveAgentTest {
    public static class ChatAgent extends Agent {
        public ChatAgent(UnifiedLLM llm) { super(llm); }
        @Generate public String chat(String message) { throw new UnsupportedOperationException(); }
    }

    @Test
    void processesQueuedUserTurnAndReportsInputWhenEmpty() {
        var interactive = InteractiveAgent.create(
            AgentFactory.create(ChatAgent.class, new FakeLLMClient()));
        assertThat(interactive.turn().status()).isEqualTo(InteractiveAgent.TurnStatus.NEED_INPUT);
        interactive.submit("hello");
        var result = interactive.turn();
        assertThat(result.status()).isEqualTo(InteractiveAgent.TurnStatus.ERROR);
        assertThat(result.error()).contains("CodeActStrategy failed");
        interactive.agent().close();
    }

    @Test
    void processesOneQueuedItemWithoutDiscardingFollowingItems() {
        var interactive = InteractiveAgent.create(
            AgentFactory.create(ChatAgent.class, new FakeLLMClient()));
        interactive.submit("first").submit("second");

        assertThat(interactive.turn().status()).isEqualTo(InteractiveAgent.TurnStatus.ERROR);
        assertThat(interactive.queueManager().size()).isEqualTo(1);
        interactive.agent().close();
    }

    @Test
    void returnsTypedCommandResults() {
        var interactive = InteractiveAgent.create(
            AgentFactory.create(ChatAgent.class, new FakeLLMClient()));
        var result = interactive.commandResult("/model");
        assertThat(result.status()).isEqualTo(InteractiveAgent.TurnStatus.COMMAND);
        assertThat(interactive.commandResult("/missing").status())
            .isEqualTo(InteractiveAgent.TurnStatus.ERROR);
        interactive.agent().close();
    }
}