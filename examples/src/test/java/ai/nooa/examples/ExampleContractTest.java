package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.llm.FakeLLMClient;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExampleContractTest {

    @Test
    void representativeAgentsCanBeCreatedThroughAgentFactory() {
        var llm = new FakeLLMClient();
        var agents = new Agent[] {
            AgentFactory.create(GreetingAgent.class, llm),
            AgentFactory.create(SentimentAgent.class, llm),
            AgentFactory.create(SupportAgent.class, llm, Map.of("widget", 5)),
            AgentFactory.create(ResearchAgent.class, llm),
            AgentFactory.create(ProjectAgent.class, llm),
            AgentFactory.create(DebugAgent.class, llm),
            AgentFactory.create(StrategyDemoAgent.class, llm),
            AgentFactory.create(NewsDigestAgent.class, llm),
            AgentFactory.create(WeatherAgent.class, llm),
            AgentFactory.create(LegalIntakeAgent.class, llm),
            AgentFactory.create(ShellDemoAgent.class, llm),
            AgentFactory.create(SnapshotDemoAgent.class, llm),
            AgentFactory.create(SummarizationDemoAgent.class, llm),
            AgentFactory.create(TraceDemoAgent.class, llm)
        };

        try {
            assertThat(agents).hasSize(14);
            assertThat(agents).allMatch(agent -> agent.getClass() != agent.getClass().getSuperclass());
        } finally {
            for (Agent agent : agents) {
                agent.close();
            }
        }
    }

    @Test
    void deterministicExampleSurfacesKeepTheirStateInJava() {
        var llm = new FakeLLMClient();
        var support = new SupportAgent(llm, Map.of("widget", 5));
        var research = new ResearchAgent(llm);
        var project = new ProjectAgent(llm);

        try {
            assertThat(support.getStock("widget")).isEqualTo(5);
            assertThat(research.search("virtual threads").snippet())
                .contains("virtual threads");
            project.addTask("Write docs");
            project.addTask("Fix bugs");
            project.completeTask("Write docs");
            assertThat(project.formatProjectStatus()).isEqualTo("Tasks: 1/2 complete");
        } finally {
            support.close();
            research.close();
            project.close();
        }
    }
}
