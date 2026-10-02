package ai.nooa.examples.incident;

import ai.nooa.AgentFactory;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.LLMResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IncidentAgent (scripted end-to-end)")
class IncidentAgentTest {

    private static FakeLLMClient happyPathLlm() {
        var llm = new FakeLLMClient();
        llm.respondWith("{\"level\":\"HIGH\",\"rationale\":\"p99 breach and DB pool exhaustion\"}");
        llm.respondWith(List.of(new LLMResponse.ToolCall("c1", "executeJava", Map.of("code",
            "returnResult(java.util.Map.of(\"summary\",\"db pool exhausted\","
                + "\"evidence\", java.util.List.of(\"HikariPool timeouts\",\"p99 2.4s\")));"))));
        llm.respondWith("{\"steps\":[\"rollback checkout-api\"],\"risk\":\"low\","
            + "\"rollback\":\"redeploy previous release\"}");
        llm.respondWith("{\"summary\":\"db pool exhaustion after settlement change\","
            + "\"followUps\":[\"add pool saturation alert\"]}");
        return llm;
    }

    @Test
    void approvedPlanResolvesTheIncident() {
        var scenario = IncidentScenarios.checkoutLatency();
        try (var agent = AgentFactory.create(IncidentAgent.class, happyPathLlm(), scenario)) {
            IncidentOutcome outcome = agent.respond(plan -> true);

            assertThat(outcome.finalStatus()).isEqualTo("Resolved");
            assertThat(outcome.finalState()).isInstanceOf(IncidentState.Resolved.class);
            assertThat(outcome.audit()).contains("Resolved <- Resolve");
            assertThat(((IncidentState.Resolved) outcome.finalState()).postmortem().summary())
                .contains("db pool");
        }
    }

    @Test
    void rejectedPlanEscalates() {
        var llm = new FakeLLMClient();
        llm.respondWith("{\"level\":\"HIGH\",\"rationale\":\"breach\"}");
        llm.respondWith(List.of(new LLMResponse.ToolCall("c1", "executeJava", Map.of("code",
            "returnResult(java.util.Map.of(\"summary\",\"db pool exhausted\","
                + "\"evidence\", java.util.List.of(\"timeouts\")));"))));
        llm.respondWith("{\"steps\":[\"rollback\"],\"risk\":\"low\",\"rollback\":\"redeploy\"}");

        var scenario = IncidentScenarios.checkoutLatency();
        try (var agent = AgentFactory.create(IncidentAgent.class, llm, scenario)) {
            IncidentOutcome outcome = agent.respond(plan -> false);

            assertThat(outcome.finalStatus()).isEqualTo("Escalated");
            assertThat(((IncidentState.Escalated) outcome.finalState()).reason())
                .contains("declined");
        }
    }

    @Test
    void criticalSeverityEscalatesWithoutInvestigation() {
        var llm = new FakeLLMClient();
        llm.respondWith("{\"level\":\"CRITICAL\",\"rationale\":\"payment outage\"}");

        var scenario = IncidentScenarios.checkoutLatency();
        try (var agent = AgentFactory.create(IncidentAgent.class, llm, scenario)) {
            IncidentOutcome outcome = agent.respond(plan -> true);

            assertThat(outcome.finalStatus()).isEqualTo("Escalated");
            assertThat(llm.callCount()).isEqualTo(1);
        }
    }

    @Test
    void telemetryHelpersExposeGroundedFacts() {
        var scenario = IncidentScenarios.checkoutLatency();
        try (var agent = AgentFactory.create(IncidentAgent.class, new FakeLLMClient(), scenario)) {
            assertThat(agent.breachedMetrics()).isNotEmpty();
            assertThat(agent.errorLogs(10)).isNotEmpty();
            assertThat(agent.recentDeploys()).contains("checkout-api@2025.12.31-a1b2c3");
        }
    }

    @Test
    void illegalTransitionRaisesTypedError() {
        var scenario = IncidentScenarios.checkoutLatency();
        try (var agent = AgentFactory.create(IncidentAgent.class, new FakeLLMClient(), scenario)) {
            assertThatThrownBy(() -> agent.fire(new IncidentEvent.Approve("alice")))
                .isInstanceOf(IllegalTransition.class);
        }
    }
}
