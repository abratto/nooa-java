package ai.nooa.examples.incident;

import ai.nooa.eval.EvalCase;
import ai.nooa.eval.EvalDataset;
import ai.nooa.eval.EvalReport;
import ai.nooa.eval.EvalRunner;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.LLMResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IncidentAgent evaluation")
class IncidentEvalTest {

    /** Scripts the model calls for the resolved and escalated cases, in order. */
    private static FakeLLMClient scripted() {
        var llm = new FakeLLMClient();
        // Case 1 — respondAuto (assess, scanTelemetry, investigate, proposePlan, writePostmortem)
        llm.respondWith("{\"level\":\"HIGH\",\"rationale\":\"breach\"}");
        llm.respondWith(scanTelemetryCall("c1"));
        llm.respondWith("{\"summary\":\"db pool exhaustion\",\"evidence\":[\"HikariPool timeouts\"]}");
        llm.respondWith("{\"steps\":[\"rollback\"],\"risk\":\"low\",\"rollback\":\"redeploy\"}");
        llm.respondWith("{\"summary\":\"resolved\",\"followUps\":[\"add alert\"]}");
        // Case 2 — respondRejected (assess, scanTelemetry, investigate, proposePlan)
        llm.respondWith("{\"level\":\"HIGH\",\"rationale\":\"breach\"}");
        llm.respondWith(scanTelemetryCall("c2"));
        llm.respondWith("{\"summary\":\"db pool exhaustion\",\"evidence\":[\"HikariPool timeouts\"]}");
        llm.respondWith("{\"steps\":[\"rollback\"],\"risk\":\"low\",\"rollback\":\"redeploy\"}");
        return llm;
    }

    private static List<LLMResponse.ToolCall> scanTelemetryCall(String id) {
        return List.of(new LLMResponse.ToolCall(id, "executeJava", Map.of("code",
            "returnResult(\"HikariPool timeouts; p99 2.4s\");")));
    }

    @Test
    void scoresResolvedAndEscalatedOutcomesByGoalState() {
        var scenario = IncidentScenarios.checkoutLatency();
        var resolved = new EvalCase("resolved", "respondAuto", Map.of(), null,
            Map.of("status", "Resolved"), List.of(), Set.of("happy-path"), Map.of());
        var escalated = new EvalCase("escalated", "respondRejected", Map.of(), null,
            Map.of("status", "Escalated"), List.of(), Set.of("rejection"), Map.of());
        EvalDataset dataset = EvalDataset.of("incident", List.of(resolved, escalated));

        EvalReport report = EvalRunner.builder(IncidentAgent.class, scripted())
            .extraArgs(scenario)
            .build()
            .run(dataset);

        assertThat(report.aggregate().completion().passAt1()).isEqualTo(1.0);
        assertThat(report.cases().get(0).goal().achieved()).isTrue();
        assertThat(report.cases().get(1).goal().achieved()).isTrue();
    }
}
