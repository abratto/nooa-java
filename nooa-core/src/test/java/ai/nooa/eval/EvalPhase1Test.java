package ai.nooa.eval;

import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
import ai.nooa.context.Event;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.strategy.PredictStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("eval (Phase 1)")
class EvalPhase1Test {

    public static class EchoAgent extends Agent {
        public EchoAgent(UnifiedLLM llm) {
            super(llm);
        }

        @Generate(prompt = "Return exactly the provided text.")
        @Strategy(PredictStrategy.class)
        public String echo(String text) {
            throw new UnsupportedOperationException();
        }
    }

    // ---- record / replay ----

    @Test
    void recordThenReplayReproducesResults(@TempDir Path dir) throws Exception {
        Path recording = dir.resolve("recording.jsonl");
        FakeLLMClient live = new FakeLLMClient();
        live.respondWith("hello");
        EvalDataset dataset = EvalDataset.of("smoke",
            List.of(EvalCase.of("c1", "echo", Map.of("text", "hello"), "hello")));

        EvalReport recorded = EvalRunner.builder(EchoAgent.class, live)
            .mode(Mode.RECORD).recordingPath(recording).build().run(dataset);
        assertThat(recorded.aggregate().completion().passAt1()).isEqualTo(1.0);
        assertThat(recording).exists();

        // A dead live client proves replay never reaches the model.
        FakeLLMClient dead = new FakeLLMClient();
        EvalReport replayed = EvalRunner.builder(EchoAgent.class, dead)
            .mode(Mode.REPLAY).recordingPath(recording).build().run(dataset);

        assertThat(replayed.aggregate().completion().passAt1()).isEqualTo(1.0);
        assertThat(replayed.cases()).hasSize(1);
    }

    @Test
    void replayMissFailsHardByDefault(@TempDir Path dir) {
        Path recording = dir.resolve("empty.jsonl");
        EvalDataset dataset = EvalDataset.of("smoke",
            List.of(EvalCase.of("c1", "echo", Map.of("text", "hello"), "hello")));

        EvalReport report = EvalRunner.builder(EchoAgent.class, new FakeLLMClient())
            .mode(Mode.REPLAY).recordingPath(recording).build().run(dataset);

        // Misses are captured as run failures rather than thrown out of the runner.
        assertThat(report.aggregate().completion().passAt1()).isZero();
        assertThat(report.cases().get(0).trialSuccess()).containsExactly(false);
    }

    // ---- baseline / regression ----

    @Test
    void baselineDetectsRegression(@TempDir Path dir) {
        FakeLLMClient good = new FakeLLMClient();
        good.respondWith("hello");
        EvalDataset dataset = EvalDataset.of("smoke",
            List.of(EvalCase.of("c1", "echo", Map.of("text", "hello"), "hello")));
        EvalReport report = EvalRunner.builder(EchoAgent.class, good).build().run(dataset);

        Path baselineFile = dir.resolve("baseline.json");
        EvalBaseline baseline = EvalBaseline.from(report).save(baselineFile);
        assertThat(EvalBaseline.load(baselineFile).weightedMean())
            .isCloseTo(baseline.weightedMean(), org.assertj.core.data.Offset.offset(1e-9));

        FakeLLMClient bad = new FakeLLMClient();
        bad.respondWith("wrong");
        EvalReport worse = EvalRunner.builder(EchoAgent.class, bad).build().run(dataset);
        assertThat(baseline.regressed(worse, 0.01)).isTrue();
        assertThatThrownBy(() -> EvalAssertions.assertNoRegression(worse, baseline, 0.01))
            .isInstanceOf(AssertionError.class);
        assertThat(baseline.regressed(report, 0.01)).isFalse();
    }

    // ---- tool / safety / ops scorers ----

    @Test
    void toolScorersEvaluateSyntheticTrace() {
        EvalCase evalCase = new EvalCase(
            "c1", "run", Map.of(), null, Map.of(),
            List.of(),
            Set.of(),
            Map.of("requiredTools", List.of("search", "read"),
                   "forbiddenTools", List.of("rm"),
                   "maxToolCalls", 3.0));

        RunTrace trace = RunTrace.builder("c1", 0)
            .output("ok", true)
            .toolCalls(2, 0)
            .build();
        // two distinct tool names recorded out of band
        RunTrace withTools = new RunTrace(
            trace.caseId(), 0, "ok", true, 0, 0, 0, 0, 0, null, false,
            0, 2, 0, 0, true, List.of(), List.of("search", "read"), List.of(), Map.of());

        var required = new ai.nooa.eval.scorers.RequiredToolsScorer().score(evalCase, withTools);
        var forbidden = new ai.nooa.eval.scorers.ForbiddenToolsScorer().score(evalCase, withTools);
        var efficiency = new ai.nooa.eval.scorers.ToolEfficiencyScorer().score(evalCase, withTools);

        assertThat(required.passed()).isTrue();
        assertThat(required.value()).isEqualTo(1.0);
        assertThat(forbidden.passed()).isTrue();
        assertThat(efficiency.passed()).isTrue();
    }

    @Test
    void safetyAndOpsScorersEvaluateSyntheticTrace() {
        EvalCase evalCase = new EvalCase(
            "c1", "run", Map.of(), null, Map.of(), List.of(), Set.of(),
            Map.of("forbiddenErrorClasses", List.of("RestrictedCodeError"),
                   "maxDurationMs", 100.0,
                   "maxTotalTokens", 10.0));

        RunTrace trace = new RunTrace(
            "c1", 0, "sk-ABCDEFGHIJKLMNOPQRSTUVWX", true, 250, 1, 5, 5, 10,
            null, false, 0, 0, 0, 0, true,
            List.of("RestrictedCodeError"), List.of(), List.of(), Map.of());

        var policy = new ai.nooa.eval.scorers.PolicyComplianceScorer().score(evalCase, trace);
        var secret = new ai.nooa.eval.scorers.SecretLeakScorer().score(evalCase, trace);
        var latency = new ai.nooa.eval.scorers.LatencyBudgetScorer().score(evalCase, trace);
        var tokens = new ai.nooa.eval.scorers.TokenBudgetScorer().score(evalCase, trace);

        assertThat(policy.passed()).isFalse();
        assertThat(secret.passed()).isFalse();
        assertThat(latency.passed()).isFalse();
        assertThat(latency.value()).isEqualTo(100.0 / 250);
        // exactly at budget still passes
        assertThat(tokens.passed()).isTrue();
    }

    // ---- retry event ----

    @Test
    void runRecorderCountsRetryEvents() {
        FakeLLMClient llm = new FakeLLMClient();
        EchoAgent agent = AgentFactory.create(EchoAgent.class, llm);
        try {
            RunRecorder recorder = RunRecorder.attach(agent, ModelPricing.of());
            agent.eventManager().add(new Event.Retry("echo", 1, "structured-output", "GenerationError"));
            agent.eventManager().add(new Event.Retry("echo", 2, "structured-output", "GenerationError"));
            RunTrace trace = recorder.output("x", true).trace("c1", 0);
            assertThat(trace.retryCount()).isEqualTo(2);
        } finally {
            agent.close();
        }
    }
}
