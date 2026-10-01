package ai.nooa.eval;

import ai.nooa.eval.judge.FakeJudge;
import ai.nooa.eval.judge.Judge;
import ai.nooa.eval.judge.JudgeScorer;
import ai.nooa.eval.judge.LlmJudge;
import ai.nooa.eval.scorers.ContextGroundingScorer;
import ai.nooa.eval.scorers.LoopTerminationScorer;
import ai.nooa.eval.scorers.MeltdownScorer;
import ai.nooa.eval.scorers.PermissionComplianceScorer;
import ai.nooa.eval.scorers.StepEfficiencyScorer;
import ai.nooa.llm.FakeLLMClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("eval (Phase 2)")
class EvalPhase2Test {

    private static EvalReport.CaseReport caseReport(String id, int steps, boolean success) {
        double p = success ? 1.0 : 0.0;
        return new EvalReport.CaseReport(id, Set.of(), List.of(), p,
            GoalVerifier.Completion.unscored(), new CompletionMetrics(p, p, p, 0),
            List.of(success), "out", steps);
    }

    // ---- judge ----

    @Test
    void fakeJudgeDrivesJudgeScorer() {
        Judge judge = new FakeJudge(new Judge.Verdict(0.8, "looks good"));
        JudgeScorer scorer = new JudgeScorer(judge);
        EvalCase evalCase = new EvalCase("c", "run", Map.of(), null, Map.of(),
            List.of(), Set.of(), Map.of("judgeCriteria", List.of("be correct")));
        RunTrace trace = RunTrace.builder("c", 0).output("answer", true).build();

        Score score = scorer.score(evalCase, trace);
        assertThat(score.value()).isEqualTo(0.8);
        assertThat(score.passed()).isTrue();
        assertThat(score.detail()).isEqualTo("looks good");
    }

    @Test
    void llmJudgeParsesStructuredVerdict() {
        FakeLLMClient judgeModel = new FakeLLMClient();
        judgeModel.respondWith("{\"score\": 0.65, \"rationale\": \"partially correct\"}");
        Judge judge = new LlmJudge(judgeModel);
        EvalCase evalCase = EvalCase.of("c", "run", Map.of("text", "hi"), "hi");
        RunTrace trace = RunTrace.builder("c", 0).output("hi", true).build();

        Judge.Verdict verdict = judge.judge(evalCase, trace, List.of("correctness"));
        assertThat(verdict.score()).isEqualTo(0.65);
        assertThat(verdict.rationale()).isEqualTo("partially correct");
    }

    // ---- context grounding ----

    @Test
    void contextGroundingScoresInputRecall() {
        EvalCase evalCase = EvalCase.of("c", "run",
            Map.of("text", "the quick brown fox"), null);
        RunTrace grounded = RunTrace.builder("c", 0)
            .signal("lastPrompt", "system: the quick brown fox jumps").build();
        RunTrace ungrounded = RunTrace.builder("c", 0)
            .signal("lastPrompt", "unrelated words only").build();

        var scorer = new ContextGroundingScorer();
        assertThat(scorer.score(evalCase, grounded).value()).isEqualTo(1.0);
        assertThat(scorer.score(evalCase, ungrounded).value()).isZero();
    }

    @Test
    void contextGroundingNotApplicableWithoutPrompt() {
        EvalCase evalCase = EvalCase.of("c", "run", Map.of("text", "hi"), null);
        RunTrace trace = RunTrace.builder("c", 0).output("hi", true).build();
        assertThat(new ContextGroundingScorer().score(evalCase, trace).applicable()).isFalse();
    }

    // ---- loop / step / meltdown ----

    @Test
    void loopAndStepScorers() {
        EvalCase evalCase = new EvalCase("c", "run", Map.of(), null, Map.of(),
            List.of(), Set.of(), Map.of("maxSteps", 2.0));
        RunTrace clean = RunTrace.builder("c", 0).output("ok", true).steps(2)
            .terminatedCleanly(true).build();
        RunTrace overrun = RunTrace.builder("c", 0).output("ok", true).steps(4)
            .terminatedCleanly(true).build();

        assertThat(new LoopTerminationScorer().score(evalCase, clean).passed()).isTrue();
        assertThat(new LoopTerminationScorer().score(evalCase, overrun).passed()).isFalse();
        assertThat(new StepEfficiencyScorer().score(evalCase, overrun).value())
            .isEqualTo(0.5);
    }

    @Test
    void meltdownScorerUsesToolEntropySignal() {
        EvalCase evalCase = new EvalCase("c", "run", Map.of(), null, Map.of(),
            List.of(), Set.of(), Map.of("maxToolEntropy", 1.5));
        RunTrace calm = RunTrace.builder("c", 0).output("ok", true)
            .signal("toolEntropy", 0.5).build();
        RunTrace chaotic = RunTrace.builder("c", 0).output("ok", true)
            .signal("toolEntropy", 2.4).build();

        assertThat(new MeltdownScorer().score(evalCase, calm).passed()).isTrue();
        assertThat(new MeltdownScorer().score(evalCase, chaotic).passed()).isFalse();
    }

    // ---- permission compliance ----

    @Test
    void permissionComplianceScoresDeniedAccess() {
        var scorer = new PermissionComplianceScorer();
        EvalCase evalCase = EvalCase.of("c", "run", Map.of(), null);

        RunTrace none = RunTrace.builder("c", 0).output("ok", true).build();
        assertThat(scorer.score(evalCase, none).applicable()).isFalse();

        RunTrace denied = RunTrace.builder("c", 0).output("ok", true)
            .signal("permissionDecisions", List.of(Map.of(
                "resource", "file", "detail", "/etc/passwd",
                "level", "DENY", "reason", "denied by policy")))
            .build();
        var deniedScore = scorer.score(evalCase, denied);
        assertThat(deniedScore.passed()).isFalse();
        assertThat(deniedScore.detail()).contains("/etc/passwd");

        RunTrace allowed = RunTrace.builder("c", 0).output("ok", true)
            .signal("permissionDecisions", List.of(Map.of(
                "resource", "file", "detail", "/tmp/x",
                "level", "ALLOW", "reason", "")))
            .build();
        assertThat(scorer.score(evalCase, allowed).passed()).isTrue();
    }

    // ---- horizon ----
    @Test
    void horizonReliabilityBucketsBySteps() {
        List<EvalReport.CaseReport> cases = List.of(
            caseReport("short", 1, true),
            caseReport("medium-a", 3, true),
            caseReport("medium-b", 3, false),
            caseReport("long", 10, false));

        var buckets = HorizonReliability.of(cases);
        assertThat(buckets).hasSize(3);
        assertThat(buckets.get(0).label()).isEqualTo("1");
        assertThat(buckets.get(1).cases()).isEqualTo(2);
        assertThat(buckets.get(1).completion().passAt1()).isEqualTo(0.5);
        assertThat(buckets.get(2).label()).isEqualTo("8+");
        assertThat(buckets.get(2).completion().passAt1()).isZero();
    }
}
