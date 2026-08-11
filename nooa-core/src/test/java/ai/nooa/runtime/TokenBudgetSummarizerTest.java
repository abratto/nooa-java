package ai.nooa.runtime;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.context.Event;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.LLMResponse;
import ai.nooa.llm.UnifiedLLM;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TokenBudgetSummarizer")
class TokenBudgetSummarizerTest {

    static class TestAgent extends Agent {
        public TestAgent(UnifiedLLM llm) { super(llm); }
        @Generate public String generate(String x) { throw new UnsupportedOperationException(); }
    }

    @Test
    @DisplayName("does not compact when under threshold")
    void noCompactUnderThreshold() {
        var llm = new FakeLLMClient();
        var agent = new TestAgent(llm);

        agent.eventManager().add(new Event.Task("task"));
        agent.eventManager().add(new Event.LLMOutput("response"));
        agent.eventManager().add(new Event.LLMOutput("another"));

        var summarizer = new TokenBudgetSummarizer(agent, 200_000);
        int before = agent.eventManager().size();
        summarizer.checkAndCompact();
        assertThat(agent.eventManager().size()).isEqualTo(before);
        agent.close();
    }

    @Test
    @DisplayName("compacts when over threshold with high token usage")
    void compactsWhenOverThreshold() throws Exception {
        var llm = new FakeLLMClient();
        var agent = new TestAgent(llm);

        agent.eventManager().add(new Event.Task("task"));
        agent.eventManager().add(new Event.LLMOutput("long response 1"));
        agent.eventManager().add(new Event.LLMOutput("long response 2"));
        agent.eventManager().add(new Event.LLMOutput("long response 3"));
        agent.eventManager().add(new Event.LLMOutput("long response 4"));

        // Force stats to appear near-limit
        var field = ActorRuntime.class.getDeclaredField("stats");
        field.setAccessible(true);
        field.set(agent.runtime(), new ai.nooa.context.ContextWindowStats(
            0, 0, 190_000, 0, 0, 95));

        var summarizer = new TokenBudgetSummarizer(agent, 200_000);
        summarizer.checkAndCompact();

        // Should have inserted a Summary event
        var events = agent.eventManager().all();
        assertThat(events).anyMatch(e -> e instanceof Event.Summary);
        agent.close();
    }

    @Test
    @DisplayName("install subscribes to LLMComplete events")
    void installSubscribes() {
        var llm = new FakeLLMClient();
        var agent = new TestAgent(llm);

        var summarizer = new TokenBudgetSummarizer(agent, 200_000);
        summarizer.install();

        // Emit an LLMComplete event — should trigger check
        agent.eventManager().add(new Event.LLMComplete("test", 100, 50, 150));
        // No compaction expected since under threshold
        agent.close();
    }

    @Test
    @DisplayName("too few events skips compaction")
    void skipsWhenTooFewEvents() throws Exception {
        var llm = new FakeLLMClient();
        var agent = new TestAgent(llm);

        agent.eventManager().add(new Event.Task("task"));
        agent.eventManager().add(new Event.LLMOutput("single"));

        var field = ActorRuntime.class.getDeclaredField("stats");
        field.setAccessible(true);
        field.set(agent.runtime(), new ai.nooa.context.ContextWindowStats(
            0, 0, 190_000, 0, 0, 95));

        var summarizer = new TokenBudgetSummarizer(agent, 200_000);
        int before = agent.eventManager().size();
        summarizer.checkAndCompact();
        assertThat(agent.eventManager().size()).isEqualTo(before);
        agent.close();
    }
}
