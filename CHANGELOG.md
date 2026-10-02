# Changelog

## v0.9.0 (2026-10-01)

### Added
- `examples`: `ai.nooa.examples.incident` — an incident-response agent whose workflow is a compile-time-checked state machine (sealed `IncidentState`/`IncidentEvent`, a pure `(state, event) -> Transition` function with guards), with deterministic telemetry helpers, CodeAct investigation, typed `Predict` payloads, a human `ApprovalGate`, and canned scenarios
- `examples`: `ai.nooa.examples.release` — a payload-free release-gate agent demonstrating the enum-with-abstract-methods state-machine idiom
- Tests: pure transition coverage, scripted agent end-to-end (approved, rejected, critical, illegal-transition), and an `EvalRunner` goal-state evaluation over the incident workflow
- `docs/concepts.md` now contrasts the enum and sealed-types state-machine idioms

## v0.8.5 (2026-10-01)

### Added
- Scoped PIT mutation-testing gate (`scripts/mutation-score.sh`, PIT 1.30.0 + junit5 plugin 1.2.3) over `Permissions`, `CodePermissionAnalyzer`, and `ai.nooa.eval.*`, with a configurable threshold (baseline 56.9%, regression floor 55%). Not lifecycle-bound, so `mvn test` is unaffected.

## v0.8.4 (2026-10-01)

### Added
- End-to-end evaluation coverage against real agents: `EvalEndToEndTest` exercises CodeAct tool use, a blocked cell producing a real `Event.PermissionDecision`, Predict retries, and prompt grounding; `RealAgentEvalTest` evaluates the real `SentimentAgent` through `EvalRunner`

### Fixed
- `StructuredFieldScorer` now reads record component accessors with `setAccessible`, so outputs from package-private record types score correctly (surfaced by the real-agent eval)

### Changed
- Documented prompt-capture redaction of sensitive-looking argument keys (`token`, `secret`, `password`, `apiKey`) and linked the worked eval examples from `docs/eval-guide.md`

## v0.8.3 (2026-10-01)

### Changed
- README is now a concise landing page (pitch, install, Quick Start, run examples, documentation hub, Why Java, license)
- New topic documentation: `docs/concepts.md`, `docs/architecture.md`, `docs/security.md`, `docs/observability.md`, `docs/integrations.md`, `docs/comparison.md`
- Roadmaps moved to `docs/roadmaps/` (`permission-hardening.md`, `eval.md`) with all references updated
- The SDK maintenance contract moved to `CONTRIBUTING.md`; the Testing and Visibility notes moved into `docs/agent-building-guide.md` and `docs/package-reference.md`

## v0.8.2 (2026-10-01)

### Fixed
- README examples now use the current `@Generate(prompt = "...")` API instead of presenting a Javadoc comment as the prompt, and the Quick Start shows the class-level `@SystemPrompt` persona and required imports
- Corrected the prompt explanation: the `@Generate` prompt is the instruction, while the method name, arguments, and return type supply the data and the structured-output contract

## v0.8.1 (2026-10-01)

### Added
- `PermissionComplianceScorer` and the `permissionDecisions` `RunTrace` signal: evaluation safety scoring over the sandbox's `Event.PermissionDecision` audit trail (fails on any denied resource access)
- `docs/eval-guide.md`: user guide covering datasets, the runner, each scorer and what it measures, reliability metrics, goal verification, record/replay, cost, reports/baselines/history, the CLI, and the JUnit 5 extension

### Changed
- README and the package reference now link the evaluation guide

## v0.8.0 (2026-10-01)

### Added
- `ai.nooa.eval` evaluation layer: `EvalCase`/`EvalDataset` (JSONL), `EvalRunner` (LIVE/RECORD/REPLAY, trials, goal verification), `RunRecorder`/`RunTrace`, `Scorer`/`Score` with applicability, `Rubric`, `GoalVerifier`/`PostState`, `ModelPricing` with `NOOA_PRICE_*` overrides, `EvalReport` (JSON + Markdown), `EvalAssertions`, `EvalBaseline`, and `EvalHistoryStore` (SQLite run history)
- Correctness, tool-use, safety, and ops scorers; completion reliability metrics (`pass@1`, `pass@k`, `pass^k`, flakiness) and horizon (step-count) buckets
- `Judge` SPI with an opt-in `LlmJudge`, `FakeJudge`, and `JudgeScorer`; context-grounding, loop-termination, step-efficiency, and meltdown scorers
- `RecordingLLMClient` / `ReplayLLMClient` for deterministic record/replay evaluation
- `nooa-eval` CLI (`ai.nooa.eval.cli.EvalCli` + `LlmFactory`) and a reference JUnit 5 extension (`EvalExtension` / `@Eval`)
- `Event.Retry` (strategy/condition retries) and `Event.PermissionDecision` (sandbox audit), the latter exported through ATIF and `AgentSnapshot`
- `CodePermissionAnalyzer`: JavaParser AST-based sandbox permission gate
- New runtime dependency: `com.github.javaparser:javaparser-core` 3.28.0

### Changed
- `Permissions` resolves symlinks in file checks so a link inside an allowed root cannot escape it, and matches the most-specific rule regardless of insertion order
- `ShellTools` fails closed when a command is `ASK` and no `PermissionCallback` is registered
- `JShellSandbox` bounds captured stdout/stderr at 1 MiB per stream; blocked-API detection is now AST-based with a contains-scan fallback
- `PredictStrategy` reuses a single `private static final ObjectMapper JSON` instead of constructing one per response parse
- `MemoryStore.close()` awaits reflection-executor termination (3s) after `shutdownNow()`, restoring the interrupt flag and logging on timeout
- README and `docs/sdk-versioning.md` document the eval layer and sandbox permission guarantee levels

### Fixed
- `SseTransport.connect()` closes the HTTP response body stream on a non-200 handshake, fixing a connection leak
- `SseTransport.close()` closes the underlying SSE input stream, which is what unblocks the reader thread parked in `BufferedReader.readLine()`; the thread interrupt is retained as a secondary measure
- Malformed JSON-RPC payloads are logged (debug) in `SseTransport` and `StdioTransport` instead of being silently swallowed
- Documented the null-return contract of `Agent.findSystemPrompt()` and the timeout/interrupt behavior of `SseTransport.receive()`

## v0.6.0 (2026-09-13)

### Added
- `examples/run.sh` runner with `--list`, single-example, and `--all` modes, per-example timeouts, and a pass/fail summary; `exec-maven-plugin` is now declared in the examples POM
- Per-example reasoning-effort tuning via `ExampleLLM.tune(agent, effort, maxTokens)` and the `NOOA_REASONING_EFFORT` / `-Dnooa.reasoningEffort` overrides
- Local Ollama examples default to the OpenAI-compatible endpoint (`http://localhost:11434/v1`) so tool calling and structured output match hosted providers
- Build guard that fails with an actionable message when Eclipse JDT (VS Code) leaves `Unresolved compilation problem` class stubs in `target/classes`
- Regression tests for sampling-override merging, structured-output gating, and CodeAct self-reentrancy (226 core tests total)

### Changed
- `ActorRuntime` merges `Agent.samplingOverrides()` into every LLM request, so per-agent sampling (e.g. `reasoning_effort`) now applies to all strategies, not only `PredictStrategy`
- `UnifiedLLM` requests structured output only for JSON-shaped targets without tools; plain `String`/primitive targets and tool-calling requests no longer force `response_format`/`format:"json"`
- `JShellSandbox` evaluates code cells statement-by-statement via `SourceCodeAnalysis` (REPL semantics) instead of one `eval` per cell
- `JShellSandbox` registers the agent classloader classpath with JShell so snippets compile under embedding classloaders such as Maven `exec:java` or application servers
- `AgentFactory` loads instrumented subclasses with `ClassLoadingStrategy.Default.INJECTION` (same classloader), fixing cross-loader type access under `exec:java`
- `CodeActStrategy` system prompt clarifies that agent methods are not tools and adds `var`-based typing guidance for helper results
- Examples documentation rewritten per class and in `examples/README.md` (feature mapping, run commands, visibility rules, safety notes)

### Fixed
- Generated code re-entering its own `@Generate` method now fails fast with a typed `ValidationError` instead of recursing through the strategy loop
- `returnResult` values that do not match the declared return type raise a correctable `GenerationError` naming the expected type and record fields; JSON-encoded string values are parsed
- Multi-statement sandbox cells no longer silently drop statements after a leading variable declaration (e.g. `int x = 5; returnResult(x + 1);` now returns `6`)
- `returnResult` without a value is a correctable error for non-void methods instead of returning `null`
- `PredictStrategy` rejects records with null components, triggering the retry loop with a diagnostic instead of returning half-populated results
- JShell `EvalException` messages are unwrapped so sandbox errors reach the model; blocked-API errors name the denied API
- `run.sh`: the timeout watchdog no longer inherits the caller's stdout, so piping runner output (for example into `tail`) no longer hangs after the script exits
- Examples: model-facing helpers are `public` (only public methods are model-callable), `ShellToolsDemo` grants explicit `Permissions`, `McpDemo` writes the file it reads, `LegalIntakeDemo` exercises both extraction paths

### Validation
- `mvn test` passes across the reactor (226 nooa-core, 2 examples)
- `./examples/run.sh --all` passes 14/14 against local Ollama (`qwen3.8:27b-mlx`)

## v0.5.0 (2026-09-10)

### Added
- Focused runnable examples for strategies, tracing, summarization, snapshots, shell tools, memory, and MCP
- Deterministic example smoke tests covering factory instrumentation and Java-owned state
- Prompt engineering guide documenting prompt assembly, method metadata, context blocks, events, and strategy-specific instructions

### Changed
- Refactored aggregate example launchers into clear, independently runnable demos while preserving compatibility entry points
- Corrected README guidance for `@Hidden`, runtime prompts, AgentDoc visibility, tracing, and typed memory relationships
- Documented the `0.5.0` Maven coordinates for downstream consumers

### Validation
- `mvn test` passes across the reactor
- `git diff --check` passes

## v0.4.0 (2026-09-10)

### Added
- Interactive turn protocol with typed turn results and queue-preserving dispatch
- Durable session snapshots for events and structured context values
- Skill activation registry, operation-level middleware hooks, and typed validation and restricted-code errors
- Structured context rendering with bounded output
- Pluggable `SandboxExecutor` SPI for host-provided process, container, VM, or remote isolation backends

### Documentation
- Added an agent-building guide covering service, interactive, worker, skill, middleware, context, and testing patterns
- Added a package reference with strategy/configuration guidance and explicit sandbox audit limits
- Added sandbox regression coverage for execution lifecycle, permissions, bindings, returns, and shared context cleanup

### Changed
- Documented parity with the original NOOA framework is limited to the Java-supported capability subset; Java remains synchronous and JVM-native
- Removed the unused Micronaut integration and legacy CLAD runtime modules from the SDK reactor

### Validation
- `nooa-core` and the Maven reactor pass the Java test suite
- The downstream `clad-agent` consumer builds and tests against the installed `nooa-core` release artifact

## v0.3.0 (2026-08-14)

### Added
- Provider-agnostic `StructuredOutputHelper` for validating typed JSON responses before returning them to callers
- `FakeLLMClient` test harness for isolated structured-output verification without external API keys
- Example agents split into dedicated source files and updated to use env-aware `ExampleLLM` provider selection

### Changed
- Improved local-model compatibility by allowing text fallback in `CodeActStrategy` when a model returns plain text instead of tool calls
- Standardized example setup to prefer `NOOA_*` environment variables with OpenAI-compatible local endpoints and Ollama fallback
- Tightened example prompts to produce more reliable, constrained structured outputs

### Fixed
- Fixed Java access issues for generated subclasses returning record-based outputs
- Resolved example-only compilation drift caused by nested record declarations and source layout changes

## v0.2.0 (2026-08-12)

### Changed
- Upgraded Java runtime target from 21 to **25 LTS**
- Upgraded byte-buddy 1.14.18 → 1.18.11 (Java 25 class file support)

### Security
- jackson-databind 2.18.0 → 2.18.9 — fixes 7 CVEs including HIGH-severity polymorphic type-validator bypass (CVE-2026-54512, CVE-2026-54513) and SSRF/`@JsonView` bypass issues
- assertj-core 3.26.3 → 3.27.7 — fixes CVE-2026-24400 (HIGH: XXE in `isXmlEqualTo`)

## v0.1.0 (2026-07-01)

First public release. Independent Java port of NVIDIA's Object-Oriented Agents
framework, targeting Java 21+ with virtual threads, JShell sandbox, and a
complete predicate engine for CLAD methodology.

### Agent SDK (`nooa-core`)

- **Agent model** — single-class agents with `@Generate`, `@Strategy`, `@Hidden`, `@SystemPrompt` annotations
- **ByteBuddy instrumentation** — `AgentFactory.create()` intercepts `@Generate` methods and routes through the LLM runtime
- **Virtual thread execution** — synchronous API; LLM calls block on virtual threads, no CompletableFuture needed
- **CodeActStrategy** — Jupyter-style REPL with `executeJava(code)` + `returnResult(value)` tools running inside JShell
- **PredictStrategy** — single-shot structured output validated against Java Records
- **ReflexionStrategy** — generate → critique → improve loop
- **ActorRuntime** — context building, generation locks, re-entrant nested calls, event lifecycle
- **15 event types** — Task, LLMOutput, ExecutionOutput, ErrorEvent, ToolCallEvent, ToolResultEvent, BeforeTurn, AfterTurn, BeforeAgentCall, AfterAgentCall, LLMCallStart, LLMCallEnd, Feedback, Summary, LLMComplete
- **Context blocks** — static and dynamic blocks with expression evaluation, protected framework blocks, XML rendering
- **Expression evaluator** — reflection-based `{self.field}`, `{self.method()}`, `{Type.method(self)}` resolution
- **AgentDoc + visibility** — auto-generated API documentation, `@Hidden` annotation, filtered exec_globals
- **Tracing** — OpenTelemetry spans for agent calls, LLM calls, code execution; JSONL file exporter
- **Memory** — SQLite-backed knowledge store with typed records, importance weighting, tag-based recall, reflection/pruning, typed relationships
- **Permissions system** — DENY/ASK/ALLOW with glob pattern matching per resource type (files, commands, URLs, classes), callback interface for user approval
- **JShell sandbox** — timeout enforcement, API blocking (reflect, File, ProcessBuilder, Runtime, System, URL, Thread), permissions integration
- **ContextWindowStats** — token usage tracking with utilization percentage
- **TokenBudgetSummarizer** — auto-compaction when approaching context limit
- **AgentSnapshot** — JSON serialization of agent state (events, context blocks) with save/load/restore
- **ShellTools** — persistent shell session with path escape blocking, command permissions, timeout
- **TodoManager** — in-memory task tracking with status transitions and active filtering
- **Media types** — Image, Audio, Video, File with data URIs, MIME detection, content hashing
- **MCP integration** — stdio + SSE transports, JSON-RPC protocol, tool discovery, McpManager, mock server for testing
- **CLI** — InteractiveAgent with slash commands, QueueManager for channel-based messaging
- **Standalone functions** — `@Generate` on static methods without agent class
- **ATIF trajectory export** — event-capture-based JSONL export for evaluation
- **Skills system** — `Skill` base class with attach/detach lifecycle
- **MethodConditions** — pre/post-conditions with InvariantError for retry
- **Config classes** — CodeActConfig, PredictConfig, TruncationConfig, ExecutionConfig with merge semantics

### LLM Providers (`nooa-core`)

- OpenAI, Anthropic (native API), OpenRouter, DeepInfra, Groq, local Ollama
- Custom OpenAI-compatible endpoints
- Exponential backoff retry on 429/5xx

### CLAD Runtime Engine (`nooa-clad-runtime`)

- **ConceptAgent** — abstract base for concept agents with completion/refusal/error lifecycle
- **PredicateConceptAgent** — enforces sync-predicate validation before commit; rejects unmatched outcomes
- **PredicateSyncDispatcher** — passive evaluator; no scheduling loop, answers "which syncs match?"
- **SyncAgent** — declarative coordination rules with trigger/where/then semantics
- **ActionRecord** — immutable invocation descriptor with flow tokens and bindings
- **ConceptContext** — runtime interface decoupling agents from storage backend
- **SyncTrigger** — composite key `conceptIri::actionName` with optional outcome wildcard
- **SyncEvaluationException** — fail-fast protocol violation for unmatched outcomes

### CLAD CLI (`nooa-clad`)

- `nooa clad init <project>` — scaffolds project from bundled CLAD methodology templates
- `nooa clad run` — executes CLAD stages sequentially, stops at human gates
- `nooa clad run --auto` — auto-advances non-gate stages
- `nooa clad run --stage 02_concepts` — runs a single stage
- Git submodule for methodology sync (`clad/`)

### Testing

- FakeLLMClient for isolated LLM testing without API keys
- Mock MCP server for transport integration tests
- 164 tests, 0 failures across all modules

### Documentation

- README with architecture diagrams (Mermaid + ASCII), full getting-started guide, 10 concept sections
- 5 example files covering 15 SDK features
- `nooa-clad/README.md` with complete CLI reference
- Generated project README template for `nooa clad init`
- Apache 2.0 LICENSE + NOTICE with original paper attribution
