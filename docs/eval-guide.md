# Evaluating NOOA Agents

The `ai.nooa.eval` package adds a first-class evaluation layer for agents built
on `nooa-core`. It is event-sourced: it reads the same event stream, traces, and
prompt capture the runtime already produces, so you evaluate the agents you
built rather than a re-implementation.

This guide covers what is included, what each piece measures, how to define
datasets, how to run evals, and how to analyze the results. For the staged
design history and roadmap status, see [roadmaps/eval.md](roadmaps/eval.md).

## Contents

1. [Concepts](#concepts)
2. [Quick start](#quick-start)
3. [Datasets](#datasets)
4. [Running an evaluation](#running-an-evaluation)
5. [Scorers](#scorers)
6. [Reliability metrics](#reliability-metrics)
7. [Goal verification](#goal-verification)
8. [Record and replay](#record-and-replay)
9. [Cost](#cost)
10. [Reports, baselines, and history](#reports-baselines-and-history)
11. [Gating a build](#gating-a-build)
12. [Command-line runner](#command-line-runner)
13. [JUnit 5 extension](#junit-5-extension)
14. [LLM-as-judge](#llm-as-judge)
15. [Instrumenting an agent for evals](#instrumenting-an-agent-for-evals)
16. [Analyzing results](#analyzing-results)
17. [Limitations](#limitations)

## Concepts

| Type | Role |
|---|---|
| `EvalCase` | One test case: target method, inputs, expected result or goal, tags, metadata. |
| `EvalDataset` | Ordered collection of cases; JSONL load/save. |
| `EvalRunner` | Runs a dataset against an agent class, in LIVE / RECORD / REPLAY mode, for N trials, and produces an `EvalReport`. |
| `RunRecorder` / `RunTrace` | Captures one invocation's signals (output, latency, tokens, cost, tool calls, retries, errors, permission decisions, prompt text, tool entropy). |
| `Scorer` / `Score` | A measurement over one run. `Score` carries a value in `[0,1]`, pass/fail, a detail string, and a structured payload. |
| `Rubric` | A weighted set of scorers that produces one aggregate score per case. |
| `GoalVerifier` | Decides whether the agent reached the goal, from the outcome and post-run state. |
| `CompletionMetrics` | `pass@1`, `pass@k`, `pass^k`, and flakiness across trials. |
| `EvalReport` | Per-case and aggregate results; JSON and Markdown output. |
| `ModelPricing` | Token-to-cost conversion. |

The runner creates a fresh agent instance per trial, so cases do not share
mutable agent state.

## Quick start

```java
var llm = UnifiedLLM.create(UnifiedLLM.openAI(System.getenv("OPENAI_API_KEY"), "gpt-4o").build());

EvalDataset dataset = EvalDataset.of("sentiment", List.of(
    EvalCase.of("pos", "analyze", Map.of("text", "I love it"), /* expected */ null),
    EvalCase.of("neg", "analyze", Map.of("text", "I hate it"), null)));

EvalReport report = EvalRunner.builder(SentimentAgent.class, llm)
    .trials(3)
    .build()
    .run(dataset);

System.out.println(report.toMarkdown());
```

`input` values are passed to the target method **in map iteration order**, which
must match the method's parameter order. Use a `LinkedHashMap` (or `Map.of`,
whose order is stable for a given set of keys) for multi-argument methods.

## Datasets

Cases are loaded from and saved to JSONL (one JSON object per line):

```json
{"id":"c1","targetMethod":"draft","input":{"report":"..."},"expected":null,
 "expectedGoal":{"status":"published"},
 "milestones":[{"name":"has-title","weight":1.0,"expected":{"title":true}}],
 "tags":["canonical"],"metadata":{"maxSteps":8}}
```

`EvalCase` fields:

| Field | Purpose |
|---|---|
| `id` | Stable case identifier. |
| `targetMethod` | Agent method to invoke. |
| `input` | Argument values in parameter order. |
| `expected` | Expected return value, or `null`. Records/strings/numbers supported. |
| `expectedGoal` | Post-run field/value goal state for goal verification, or empty. |
| `milestones` | Weighted subgoals for partial credit. |
| `tags` | Dataset facets, e.g. `canonical`, `paraphrase`, `edge`, `ambiguous`. |
| `metadata` | Scorer-specific thresholds and options (see [Scorers](#scorers)). |

```java
dataset.save(Path.of("src/test/resources/eval/sentiment.jsonl"));
EvalDataset loaded = EvalDataset.load(Path.of("src/test/resources/eval/sentiment.jsonl"));
```

## Running an evaluation

```java
EvalReport report = EvalRunner.builder(MyAgent.class, llm)
    .extraArgs(someDependency)          // args after the llm, for the constructor
    .trials(3)                          // repetitions per case (reliability)
    .mode(Mode.LIVE)                    // LIVE | RECORD | REPLAY
    .rubric(Rubric.defaults())          // or a custom Rubric
    .goalVerifier(new DefaultGoalVerifier())
    .environment(() -> Map.of("published", publishedFlag))
    .recordingPath(Path.of("recording.jsonl"))
    .fallThroughOnMiss(false)           // REPLAY: fail hard by default
    .build()
    .run(dataset);
```

Use K=1 for capability runs and K≥3 for reliability suites (the metrics that
matter most need multiple trials).

## Scorers

Scorers run on the captured `RunTrace`. A scorer that does not apply to a case
returns `Score.notApplicable(...)` and is excluded from aggregation, so the same
rubric works across mixed datasets.

| Scorer | Dimension | Measures | Config (case metadata / fields) |
|---|---|---|---|
| `ExactMatch` | Correctness | Output equals `expected` (trimmed strings, numeric-aware) | `expected` |
| `StructuredField` | Correctness | Fraction of record components matching `expected` | `expected` (records) |
| `Contains` | Correctness | Output contains the expected substring | `expected` (String) |
| `Milestone` | Correctness (partial) | Weighted fraction of milestones met | `milestones` on the case |
| `RequiredTools` | Tool use | All named tools were called | `requiredTools: [..]` |
| `ForbiddenTools` | Tool use | None of the forbidden tools were called | `forbiddenTools: [..]` |
| `ToolEfficiency` | Tool use | Tool calls within budget | `maxToolCalls: <n>` |
| `ToolRecovery` | Robustness | Produced output after a tool error | — |
| `PolicyCompliance` | Safety | No forbidden error classes recorded | `forbiddenErrorClasses: [..]` |
| `PermissionCompliance` | Safety | No sandbox `DENY` permission decision occurred | — |
| `SecretLeak` | Safety | Output contains no likely secret | — |
| `LatencyBudget` | Ops | Wall-clock within budget | `maxDurationMs: <n>` |
| `TokenBudget` | Ops | Total tokens within budget | `maxTotalTokens: <n>` |
| `CostBudget` | Ops | Cost within budget | `maxCostUsd: <n>` |
| `LoopTermination` | Planning | Run finished cleanly within the step budget | `maxSteps: <n>` (optional) |
| `StepEfficiency` | Planning | Steps within budget | `maxSteps: <n>` |
| `Meltdown` | Planning | Tool-call entropy below threshold | `maxToolEntropy: <n>` |
| `ContextGrounding` | Prompt/context | Input tokens recalled in the captured prompt | requires `NOOA_LOG_PROMPTS` |
| `Judge` | Reasoning/quality | Score from a `Judge` (see [LLM-as-judge](#llm-as-judge)) | `judgeCriteria: [..]` |

`Rubric.defaults()` weights these into one aggregate; build a custom `Rubric`
with `Rubric.of(scorer...)` or explicit `WeightedScorer`s when you need
different priorities.

## Reliability metrics

`EvalReport.aggregate().completion()` reports the standard reliability envelope
computed across trials:

| Metric | Meaning |
|---|---|
| `pass@1` | Mean per-trial success (capability). |
| `pass@k` | At least one of k trials passed (optimistic bound). |
| `pass^k` | All k trials passed (consistency; the reliability metric for side-effecting or customer-facing agents). |
| `flakiness` | Fraction of cases whose outcome switched across trials. |

The Markdown report also includes a **reliability-by-horizon** table that
buckets `pass@1`/`pass^k` by step count, exposing decay as tasks get longer.

The `toolEntropy` signal (Shannon entropy over the tool-call sequence) feeds the
`Meltdown` scorer and flags repetitive, unfocused loops.

## Goal verification

`GoalVerifier` inspects the outcome **and** the post-run state:

1. If `expectedGoal` is set, every field must match `PostState.fields()`; the
   completion fraction is the matched proportion.
2. Otherwise, if `expected` is set, the returned value must equal it.
3. Otherwise the goal is unscored (neutral success).

`PostState` is backed by `ReflectivePostState` (agent instance fields, static /
structured context blocks, and a `MemoryStore` when present). Provide an
`EnvironmentSnapshot` to merge external state (filesystem flags, database rows):

```java
.environment(() -> Map.of("db_row_count", queryCount()))
```

Goal verification is what makes `pass^k` meaningful for side-effecting agents:
it checks the world, not just the text.

## Record and replay

Deterministic, network-free CI runs:

```java
// 1. Record once against a live model.
EvalRunner.builder(MyAgent.class, liveLlm)
    .mode(Mode.RECORD)
    .recordingPath(Path.of("recording.jsonl"))
    .build().run(dataset);

// 2. Replay in CI with no live calls.
EvalRunner.builder(MyAgent.class, unusedLlm)
    .mode(Mode.REPLAY)
    .recordingPath(Path.of("recording.jsonl"))
    .build().run(dataset);
```

`RecordingLLMClient` stores request/response pairs keyed by a prompt
fingerprint; `ReplayLLMClient` matches by fingerprint and **fails hard on a
miss** so replay cannot silently diverge. Set `fallThroughOnMiss(true)` to
delegate misses to the live client.

Reliability scoring needs a stochastic source: run reliability suites in
`LIVE` (or `REPLAY` with input perturbation). Identical replays have zero
variance by construction.

## Cost

`ModelPricing` converts tokens to USD. It ships a small default table, supports
programmatic overrides, and reads `NOOA_PRICE_<MODEL>=<promptPer1k>/<completionPer1k>`
from the environment:

```java
ModelPricing pricing = ModelPricing.fromEnv().with("my-local-model", 0.0, 0.0);
EvalRunner.builder(MyAgent.class, llm).pricing(pricing).build().run(dataset);
```

Unknown models report `pricingKnown=false` and `costUsd=null`; `CostBudget` is
not applicable rather than silently free.

## Reports, baselines, and history

```java
String json = report.toJson();
String markdown = report.toMarkdown();

// Regression baseline
EvalBaseline.from(report).save(Path.of("baseline.json"));
EvalBaseline baseline = EvalBaseline.load(Path.of("baseline.json"));
boolean worse = baseline.regressed(anotherReport, 0.02);
Map<String, Double> deltas = baseline.deltas(anotherReport);

// Run history (SQLite)
try (var history = new EvalHistoryStore("eval-history.db")) {
    long id = history.record(report);
    List<EvalHistoryStore.RunSummary> recent = history.recent("sentiment", 10);
    EvalReport loaded = history.load(id).orElseThrow();
}
```

## Gating a build

`EvalAssertions` is dependency-free (throws `AssertionError`), so it works in
JUnit, TestNG, a `main`, or a Gradle task:

```java
EvalAssertions.assertPass(report, 0.85);                 // weighted mean
EvalAssertions.assertGate(report, "PermissionCompliance", 1.0); // hard gate
EvalAssertions.assertFullyReliable(report);              // pass^k == 1
EvalAssertions.assertNoRegression(current, baseline, 0.02);
```

Hard gates are evaluated independently and are never hidden by the weighted
aggregate.

## Command-line runner

```bash
java -cp nooa-core.jar:... ai.nooa.eval.cli.EvalCli \
  --agent ai.nooa.examples.SentimentAgent \
  --dataset sentiment.jsonl \
  --trials 3 \
  --report report.json \
  --markdown report.md \
  --history eval-history.db \
  --min-weighted 0.85 \
  --gate PermissionCompliance=1.0
```

| Option | Meaning |
|---|---|
| `--agent <fqcn>` | Agent class (required). |
| `--dataset <jsonl>` | Dataset file (required). |
| `--trials N` | Trials per case (default 1). |
| `--mode LIVE\|RECORD\|REPLAY` | Execution mode (default LIVE). |
| `--recording <path>` | Recording file for RECORD/REPLAY. |
| `--report <json>` / `--markdown <md>` | Write reports. |
| `--history <db>` | Record the run in the SQLite history. |
| `--min-weighted X` | Fail (exit 1) below the weighted mean. |
| `--gate scorer=min` | Hard gate; repeatable. |

Exit codes: `0` success, `1` gate/threshold failure, `2` usage error. The model
comes from `NOOA_BASE_URL`/`NOOA_API_KEY`/`NOOA_MODEL` (or the `OPENAI_*`
equivalents), falling back to local Ollama.

## JUnit 5 extension

A reference extension lives in the `examples` module test scope
(`ai.nooa.examples.eval`):

```java
@Test
@ExtendWith(EvalExtension.class)
@Eval(agent = EchoAgent.class,
      dataset = "src/test/resources/eval/smoke.jsonl",
      minWeighted = 0.8, gate = "ExactMatch", gateMin = 1.0)
void meetsBar(EvalReport report) {
    assertThat(report.cases()).hasSize(1);
}
```

`EvalExtension.useLlm(...)` injects a scripted client for deterministic tests;
otherwise the model is resolved from the environment.

## LLM-as-judge

`Judge` is the SPI for subjective dimensions; `LlmJudge` is an opt-in reference
implementation that only exists when you supply a judge model (never assumed to
be the agent's model). It uses temperature 0 and asks for a JSON verdict.

```java
Judge judge = new LlmJudge(judgeLlm);          // separate model recommended
Rubric rubric = Rubric.of(new JudgeScorer(judge));
```

Cases supply `judgeCriteria` in metadata. Judge scores are instrument readings,
not ground truth — calibrate against human review before using them for gates.
`FakeJudge` provides deterministic verdicts in tests.

## Instrumenting an agent for evals

Evals work best when the agent already follows the NOOA patterns:

- **Target typed methods.** `@Generate` methods that return records give you
  field-level correctness and auto-generated goal checks. Deterministic methods
  are evaluable too (they simply produce no LLM signals).
- **Put the contract in the name and prompt.** A descriptive method name and a
  precise `@Generate(prompt = ...)` make both the run and the report readable.
- **Express constraints as metadata.** Tool budgets, step limits, latency/token/
  cost budgets, forbidden tools/error classes, and judge criteria live in
  `EvalCase.metadata`; the built-in scorers read them.
- **Use `MethodConditions` for hard contracts.** Postconditions feed goal
  verification and postcondition retries surface as `Event.Retry`.
- **Model side effects as `expectedGoal` or an `EnvironmentSnapshot`** so
  completion reliability measures the world, not the narration.
- **Keep tool surfaces minimal.** `RequiredTools`/`ForbiddenTools` compare
  against the tools actually called; a small, purposeful tool surface is easier
  to score and safer under permissions.
- **Enable prompt capture when scoring context.** `ContextGrounding` needs the
  captured prompt (`NOOA_LOG_PROMPTS=true`, or a `PromptRecorder`).

Worked, tested examples of these patterns live in the test suite:
`nooa-core/.../EvalEndToEndTest` (real CodeAct tool use, blocked-cell permission
audit, Predict retries, and prompt grounding driven by a scripted client) and
`examples/.../RealAgentEvalTest` (evaluating the real `SentimentAgent`).

## Analyzing results

A practical loop:

1. **Capability first.** Run K=1, read correctness scores and the weighted mean.
2. **Then reliability.** Run K=3, compare `pass@1` vs `pass^k`; investigate
   flaky cases and the horizon table.
3. **Slice by tag.** Compare `canonical` vs `paraphrase`/`edge` success to spot
   prompt sensitivity and robustness gaps.
4. **Attribute failures.** Use `RunTrace.signals()` (tool sequence, permission
   decisions, errors), ATIF trajectories, and captured prompts to root-cause.
5. **Regress against a baseline.** Persist an `EvalBaseline` and gate CI with
   `assertNoRegression`.
6. **Track over time.** Record runs in `EvalHistoryStore` and watch
   `weightedMean`, `pass@1`, and `pass^k` trends per dataset.

## Limitations

- The runner creates a fresh agent per trial; this favors correctness over
  speed for large datasets.
- Record/replay makes repeated runs identical, so reliability variance must be
  measured in LIVE mode (or with perturbed inputs).
- `ContextGrounding` requires prompt capture to be enabled.
- `LlmJudge` is nondeterministic and biased; treat it as an instrument, not a
  gate, until calibrated.
- The sandbox permission gate is a strong static/allow-list control, not a
  hostile-code isolation boundary; see [roadmaps/permission-hardening.md](roadmaps/permission-hardening.md) Stage 4.
