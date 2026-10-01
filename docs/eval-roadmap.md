# SDK Roadmap: Agent Evaluation (`ai.nooa.eval`)

This document is the file-level implementation plan for first-class agent
evaluation in NOOA-Java. It is written so an agent (or contributor) can pick
up any phase independently. It builds directly on the existing observability
surface (events, tracing, ATIF, prompt capture) and adds no new Maven artifact:
everything lives in the `nooa-core` module under package `ai.nooa.eval`.

> **Design principle.** NOOA owns the event stream, per-call scoping, typed
> return values, and the callable-method surface. Evaluation is therefore an
> **event subscriber + dataset runner**, not a bolted-on framework. Most
> metrics require zero changes to `nooa-core` beyond this package.

## Status

- **Phase 0 — implemented.** `EvalCase`, `EvalDataset`, `EvalRunner` (LIVE),
  `RunRecorder`, `RunTrace`, `Scorer`, `Score`, `Rubric`, `GoalVerifier` +
  `PostState`/`ReflectivePostState`/`EnvironmentSnapshot`/`DefaultGoalVerifier`,
  `ModelPricing`, `EvalReport`, `EvalAssertions`, and the
  `ExactMatch`/`StructuredField`/`Contains`/`Milestone` scorers.
- **Phase 1 — implemented.** `RecordingLLMClient` / `ReplayLLMClient` +
  `Mode`, tool/safety/ops scorers, `EvalBaseline` +
  `EvalAssertions.assertNoRegression`, and the small core addition
  `Event.Retry` (emitted by `PredictStrategy` and postcondition validation).
- **Phase 2 — implemented.** `judge/Judge` SPI, opt-in `LlmJudge`,
  `FakeJudge`, `JudgeScorer`; `ContextGroundingScorer` (promoted from the
  test-only harness); `LoopTerminationScorer`, `StepEfficiencyScorer`,
  `MeltdownScorer` (tool-call entropy signal); and `HorizonReliability`
  (pass^k bucketed by step count, rendered in the Markdown report).
- **Phase 3 (JUnit/CLI, SQLite history, PermissionDecision integration) — pending.**

Tests: `EvalPhase0Test`, `EvalPhase1Test`, `EvalPhase2Test` (21 tests).

## Decisions log (confirmed)

1. **Packaging:** `ai.nooa.eval` package inside `nooa-core` — no third artifact.
2. **LLM-as-judge:** `Judge` SPI ships in P2; one reference `LlmJudge` is
   **disabled unless a judge model is explicitly configured**; `FakeJudge`
   for deterministic tests.
3. **Cost:** `ModelPricing` added in P0. Programmatic overrides
   (`ModelPricing.with(...)`) plus env fallback
   (`NOOA_PRICE_<MODEL>=<promptPer1k>/<completionPer1k>`).
4. **Record/replay:** built in P1. `RecordingLLMClient` / `ReplayLLMClient`;
   fingerprint-based, **fails hard on miss by default**, configurable via
   `EvalRunner.fallThroughOnMiss(true)`.
5. **Integration surface:** dependency-free production Java API +
   `EvalAssertions` (throws `AssertionError`, works in any runner). A JUnit 5
   extension and a CLI are deferred to P3. Reference JUnit usage lives in the
   `examples` module, not `nooa-core` test scope.
6. **Reports:** JSON + Markdown + console only (no SQLite history for now).
7. **Goal-state access:** both paths. Default `ReflectivePostState` (agent
   fields + `context()` + `MemoryStore` if present); optional user-supplied
   `EnvironmentSnapshot` callback for filesystem/external state.
8. **Trial defaults:** reliability suites default K=3; capability suites K=1;
   both configurable via `EvalRunner.trials(int)`.
9. **Partial credit:** `MilestoneScorer` hook defined in P0, scoring logic in
   P1.
10. **Retry observability:** add `Event.Retry` to `nooa-core` in P1 (small,
    justified core addition) so retry metrics are first-class rather than
    inferred.

## Non-goals

- No benchmark leaderboards, hosted services, or dashboards.
- No replacement for `PromptRecorder`, `AtifExporter`, or `Tracing` — the eval
  layer consumes their data, it does not duplicate them.
- No new runtime dependency in `nooa-core` main scope (JUnit/AssertJ stay
  test-scope).

## Package layout

```
nooa-core/src/main/java/ai/nooa/eval/
├── EvalCase.java                 record: id, target, input, expected, goal, milestones, tags, metadata
├── EvalDataset.java              List<EvalCase>; load/save JSONL
├── EvalRunner.java               modes, trials, scoring, baseline; run(dataset) -> EvalReport
├── Mode.java                     enum LIVE | RECORD | REPLAY
├── RunRecorder.java              AutoCloseable event subscriber -> RunTrace
├── RunTrace.java                 raw per-run signals
├── PostState.java                interface: field(name), fields(), memory(), workspace()
├── ReflectivePostState.java      default impl over agent reflection + context()
├── EnvironmentSnapshot.java      interface for user-supplied external state
├── Scorer.java                   SPI: name(), score(EvalCase, RunTrace)
├── Score.java                    record: scorer, value, passed, detail, data
├── Rubric.java                   weighted scorer aggregate
├── GoalVerifier.java             SPI + Completion record
├── CompletionMetrics.java        pass@1, pass@k, pass^k, flakiness
├── ReliabilitySpread.java        per-case trial spread
├── EvalReport.java               report model + toJson()/toMarkdown()
├── EvalBaseline.java             save/load/compare reports
├── EvalAssertions.java           dependency-free pass/fail gate
├── ModelPricing.java             token -> USD
├── scorers/
│   ├── ExactMatchScorer.java
│   ├── StructuredFieldScorer.java
│   ├── ContainsScorer.java
│   ├── RequiredToolsScorer.java
│   ├── ForbiddenToolsScorer.java
│   ├── ToolEfficiencyScorer.java
│   ├── ToolRecoveryScorer.java
│   ├── PolicyComplianceScorer.java
│   ├── SecretLeakScorer.java
│   ├── LatencyBudgetScorer.java
│   ├── TokenBudgetScorer.java
│   ├── CostBudgetScorer.java
│   ├── ObservabilityCoverageScorer.java
│   ├── ContextGroundingScorer.java
│   ├── LoopTerminationScorer.java
│   ├── StepEfficiencyScorer.java
│   └── MilestoneScorer.java
├── judge/
│   ├── Judge.java                SPI + Verdict record
│   ├── LlmJudge.java             opt-in reference impl (disabled by default)
│   └── FakeJudge.java            scripted verdicts for tests
nooa-core/src/main/java/ai/nooa/llm/
├── RecordingLLMClient.java       extends UnifiedLLM; wraps a delegate, writes JSONL
└── ReplayLLMClient.java          extends UnifiedLLM; fingerprint lookup, fail-hard default
```

## Core type sketches

```java
public record EvalCase(
    String id,
    String targetMethod,
    Map<String, Object> input,
    Object expected,                     // expected return value (nullable)
    Map<String, Object> expectedGoal,    // field -> value goal state (nullable)
    List<Milestone> milestones,          // nullable -> empty
    Set<String> tags,                    // canonical, paraphrase, edge, ambiguous, ...
    Map<String, Object> metadata) {

  public record Milestone(String name, double weight, Map<String, Object> expected) {}
}

public record RunTrace(
    String caseId, int trial, Object output,
    long durationMs, int llmCallCount,
    int promptTokens, int completionTokens, int totalTokens,
    Double costUsd, boolean pricingKnown,
    int retryCount, int toolCallCount, int toolErrorCount,
    int stepCount, boolean terminatedCleanly,
    List<String> errorClasses, List<String> executedToolNames,
    List<String> promptFingerprints,
    Map<String, Object> signals) {}

public interface Scorer {
  String name();
  Score score(EvalCase c, RunTrace t);
}

public record Score(String scorer, double value, boolean passed,
                    String detail, Map<String, Object> data) {
  public static Score of(String scorer, boolean passed, String detail);
  public static Score of(String scorer, double value, boolean passed, String detail);
}

public interface GoalVerifier {
  Completion verify(EvalCase c, RunTrace t, PostState state);
  record Completion(boolean achieved, double fraction, List<String> unmet) {}
}

public interface PostState {
  Optional<Object> field(String name);
  Map<String, Object> fields();
  Optional<MemoryStore> memory();
  Optional<Path> workspace();
}

public record CompletionMetrics(
    double passAt1, double passAtK, double passHatK, double flakiness) {}

public record Rubric(List<WeightedScorer> scorers) {
  public record WeightedScorer(Scorer scorer, double weight) {}
  public static Rubric defaults(); // 40 completion / 20 tool-use / 15 safety / 15 stability / 10 ops
  public double aggregate(List<Score> scores);
}

public final class EvalAssertions {
  public static void assertPass(EvalReport r, double minWeighted);
  public static void assertNoRegression(EvalReport current, EvalReport baseline, double tolerance);
  public static void assertGate(EvalReport r, String scorer, double min); // hard gate, never masked by aggregate
}
```

## Evaluation dimensions -> scorers

| # | Dimension | Scorers | Signal source |
|---|---|---|---|
| 1 | Task correctness | `ExactMatchScorer`, `StructuredFieldScorer`, `ContainsScorer`, `GoalVerifier` | return value, `genericReturnType()`, `expectedGoal` |
| 2 | Tool-use quality | `RequiredToolsScorer`, `ForbiddenToolsScorer`, `ToolEfficiencyScorer`, `ToolRecoveryScorer` | `ToolCallEvent`, `ToolResultEvent`, `AgentDoc.visibleMethods` |
| 3 | Reasoning | `LlmJudge` (P2), `MilestoneScorer` | trajectory + criteria |
| 4 | Safety | `PolicyComplianceScorer`, `SecretLeakScorer` | errors, `Permissions`, `Event.Retry` |
| 5 | Reliability (goal completion + stability) | `GoalVerifier`, `CompletionMetrics`, `ReliabilitySpread`, robustness-by-tag | multi-trial runs |
| 6 | Latency/cost | `LatencyBudgetScorer`, `TokenBudgetScorer`, `CostBudgetScorer` | event timestamps, `LLMComplete`, `ModelPricing` |
| 7 | Trace quality | `ObservabilityCoverageScorer` | events/spans |
| 8 | Prompt/context | `ContextGroundingScorer`, `ContextWindowStats` | `PromptBuilt`, `ContextWindowStats` |
| 9 | Planning | `LoopTerminationScorer`, `StepEfficiencyScorer` | `BeforeTurn`/`AfterTurn` |
| 10 | Regression | `EvalBaseline` + `EvalAssertions` | saved prior report |

## Reliability suite (first-class)

The literature (τ-bench, SWE-bench, METR, ReliabilityBench) distinguishes four
senses; all four are covered:

1. **Goal completion** — `GoalVerifier` checks post-run state against
   `expectedGoal`; `CompletionMetrics` computes the unbiased estimators:
   - `pass@1` = mean per-trial success (capability)
   - `pass@k` = at least one of k succeeds (optimistic bound)
   - `pass^k` = all k succeed (consistency / reliability; primary for
     customer-facing or side-effecting agents)
   - `flakiness` = fraction of cases whose pass/fail flips across k
2. **Long-horizon** — bucket cases by `stepCount`; emit RDC-flavored table
   (`pass^k` vs step bucket) and `StepsToCompletion`.
3. **Stability** — output consistency (structured-field agreement across
   trials) and robustness delta by tag (canonical vs paraphrase/edge/ambiguous).
4. **Operational** — retry rate, recovery rate, timeout rate, error-class
   distribution, clean-termination rate, and a meltdown signal (entropy over
   the tool-call sequence) in P2.

Hard gates (`assertGate`) are evaluated separately and never hidden by the
weighted aggregate.

## Record / replay

```java
public final class RecordingLLMClient extends UnifiedLLM {
  public RecordingLLMClient(UnifiedLLM delegate, Path jsonl);
  // overrides chat(...) and chat(..., Type); writes {fingerprint, request, response} lines
  public void close();
}

public final class ReplayLLMClient extends UnifiedLLM {
  public ReplayLLMClient(Path jsonl);
  public ReplayLLMClient fallThroughOnMiss(boolean v);
  // fingerprint = SHA-256 over model + outputModel + toolNames + messages + samplingParams
}
```

`UnifiedLLM` is a concrete class with protected constructor and overridable
`chat(...)` (see `FakeLLMClient`), so both clients subclass it. REV/REPLAY
modes are wired in `EvalRunner`. **Reliability scoring runs in LIVE mode** (or
REPLAY with input perturbation) because identical replay runs have zero
variance by construction.

## Cost

```java
public final class ModelPricing {
  public static ModelPricing standard();          // common hosted models; local Ollama = 0
  public ModelPricing with(String model, double promptPer1k, double completionPer1k);
  public Optional<Double> cost(String model, int promptTokens, int completionTokens);
  public static ModelPricing fromEnv();           // NOOA_PRICE_<MODEL>=<inPer1k>/<outPer1k>
}
```

Unknown model -> `pricingKnown=false`, `costUsd=null`; never a silent zero.

## Phase 0 — Foundation (no core changes)

Files: `EvalCase`, `EvalDataset`, `EvalRunner` (LIVE only), `RunRecorder`,
`RunTrace`, `Scorer`, `Score`, `Rubric`, `GoalVerifier` + `PostState` +
`ReflectivePostState` + `EnvironmentSnapshot`, `ModelPricing`, `EvalReport`,
`EvalAssertions`, `scorers/ExactMatchScorer`, `scorers/StructuredFieldScorer`,
`scorers/ContainsScorer`, `scorers/MilestoneScorer` (hook).

- `RunRecorder.attach(agent)` subscribes to `agent.eventManager()` and tallies
  `LLMComplete` tokens, `ToolCallEvent`/`ToolResultEvent`, `ErrorEvent`,
  `BeforeTurn`/`AfterTurn`, `BeforeAgentCall`/`AfterAgentCall`; computes
  `durationMs` from event `timestamp()` pairs; computes cost via
  `ModelPricing`.
- `EvalRunner` builds the agent with `AgentFactory.create`, invokes
  `targetMethod` by reflection with `EvalCase.input`, captures the return value
  and attaches a `RunRecorder`.
- `EvalReport.toJson()` / `toMarkdown()`.

**Acceptance:** run a dataset of `SentimentAgent` and `WeatherAgent` cases,
print `pass@1` and weighted score; `EvalAssertions.assertPass` fails
correctly; `mvn -pl nooa-core test` green.

## Phase 1 — Determinism, behavior, reliability

Files: `llm/RecordingLLMClient`, `llm/ReplayLLMClient`, `Mode`, tool-use and
safety scorers, ops scorers, `CompletionMetrics`, `ReliabilitySpread`,
`EvalBaseline`, `MilestoneScorer` logic; core addition `context/Event.Retry`
+ emission in `ActorRuntime.executeWithConditions`, `PredictStrategy`,
`CodeActStrategy`, `ReflexionStrategy`.

- `EvalRunner` gains `mode`, `trials(int)`, `baseline`, `fallThroughOnMiss`.
- Reliability: compute `pass@1`/`pass@k`/`pass^k`/flakiness over trials;
  robustness delta by tag; operational rates from `Event.Retry`/errors.
- `EvalBaseline.compare` + `EvalAssertions.assertNoRegression`.

**Acceptance:** recording then replaying a live run reproduces identical
outputs; a deliberately flaky `FakeLLMClient` script yields `flakiness > 0`
and `pass^k < pass@1`; baseline regression gate tested.

## Phase 2 — Judgment, horizon, meltdown

Files: `judge/Judge`, `judge/LlmJudge` (opt-in), `judge/FakeJudge`,
`scorers/ContextGroundingScorer` (promoted from the test-only
`PromptEvaluationHarness.groundingScore`), `scorers/LoopTerminationScorer`,
`scorers/StepEfficiencyScorer`, horizon + meltdown signals in `RunRecorder`.

- `LlmJudge` uses a separately configured judge model (never forced to equal
  the agent model), temperature 0, structured `Verdict`, criteria per case,
  and captures judge prompts for audit.
- Horizon: RDC table keyed by `stepCount`. Meltdown: sliding-window entropy
  over executed tool names.

**Acceptance:** `FakeJudge` drives deterministic judge-scorer tests; grounding
scorer equals the promoted legacy computation; horizon buckets render in the
Markdown report.

## Phase 3 — Optional ergonomics

- JUnit 5 extension (test-scope only, or in `examples`) and a `nooa-eval` CLI
  mirroring `examples/run.sh`.
- SQLite run history (reuse `sqlite-jdbc`).
- Consume `Event.PermissionDecision` once added by the permission-hardening
  roadmap (`docs/roadmap.md`, Stage 5) to strengthen safety scorers.

## Test plan (new)

| Test | Verifies |
|---|---|
| `EvalDatasetTest` | JSONL round-trip, tags, milestones |
| `EvalRunnerTest` | reflection dispatch, Live mode, return capture |
| `RunRecorderTest` | token/duration/tool/error aggregation from scripted events |
| `ModelPricingTest` | known/unknown models, env override parsing |
| `GoalVerifierTest` | typed field match, milestone partial credit, reflective state |
| `CompletionMetricsTest` | `pass@1`/`pass@k`/`pass^k` combinatorics; flakiness |
| `RubricTest` | weighting, hard-gate independence |
| `EvalBaselineTest` | save/load, regression delta |
| `RecordingReplayLLMTest` | record then replay identical; miss fails hard |
| `ScorersTest` | each built-in scorer on curated `RunTrace` fixtures |
| `EvalAssertionsTest` | pass/fail/gate behavior |
| `LlmJudgeTest` | `FakeJudge` path; disabled-by-default posture |

## Validation commands

```bash
mvn -q clean install
mvn test
```

Plus a manual smoke run: an eval dataset over `SentimentAgent` (capability,
K=1) and a side-effecting example (reliability, K=3) producing JSON + Markdown
reports.

## Existing assets reused

- Event stream + `EventManager.onEvent` / `forCall` (subscription + scoping)
- `Event.LLMComplete` (tokens), `ToolCallEvent`/`ToolResultEvent` (tool use)
- `PromptRecorder` fingerprint convention (record/replay keying)
- `ContextWindowStats` / `runtime().stats()` (context metrics)
- `AgentDoc.visibleMethods` (tool-surface ground truth)
- `Permissions` (policy compliance)
- `MethodConditions` (postcondition goal contracts)
- `PromptEvaluationHarness.groundingScore` (promote in P2)
- `FakeLLMClient` (deterministic scripts)
