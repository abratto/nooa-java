# CLAD Agent Validation — Session Summary

This document records what was done in this session, why, the results, and the
remaining next steps for the CLAD-agent validation / test-run plan.

## Goal

Prove that the CLAD agent (in `clad-agent`, built on the NOOA SDK in
`nooa-java`) follows the CLAD methodology end-to-end and can deliver the
required artefacts and implementation, and — along the way — make the NOOA
runtime's tool-calling and prompt handling actually work.

## What was done, and why

### 1. Prompt observability (nooa-core)

- Added a `PromptBuilt` runtime event (model name, full message list, tool
  names, output model, sampling params, redacted flag), emitted right before
  `llm.chat`.
- Added opt-in flags `NOOA_LOG_PROMPTS` / `NOOA_LOG_PROMPTS_RAW` (redacted by
  default).
- Added `PromptRecorder` (`ai.nooa.observability`) — a JSONL recorder that
  streams one `PromptBuilt` event per line.
- Preserved `PromptBuilt` through `AgentSnapshot` save/load and `AtifExporter`.

**Why:** we needed to see exactly what the runtime sends to the LLM to tune
the agent/SDK, and to diagnose why the local model was looping.

### 2. Tool-calling fixes (nooa-core)

These were found via the prompt traces and live probes:

- **JShell classloader isolation** — the JShell execution engine runs in an
  isolated classloader and could not see application classes (`SandboxContext`,
  the agent). Fixed with a custom `ExecutionControlProvider` using
  `LocalExecutionControl(agentClassLoader)`.
- **`SandboxContext` used `ThreadLocal`** — the engine runs on a different
  thread, so `setReturnValue`/`consumeReturnValue` never met. Changed to
  `volatile`/concurrent state.
- **`__agent__` typed as base `Agent`** — generated code could not call
  concrete agent methods. Bound it as the concrete type (walking to the
  superclass for ByteBuddy `$Nooa` subclasses).
- **Inline `returnResult(v)` was dropped** — added `explicitReturn` to
  `ExecutionResult` so `executeJava` terminates on an inline `returnResult`.
- **Method arguments now reach the LLM** — `callPlan`/`executeTask` emit the
  `Task` with `userPrompt(true, …)` (args), not just the docstring; and
  arguments are bound as typed REPL variables (`bindVariable`).

### 3. Structured-output fixes (nooa-core)

- `response_format` now uses `json_object` mode by default (strict
  `json_schema` via `-Dnooa.structured.format=json_schema`). Local models
  ignore `json_schema` and emit prose otherwise.
- `PredictStrategy.parseResponse` is tolerant (strips fences/quotes, extracts
  the first balanced JSON object from prose).
- Configurable truncation (`-Dnooa.prompt.maxArgChars`) and output headroom
  (`-Dnooa.predict.maxTokens`).

### 4. CLAD agent alignment (clad-agent)

- **Removed `executeProcess`** (CodeAct "plan" step) — CLAD has no such
  artefact; the loop is now `parseContract → produceOutputs → runVerification →
  advanceGate`, matching the CLAD contract (Inputs/Process/Outputs/Verify/Gate).
- **Nested stage discovery** — `findCurrentStage` uses a `STAGE_ORDER` list
  mirroring `quality-gate/clad_stages.py` (correct red-before-green ordering),
  plus `dir_is_populated`-style "empty output" semantics.
- **`advanceGate` parsing** matches the real `advance.py` output
  (`NEXT STAGE:`, `HUMAN GATE`, exit 0/10/1).
- **Source-tree routing** — `.java` files go to the profile source root,
  test files (`*Test.java`, `*StepDefinitions.java`) to the test root, and
  `.feature` files to `output/`.
- **Derived-output resolution** — stages with variable outputs (`02_concepts`,
  `03a`, `03b`, `04b`, `04c`, `04d/04e`) derive filenames from the
  responsibility map (concepts/actions), chain table (outcomes), syncs, and the
  feature slug.
- **Script-generated outputs** — `Generated via:` blocks (e.g.
  `verify_concept_matrix.py`) are run deterministically and excluded from the
  model's draft.
- **Canonical-token injection** — concepts/actions/outcomes/sync names are
  extracted and held in a NOOA context block, auto-rendered into downstream
  prompts so the model copies tokens verbatim instead of drifting.
- **Build/test loop** — reads `_config/build-and-test.md`, runs the build only
  for source-output stages, feeds build errors back into the retry, with a
  configurable retry count (`-Dclad.maxAttempts`).
- **Reference-pattern injection** — for implementation stages, injects the one
  relevant reference source file so the model copies the correct API.
- **Minimal context** — `eventManager().clear()` per stage and between
  parse/draft, plus trimmed reference injection.
- Misc fixes: case-insensitive verification, bootstrap (`Web`) exclusion,
  extra-file filtering, code-fence skipping in `parseContractDeterministically`,
  `resolveStagesRoot` null-safety, explicit JSON output contract in the system
  prompt, and gate-stop summary handling.

## Results

### Test suites (all green)

- `nooa-core`: **170 tests**.
- `clad-agent`: **52 tests** (includes end-to-end tests against a clone of the
  real CLAD repo, e.g. `agentStopsAtHumanGateOneUsingRealAdvanceScript`,
  `extractsCanonicalTokensIntoContext`, `generatedScriptOutputsAreProducedAndExcludedFromModelDraft`).

### End-to-end run — design stages (proven)

Using `qwen3.8:27b-mlx` (Ollama) against a cloned CLAD repo
(`/Users/alanp/Documents/GitHub/clad-clone`), the agent walked a fresh
`UC-01-greeting` feature through:

`01 → 01a → 01b (Gate 1) → 02 → 03 → 03a → 03b (Gate 2) → 04a → 04b`

with every real gate check passing, including `scenario_coverage`,
`sync_matrix`, `sync_cycle_graph`, `sync_overlap`, `data_model`,
`spec_parity`, `outcome_alignment`, and — critically — `action_chain` (the
strictest cross-stage token-consistency check, which initially blocked).

It produced real Cockburn-style artefacts (Fully-Dressed use case,
responsibility map, chain table, concept spec, syncs, data model, spec), and
correctly stopped at both human gates.

### Implementation stages (the wall)

`04c+` is where the run stalls. The framework now gives the model its best
chance (minimal context, token injection, build-error feedback, reference
patterns, output headroom), but `qwen3.8:27b-mlx` is too slow and too
inconsistent at multi-file Java: it produced the files on some runs and fell
back to stubs on others, and its Java hallucinated API names
(`@CucumberConfiguration`, `CucumberEngineRegistrar`, `org.junit.platform.runner`).
The build-error feedback *did* make it self-correct (`@CucumberConfiguration`
→ `@IncludeEngines`/`@ConfigurationParameter`), confirming the mechanism works,
but the model remains the bottleneck.

## Round 2 — making the coding stages actually run

A second pass focused on the coding phase (04c+). Three non-model root causes
were found and fixed:

1. **The prompt was truncated to 800 chars.** `ActorRuntime` capped each
   `@Generate` argument at `nooa.prompt.maxArgChars` (default 800), so the
   enriched stage context (CONTEXT.md + input artefacts + reference) was cut
   before the model saw it. Raised the default to `262_144` to match the local
   model's 256k context window.
2. **One prompt had to emit every file in the stage.** `produceOutputs` now
   splits source-output stages into one `draftOutputs` call per file, and
   injects a reference pattern matched to the specific file being drafted.
   Design (markdown-only) stages keep the single batched call.
3. **Java files were routed flat, without packages.** `resolveOutputTarget`
   appended only the basename to the source root, so `GreetingConcept.java`
   landed at `src/main/java/GreetingConcept.java` and `mvn test` on the
   reference profile could not compile. The layout now reads
   `APP_PACKAGE_ROOT`, `deriveOutputs` emits package-qualified paths
   (concept → `<pkg>.concepts.<lower>`, sync → `<pkg>.syncs`, step defs →
   `<pkg>.steps`), and names are derived deterministically from the
   responsibility map / syncs rather than invented by the model (killing the
   `ConceptOwned actionsTest.java` class of hallucination). The red/green
   split was also corrected: red stages emit the tests
   (`<Concept><Action>Test`, `<Sync>Test`), green stages emit the
   implementation (`<Name>Concept`, `<Sync>`).

Results: `nooa-core` 170 tests green; `clad-agent` 53 tests green (added
`derivesPackageQualifiedCodingOutputs`).

## Round 3 — reasoning budget (the real slowness)

Profiling `qwen3.8:27b-mlx` showed the default behaviour is a thinking model
that spends its whole token budget on a hidden reasoning trace before emitting
content — with `max_tokens`-capped calls returning empty `content`. Measured on
the same prompt via the OpenAI-compatible endpoint:

| reasoning_effort | time | reasoning chars | content chars |
|---|---|---|---|
| default | 30.2s | 3 774 | **0** |
| `none` | 5.5s | 0 | 702 |
| `low` | 26.7s | 2 926 | 767 |
| `medium` | 26.5s | 3 287 | 487 |

Fix:

- `UnifiedLLM` now forwards `reasoning_effort` (and `think`) as top-level
  request fields; `PredictConfig` gained `reasoningEffort`, and `PredictStrategy`
  resolves it from config → per-agent override → `-Dnooa.predict.reasoningEffort`.
- `Agent` gained `samplingOverride(key, value)` (a hidden, non-rendered map)
  so an agent can set per-call sampling without leaking into the prompt.
- `CladAgent` sets `reasoning_effort` per stage: **`none`** for the coding
  stages (`04c`/`04d`/`04e`), **`medium`** for the design stages.

Results: `nooa-core` 170 tests green; `clad-agent` 54 tests green (added
`codingStageSetsReasoningEffortToNone`).

## Round 4 — fresh app + deterministic step-def derivation

Two more changes that moved `04c` past its gate:

1. **Fresh app (`app/backend`).** The greeting feature was being written *into*
   the login reference profile, so its step definitions collided with login's
   (`Duplicate step definitions`). Following the reference profile's
   "downstream-template rule", a fresh app was scaffolded at `app/backend/` —
   the engine + bootstrap, minus all login concepts/syncs/api/step-defs — and
   `_config/package-and-layout.md`, `_config/build-and-test.md`, and
   `clad.properties` (`test.source.root`, `sync.impl.dir`, `concept.impl.dir`,
   `test.command`) were repointed at it.
2. **Deterministic step-def derivation.** Applying the principle "do
   deterministic CLAD activities in code, only delegate genuine tasks to the
   model", `*StepDefinitions.java` is now generated from the produced
   `.feature` + chain table instead of being drafted by the model: one
   `@Given/@When/@Then` method per Gherkin step with correct Cucumber
   parameterization (`"..."` → `{string}`, bare ints → `{int}`, Scenario-Outline
   placeholders typed from the `Examples` table), non-trivial bodies that
   reference the chain-table actions, and inline JSON envelopes rewritten as
   Cucumber doc-strings so the parity verifier can match them. The model is now
   used only for `parseContract`, the `.feature`, and `CucumberTest`.

`./clad advance` for `04c` now passes `step_definition_parity` and
`step_definition_derivation` and stops correctly at **Human Gate 3**.

Results: `nooa-core` 172 tests green; `clad-agent` 55 tests green (added
`generatesStepDefinitionsDeterministicallyFromFeature`).

## Next steps

1. **(Deferred) deterministic sync-name derivation** — derive `03_syncs` names
   from the chain table via the compressed grammar (option "b"), so sync names
   are ground-truth rather than model-generated.
2. **Prove 04d/04e** — approve Gate 3 and run the concept/sync TDD stages (pure
   Java, `reasoning_effort=none`); extend deterministic derivation to concept
   tests / sync impls where the same principle applies.

