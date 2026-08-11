package ai.nooa.runtime;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.UnifiedLLM;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ExpressionEvaluator")
class ExpressionEvaluatorTest {

    static class TestAgent extends Agent {
        public TestAgent(UnifiedLLM llm) { super(llm); }
        public String getName() { return "test-agent"; }
        public int getValue() { return 42; }
        @Generate public String generate(String x) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    @DisplayName("simple self.field lookup")
    void selfField() {
        var agent = new TestAgent(new FakeLLMClient());
        var result = ExpressionEvaluator.resolve("self.agentId",
            Map.of("self", agent, "type", agent.getClass()));
        assertThat(result).isNotNull();
        assertThat(result.toString()).isNotEmpty();
    }

    @Test
    @DisplayName("self.method() getter call")
    void selfMethod() {
        var agent = new TestAgent(new FakeLLMClient());
        var result = ExpressionEvaluator.resolve("self.name",
            Map.of("self", agent, "type", agent.getClass()));
        assertThat(result.toString()).isEqualTo("test-agent");
    }

    @Test
    @DisplayName("type.name returns class simple name")
    void typeName() {
        var agent = new TestAgent(new FakeLLMClient());
        var result = ExpressionEvaluator.resolve("type.name",
            Map.of("self", agent, "type", agent.getClass()));
        assertThat(result.toString()).isEqualTo("TestAgent");
    }

    @Test
    @DisplayName("AgentDoc.of(type(self)) static method call")
    void agentDocOf() {
        var agent = new TestAgent(new FakeLLMClient());
        var result = ExpressionEvaluator.resolve(
            "ai.nooa.agentdoc.AgentDoc.of(type(self))",
            Map.of("self", agent, "type", agent.getClass()));
        assertThat(result).isNotNull();
        assertThat(result.toString()).contains("TestAgent");
    }

    @Test
    @DisplayName("AgentDoc.instanceValues(self) static method call")
    void agentDocInstanceValues() {
        var agent = new TestAgent(new FakeLLMClient());
        var result = ExpressionEvaluator.resolve(
            "ai.nooa.agentdoc.AgentDoc.instanceValues(self)",
            Map.of("self", agent, "type", agent.getClass()));
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("type(self) returns Class object")
    void typeOfSelf() {
        var agent = new TestAgent(new FakeLLMClient());
        var result = ExpressionEvaluator.resolve("type",
            Map.of("self", agent, "type", agent.getClass()));
        assertThat(result).isEqualTo(agent.getClass());
    }

    @Test
    @DisplayName("template evaluation with {expr}")
    void templateEvaluation() {
        var agent = new TestAgent(new FakeLLMClient());
        var result = ExpressionEvaluator.evaluate(
            "Agent: {type.name}, ID: {self.agentId}",
            Map.of("self", agent, "type", agent.getClass()));
        assertThat(result).contains("Agent: TestAgent");
        assertThat(result).contains("ID:");
    }

    @Test
    @DisplayName("unknown field returns {expr} placeholder")
    void unknownField() {
        var agent = new TestAgent(new FakeLLMClient());
        var result = ExpressionEvaluator.evaluate(
            "Result: {self.nonexistent}",
            Map.of("self", agent, "type", agent.getClass()));
        assertThat(result).contains("{self.nonexistent}");
    }
}
