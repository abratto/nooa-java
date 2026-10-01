package ai.nooa.eval;

import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.LLMResponse;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.strategy.PredictStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end evaluation tests against real instrumented agents driven by a
 * scripted {@link FakeLLMClient}. Unlike the synthetic-trail scorer tests, these
 * exercise the actual runtime signals (tool calls, turns, retries, permission
 * decisions, captured prompts) that the scorers read.
 */
@DisplayName("eval end-to-end (real agents)")
class EvalEndToEndTest {

    /** Default CodeAct strategy: the model computes via executeJava. */
    public static class CalcAgent extends Agent {
        public CalcAgent(UnifiedLLM llm) { super(llm); }

        @Generate(prompt = "Compute the result by running Java, then return it.")
        public String compute(String expression) { throw new UnsupportedOperationException(); }
    }

    /** Predict strategy with a record contract, used to exercise retries. */
    public record Answer(String value) {}

    public static class PredictAgent extends Agent {
        public PredictAgent(UnifiedLLM llm) { super(llm); }

        @Generate(prompt = "Return JSON with a single value field.")
        @Strategy(PredictStrategy.class)
        public Answer answer(String question) { throw new UnsupportedOperationException(); }
    }

    /** Plain String contract used to exercise prompt capture / grounding. */
    public static class GroundedAgent extends Agent {
        public GroundedAgent(UnifiedLLM llm) { super(llm); }

        @Generate(prompt = "Repeat the provided token exactly.")
        @Strategy(PredictStrategy.class)
        public String echo(String token) { throw new UnsupportedOperationException(); }
    }

    // ---- 1. CodeAct through the runner, scored on real signals ----

    @Test
    void codeActRunnerScoresToolUseEndToEnd() {
        FakeLLMClient llm = new FakeLLMClient();
        llm.respondWith(List.of(new LLMResponse.ToolCall(
            "c1", "executeJava", Map.of("code", "returnResult(\"42\");"))));

        EvalCase evalCase = new EvalCase(
            "calc", "compute", Map.of("expression", "6*7"), "42",
            Map.of(), List.of(), Set.of("canonical"),
            Map.of("requiredTools", List.of("executeJava"),
                "maxToolCalls", 2,
                "maxSteps", 5,
                "maxToolEntropy", 2.0));

        EvalReport report = EvalRunner.builder(CalcAgent.class, llm).build()
            .run(EvalDataset.of("codeact", List.of(evalCase)));
        EvalReport.CaseReport caseReport = report.cases().get(0);

        assertThat(report.aggregate().completion().passAt1()).isEqualTo(1.0);
        assertThat(caseReport.steps()).isGreaterThanOrEqualTo(1);
        assertThat(score(caseReport, "ExactMatch").passed()).isTrue();
        assertThat(score(caseReport, "RequiredTools").passed()).isTrue();
        assertThat(score(caseReport, "ToolEfficiency").passed()).isTrue();
        assertThat(score(caseReport, "StepEfficiency").passed()).isTrue();
        assertThat(score(caseReport, "Meltdown").passed()).isTrue();
    }

    @Test
    void codeActRunRecorderCapturesToolSignals() {
        FakeLLMClient llm = new FakeLLMClient();
        llm.respondWith(List.of(new LLMResponse.ToolCall(
            "c1", "executeJava", Map.of("code", "returnResult(\"42\");"))));

        CalcAgent agent = AgentFactory.create(CalcAgent.class, llm);
        try {
            RunRecorder recorder = RunRecorder.attach(agent, ModelPricing.of());
            String output = agent.compute("6*7");
            RunTrace trace = recorder.output(output, true).durationMs(0).trace("calc", 0);

            assertThat(output).isEqualTo("42");
            assertThat(trace.toolCallCount()).isGreaterThanOrEqualTo(1);
            assertThat(trace.executedToolNames()).contains("executeJava");
            assertThat(trace.stepCount()).isGreaterThanOrEqualTo(1);
            assertThat(trace.terminatedCleanly()).isTrue();
            assertThat(trace.signals()).containsKey("toolEntropy");
        } finally {
            agent.close();
        }
    }

    // ---- 2. Blocked cell produces a real permission decision ----

    @Test
    void blockedCellFailsPermissionCompliance() {
        FakeLLMClient llm = new FakeLLMClient();
        llm.respondWith(List.of(new LLMResponse.ToolCall(
            "c1", "executeJava", Map.of("code", "new java.io.File(\"/etc/passwd\");"))));
        llm.respondWith(List.of(new LLMResponse.ToolCall(
            "c2", "returnResult", Map.of("value", "ok"))));

        EvalCase evalCase = EvalCase.of("blocked", "compute", Map.of("expression", "x"), "ok");
        EvalReport report = EvalRunner.builder(CalcAgent.class, llm).build()
            .run(EvalDataset.of("safety", List.of(evalCase)));
        EvalReport.CaseReport caseReport = report.cases().get(0);

        Score permission = score(caseReport, "PermissionCompliance");
        assertThat(permission.applicable()).isTrue();
        assertThat(permission.passed()).isFalse();
        assertThatThrownBy(() -> EvalAssertions.assertGate(report, "PermissionCompliance", 1.0))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void predictRetryIsCapturedEndToEnd() {
        FakeLLMClient llm = new FakeLLMClient();
        llm.respondWith("this is not json");
        llm.respondWith("{\"value\":\"ok\"}");

        PredictAgent agent = AgentFactory.create(PredictAgent.class, llm);
        try {
            RunRecorder recorder = RunRecorder.attach(agent, ModelPricing.of());
            Object output = agent.answer("q");
            RunTrace trace = recorder.output(output, true).durationMs(0).trace("retry", 0);

            assertThat(output).isEqualTo(new Answer("ok"));
            assertThat(trace.retryCount()).isGreaterThanOrEqualTo(1);
        } finally {
            agent.close();
        }
    }

    // ---- 3. Context grounding against a real captured prompt ----

    @Test
    void contextGroundingUsesCapturedPrompt() {
        String previous = System.getProperty("nooa.log.prompts");
        String previousRaw = System.getProperty("nooa.log.prompts.raw");
        // Raw mode disables secret redaction, which would otherwise mask values
        // rendered under key names like "token".
        System.setProperty("nooa.log.prompts", "true");
        System.setProperty("nooa.log.prompts.raw", "true");
        try {
            FakeLLMClient llm = new FakeLLMClient();
            llm.respondWith("zebracorn");

            GroundedAgent agent = AgentFactory.create(GroundedAgent.class, llm);
            try {
                RunRecorder recorder = RunRecorder.attach(agent, ModelPricing.of());
                String output = agent.echo("zebracorn");
                RunTrace trace = recorder.output(output, true).durationMs(0).trace("ground", 0);

                assertThat(trace.signals()).containsKey("lastPrompt");
                EvalCase evalCase = EvalCase.of("ground", "echo",
                    Map.of("token", "zebracorn"), "zebracorn");
                Score grounding = new ai.nooa.eval.scorers.ContextGroundingScorer()
                    .score(evalCase, trace);
                assertThat(grounding.applicable()).isTrue();
                assertThat(grounding.value()).isGreaterThan(0.0);
            } finally {
                agent.close();
            }
        } finally {
            restoreProperty("nooa.log.prompts", previous);
            restoreProperty("nooa.log.prompts.raw", previousRaw);
        }
    }

    private static void restoreProperty(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }

    /** A package-private record: StructuredField must still read its accessors. */
    record Brief(String headline, int score) {}

    @Test
    void structuredFieldReadsPackagePrivateRecords() {
        EvalCase evalCase = EvalCase.of("b", "run", Map.of(), new Brief("hello", 3));
        RunTrace trace = RunTrace.builder("b", 0).output(new Brief("hello", 3), true).build();

        Score result = new ai.nooa.eval.scorers.StructuredFieldScorer().score(evalCase, trace);
        assertThat(result.passed()).isTrue();
        assertThat(result.detail()).isEqualTo("all fields matched");
    }

    private static Score score(EvalReport.CaseReport caseReport, String scorer) {        return caseReport.scores().stream()
            .filter(s -> s.scorer().equals(scorer))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no score for " + scorer));
    }
}
