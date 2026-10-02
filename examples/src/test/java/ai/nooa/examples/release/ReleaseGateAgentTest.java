package ai.nooa.examples.release;

import ai.nooa.AgentFactory;
import ai.nooa.llm.FakeLLMClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReleaseGateAgent")
class ReleaseGateAgentTest {

    private static FakeLLMClient llm() {
        var llm = new FakeLLMClient();
        llm.respondWith("Adds pagination to the list endpoint");
        return llm;
    }

    @Test
    void greenBuildAndApprovalDeploys() {
        try (var agent = AgentFactory.create(ReleaseGateAgent.class, llm())) {
            agent.run("TICKET-42", true, true);
            assertThat(agent.phase()).isEqualTo(ReleasePhase.DEPLOYED);
            assertThat(agent.history())
                .contains("BUILDING <- START_BUILD", "DEPLOYED <- DEPLOY");
        }
    }

    @Test
    void failingTestsReject() {
        try (var agent = AgentFactory.create(ReleaseGateAgent.class, llm())) {
            agent.run("TICKET-42", false, true);
            assertThat(agent.phase()).isEqualTo(ReleasePhase.REJECTED);
        }
    }

    @Test
    void declinedApprovalRejects() {
        try (var agent = AgentFactory.create(ReleaseGateAgent.class, llm())) {
            agent.run("TICKET-42", true, false);
            assertThat(agent.phase()).isEqualTo(ReleasePhase.REJECTED);
        }
    }
}
