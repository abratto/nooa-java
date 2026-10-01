package ai.nooa.eval;

import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
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

@DisplayName("eval (Phase 0)")
class EvalPhase0Test {

    public static class EchoAgent extends Agent {
        private String status = "done";

        public EchoAgent(UnifiedLLM llm) {
            super(llm);
        }

        @Generate(prompt = "Return exactly the provided text.")
        @Strategy(PredictStrategy.class)
        public String echo(String text) {
            throw new UnsupportedOperationException();
        }
    }

    // ---- EvalCase / EvalDataset ----

    @Test
    void datasetRoundTripsThroughJsonl(@TempDir Path dir) {
        EvalCase evalCase = new EvalCase(
            "case-1", "echo", Map.of("text", "hi"), "hi",
            Map.of("status", "done"),
            List.of(new EvalCase.Milestone("m1", 0.5, Map.of("status", "done"))),
            Set.of("canonical"),
            Map.of("note", "smoke"));
        EvalDataset dataset = EvalDataset.of("smoke", List.of(evalCase));

        Path file = dir.resolve("smoke.jsonl");
        dataset.save(file);
        EvalDataset loaded = EvalDataset.load(file);

        assertThat(loaded.id()).isEqualTo("smoke");
        assertThat(loaded.cases()).hasSize(1);
        EvalCase round = loaded.cases().get(0);
        assertThat(round.id()).isEqualTo("case-1");
        assertThat(round.targetMethod()).isEqualTo("echo");
        assertThat(round.input()).isEqualTo(Map.of("text", "hi"));
        assertThat(round.expected()).isEqualTo("hi");
        assertThat(round.expectedGoal()).isEqualTo(Map.of("status", "done"));
        assertThat(round.tags()).containsExactly("canonical");
        assertThat(round.milestones()).hasSize(1);
    }

    // ---- ModelPricing ----

    @Test
    void modelPricingComputesCostAndHandlesUnknown() {
        ModelPricing pricing = ModelPricing.of().with("gpt-4o", 0.0025, 0.01);

        assertThat(pricing.cost("gpt-4o", 1000, 1000)).contains(0.0125);
        assertThat(pricing.cost("gpt-4o-2024-08-06", 1000, 0)).contains(0.0025);
        assertThat(pricing.cost("unknown-model", 1000, 1000)).isEmpty();
        assertThat(pricing.cost(null, 1, 1)).isEmpty();
    }

    // ---- CompletionMetrics ----

    @Test
    void completionMetricsComputeEnvelope() {
        CompletionMetrics all = CompletionMetrics.fromTrials(List.of(true, true, true));
        assertThat(all.passAt1()).isEqualTo(1.0);
        assertThat(all.passHatK()).isEqualTo(1.0);
        assertThat(all.flakiness()).isZero();

        CompletionMetrics mixed = CompletionMetrics.fromTrials(List.of(true, false, true));
        assertThat(mixed.passAt1()).isCloseTo(2.0 / 3, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(mixed.passAtK()).isEqualTo(1.0);
        assertThat(mixed.passHatK()).isZero();
        assertThat(mixed.flakiness()).isEqualTo(1.0);

        CompletionMetrics none = CompletionMetrics.fromTrials(List.of(false, false));
        assertThat(none.passAtK()).isZero();
    }

    // ---- Goal verification ----

    @Test
    void defaultGoalVerifierChecksReflectiveState() {
        FakeLLMClient llm = new FakeLLMClient();
        llm.respondWith("hi");
        EchoAgent agent = AgentFactory.create(EchoAgent.class, llm);
        try {
            String output = agent.echo("hi");
            RunTrace trace = RunTrace.builder("c", 0).output(output, true).build();
            PostState state = new ReflectivePostState(agent, null);

            GoalVerifier.Completion ok = new DefaultGoalVerifier()
                .verify(EvalCase.of("c", "echo", Map.of("text", "hi"), "hi"), trace, state);
            assertThat(ok.achieved()).isTrue();

            GoalVerifier.Completion goal = new DefaultGoalVerifier()
                .verify(new EvalCase("c", "echo", Map.of(), null,
                    Map.of("status", "done"), List.of(), Set.of(), Map.of()), trace, state);
            assertThat(goal.achieved()).isTrue();
            assertThat(goal.fraction()).isEqualTo(1.0);
        } finally {
            agent.close();
        }
    }

    // ---- RunRecorder ----

    @Test
    void runRecorderAggregatesTokensAndCalls() {
        FakeLLMClient llm = new FakeLLMClient();
        llm.respondWith("hi");
        EchoAgent agent = AgentFactory.create(EchoAgent.class, llm);
        try {
            RunRecorder recorder = RunRecorder.attach(agent, ModelPricing.of().with("fake-model", 0.01, 0.02));
            String output = agent.echo("hi");
            RunTrace trace = recorder.output(output, true).durationMs(0).trace("c", 0);

            assertThat(trace.llmCallCount()).isEqualTo(1);
            assertThat(trace.totalTokens()).isEqualTo(2);
            assertThat(trace.output()).isEqualTo("hi");
            assertThat(trace.terminatedCleanly()).isTrue();
            assertThat(trace.pricingKnown()).isTrue();
            assertThat(trace.costUsd()).isNotNull();
        } finally {
            agent.close();
        }
    }

    // ---- EvalRunner end-to-end ----

    @Test
    void runnerScoresPassingCase() {
        FakeLLMClient llm = new FakeLLMClient();
        llm.respondWith("hello");
        EvalDataset dataset = EvalDataset.of("smoke",
            List.of(EvalCase.of("c1", "echo", Map.of("text", "hello"), "hello")));

        EvalReport report = EvalRunner.builder(EchoAgent.class, llm).build().run(dataset);

        assertThat(report.cases()).hasSize(1);
        assertThat(report.aggregate().completion().passAt1()).isEqualTo(1.0);
        assertThat(report.aggregate().weightedMean()).isGreaterThan(0.0);
        EvalAssertions.assertPass(report, 0.9);
        EvalAssertions.assertGate(report, "ExactMatch", 1.0);
        assertThat(report.toJson()).contains("c1");
        assertThat(report.toMarkdown()).contains("pass@1");
    }

    @Test
    void runnerDetectsFlakinessAcrossTrials() {
        FakeLLMClient llm = new FakeLLMClient();
        llm.respondWith("hello");   // trial 0 passes
        llm.respondWith("wrong");   // trial 1 fails
        EvalDataset dataset = EvalDataset.of("smoke",
            List.of(EvalCase.of("c1", "echo", Map.of("text", "hello"), "hello")));

        EvalReport report = EvalRunner.builder(EchoAgent.class, llm)
            .trials(2).build().run(dataset);

        CompletionMetrics completion = report.aggregate().completion();
        assertThat(completion.passAt1()).isEqualTo(0.5);
        assertThat(completion.passAtK()).isEqualTo(1.0);
        assertThat(completion.passHatK()).isZero();
        assertThat(completion.flakiness()).isEqualTo(1.0);
    }

    @Test
    void assertionsFailWhenThresholdNotMet() {
        FakeLLMClient llm = new FakeLLMClient();
        llm.respondWith("wrong");
        EvalDataset dataset = EvalDataset.of("smoke",
            List.of(EvalCase.of("c1", "echo", Map.of("text", "hello"), "hello")));
        EvalReport report = EvalRunner.builder(EchoAgent.class, llm).build().run(dataset);

        assertThatThrownBy(() -> EvalAssertions.assertPass(report, 0.9))
            .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> EvalAssertions.assertGate(report, "ExactMatch", 1.0))
            .isInstanceOf(AssertionError.class);
    }
}
