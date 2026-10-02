# NOOA Java Examples

Small, runnable demonstrations of the `nooa-core` SDK. Every example is split
intentionally between deterministic Java orchestration and model capabilities
exposed with `@Generate`.

## Quick start

```bash
# From the repository root. Requires Java 25+, Maven 3.9+, and a model endpoint.
./examples/run.sh --list                  # see what can run
./examples/run.sh QuickstartExamples      # run one example
./examples/run.sh --all                   # run everything, print a pass/fail summary
```

`run.sh` builds the SDK, points the examples at local Ollama
(`http://localhost:11434/v1`) with model `qwen3.8:27b-mlx` unless you configure
otherwise, runs the selected entry points through Maven, and reports
`PASS`/`FAIL` per example. Pass `--timeout N` to change the per-example limit
(default 600 s).

Without `run.sh`, run an entry point directly:

```bash
mvn -pl examples -am clean install -DskipTests
mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.QuickstartExamples
```

The `clean` matters if you edit this project in VS Code: the Eclipse JDT
language server compiles into the same `target/classes` directory as Maven and
can race a clean build. While sibling classes are missing it writes ECJ class
stubs with embedded `Unresolved compilation problem` errors; because those
stubs are newer than the sources, incremental javac never replaces them and
they fail at runtime with confusing errors.

The build guards against this: a `process-classes` check fails with a clear
message if stub classes are detected, so a poisoned `target/` can never be
installed silently. To reduce the race itself, stop the language server from
building into `target/` — in VS Code settings:

```jsonc
{
  // Stop background builds into target/classes (diagnostics update on demand)
  "java.autobuild.enabled": false
  // or, more drastic: "java.server.launchMode": "LightWeight"
}
```

After a mass rebuild, `Java: Clean Java Language Server Workspace` from the
command palette refreshes the IDE's view.

## Model configuration

`ExampleLLM.create()` picks a provider in this order:

1. `NOOA_BASE_URL`, with `NOOA_API_KEY` and `NOOA_MODEL` — any OpenAI-compatible endpoint
2. `OPENAI_BASE_URL`, with `OPENAI_API_KEY` and `OPENAI_MODEL`
3. OpenAI using `OPENAI_API_KEY`
4. Local Ollama using `NOOA_MODEL` (default `qwen3.8:27b-mlx`), reached through
   the OpenAI-compatible endpoint `NOOA_OLLAMA_BASE_URL`
   (default `http://localhost:11434/v1`)

**Why the OpenAI-compatible endpoint for Ollama?** The native `/api/chat` path
in `UnifiedLLM` does not forward tool definitions, which would silently disable
the default `CodeActStrategy` (its `executeJava`/`returnResult` tools would
never reach the model). The `/v1` endpoint speaks the same tools +
`response_format` protocol as the hosted providers, so examples behave the same
locally and remotely.

### Reasoning effort

Examples declare the reasoning level their task needs through
`ExampleLLM.tune(agent, effort, maxTokens)`, which sets per-agent sampling
overrides that the runtime merges into every LLM request:

- `low` — deterministic classification, extraction, short answers
  (`GreetingAgent`, `SentimentAgent`, `LegalIntakeAgent`, `ProjectAgent`, ...)
- `medium` — structured production through the CodeAct protocol and judgement
  over real data (`SupportAgent`, `WeatherAgent`, `solveWithCode`)
- `high` — multi-step self-critique (`solveWithReflection`)

Override everything for a tuning session with `NOOA_REASONING_EFFORT` or
`-Dnooa.reasoningEffort=...`, and the per-turn output bound with
`-Dnooa.maxTokens=...`.

## Example catalog

Runnable entry points (`./examples/run.sh <name>`):

| Entry point | Demonstrates | Runtime requirements |
| --- | --- | --- |
| `QuickstartExamples` | Basic `@Generate`, `PredictStrategy`, Java helper facts, and a Java-owned workflow | Model endpoint |
| `QuickstartAdvanced` | Progressive disclosure, dynamic context, and explicit context blocks | Model endpoint |
| `StrategyComparisonDemo` | CodeAct, `PredictStrategy`, and `ReflexionStrategy` on one problem, with per-stage reasoning effort | Model endpoint |
| `TracingDemo` | JSONL tracing; writes `traces_demo/` | Model endpoint |
| `SummarizationDemo` | Token-budget summarizer installation | Model endpoint |
| `SnapshotDemo` | Agent snapshot save/restore; writes `snapshot_demo.json` | Java only |
| `ShellToolsDemo` | `ShellTools` with an explicit `Permissions` grant | Shell access |
| `MemoryDemo` | SQLite memory lifecycle; creates `.nooa-demo-memory.db` | Java only |
| `McpDemo` | MCP filesystem tool discovery and calls over stdio | Node.js and `@modelcontextprotocol/server-filesystem` |
| `LegalIntakeDemo` | Structured triage via `@Generate`/`PredictStrategy` and via `StructuredOutputHelper` | Model endpoint |
| `NewsDigestAgent` | Deterministic input preparation followed by typed model summarization (has its own `main`) | Model endpoint |
| `WeatherAgent` | HTTP/JSON collection, typed records, two-stage typed generation, Java orchestration (has its own `main`) | Model endpoint and network access to `api.weather.gov` |
| `IncidentDemo` | Incident-response workflow as a **sealed-type state machine**: telemetry helpers, CodeAct investigation, typed remediation, human approval gate | Model endpoint |
| `ReleaseDemo` | Release gate as an **enum-style state machine** (payload-free phases with abstract transition methods) | Model endpoint |
| `Examples04to15` | Compatibility notice for the old combined launcher | Nothing |
| `Examples10to11` | Compatibility notice for the old memory/MCP launcher | Nothing |

Agent classes (no `main`; exercised through the demos above, or instantiate
them via `AgentFactory.create(...)` in your own code):

| Agent class | Demonstrates |
| --- | --- |
| `GreetingAgent` | The smallest agent: `@SystemPrompt` + one plain-text `@Generate` capability |
| `SentimentAgent` | Typed JSON output with `PredictStrategy` (record as enforced contract) |
| `SupportAgent` | `@Hidden` state, a public helper as the model's source of truth, constructor injection |
| `ResearchAgent` | Model-visible helper methods and progressive disclosure via `__agent__` calls |
| `StrategyDemoAgent` | Default CodeAct, `PredictStrategy`, and `ReflexionStrategy` side by side |
| `ProjectAgent` | Java-owned mutable state rendered through a dynamic context block |
| `DebugAgent` | Static context blocks (`put`/`remove`) steering the model |
| `MemoryDemoAgent` | Memory writes, recall, tags, relationships, forgetting, and reflection |
| `McpDemoAgent` | Agent type for MCP-backed capabilities (MCP tools are wired by the application) |
| `LegalIntakeAgent` | Constrained classification with a legal-safety prompt boundary; both extraction paths |
| `ShellDemoAgent` | Java helper output supplied to a generated code-review response |
| `SnapshotDemoAgent` | Agent context and event snapshot/restore payload |
| `SummarizationDemoAgent` | `TokenBudgetSummarizer` installation around a generated conversation |
| `TraceDemoAgent` | Agent/LLM/code tracing target |
| `IncidentAgent` | Object-first workflow: `state` field + pure sealed-type transition function, typed `@Generate` payloads, deterministic telemetry helpers, `ApprovalGate` |
| `ReleaseGateAgent` | Drives an enum-style FSM (see the `release` package) |

## What each example teaches

### Core agent shape

`GreetingAgent` is the smallest agent: extend `Agent`, add `@Generate`, and let
`AgentFactory.create(...)` provide the instrumented subclass. `SentimentAgent`
adds a record and `PredictStrategy`, showing where typed output validation
belongs.

`SupportAgent`, `ResearchAgent`, and `ProjectAgent` demonstrate the boundary
between deterministic Java and model behavior. Java owns inventory, helper
results, state transitions, and orchestration; the model receives those facts
through the runtime capability surface and context rendering.

**Visibility rules worth knowing:**

- Only `public` methods are model-callable from generated code (`AgentDoc` is
  built from `getMethods()`). Make a helper public if the model should call it;
  use `@Hidden` to exclude one instead.
- Agent classes must be public and top-level (or have an accessible enclosing
  class) so the sandbox preamble can bind `__agent__` — JShell rejects types
  whose canonical name passes through a package-private enclosing class.
- `@Hidden` is a visibility control, not a security boundary.

### Strategies and workflows

`StrategyDemoAgent` compares the default CodeAct loop with single-shot typed
prediction and a reflexion loop. `NewsDigestAgent` and `WeatherAgent` show a
stronger production shape: fetch or construct facts in Java, then call narrow
generated methods with typed inputs and outputs.

`LegalIntakeDemo` is an example of constrained classification, not legal advice.
Its prompt and result schema classify and route an intake message; an
application must still apply its own legal review and escalation policy.

The `incident` and `release` examples show the two ways to model a workflow as a
state machine in modern Java. `IncidentAgent` uses **sealed interfaces + records
+ pattern-matching `switch`** for a real workflow where each state carries data
and transitions have guards (for example, a remediation cannot be resolved until
verification passes), with Java owning every transition and the model supplying
typed payloads. `ReleaseGateAgent` uses the **enum-with-abstract-methods** style
for a payload-free pipeline. See `docs/concepts.md` for the contrast.

### Runtime services

`QuickstartAdvanced` covers context and visibility. The focused runtime demos
cover tracing, summarization, snapshots, shell access, durable memory, and MCP
individually. The compatibility launchers remain for older commands but do not
run the demos themselves.

## Tests

`mvn -pl examples -am test` runs the offline contract tests (`FakeLLMClient`,
no model needed): every representative agent is instrumentable through
`AgentFactory`, and the deterministic helper surfaces keep their state in Java.

## Safety and cleanup

The examples are educational, not security boundaries. In particular:

- `ShellToolsDemo` grants `Permissions.allowAll()` explicitly because it is a
  trusted demo; production agents should add narrow rules instead.
- `MemoryDemoAgent` creates `.nooa-demo-memory.db` in the working directory.
- `SnapshotDemo` writes `snapshot_demo.json`; `TracingDemo` writes
  `traces_demo/`; `McpDemo` writes `~/nooa-mcp-test.txt`.
- MCP and weather examples access external processes or networks.
- In-process JShell execution is defense-in-depth, not process/container
  isolation.

These generated files are listed in the root `.gitignore`; remove them after a
run if they are not needed. Do not place production credentials in prompts,
context, snapshots, or trace output.
