# NOOA Java Examples

These examples are small, runnable demonstrations of the `nooa-core` SDK. They
are intentionally split between deterministic Java orchestration and model
capabilities exposed with `@Generate`.

## Prerequisites

- Java 25+
- Maven 3.9+
- A model endpoint for examples that invoke `@Generate`

`ExampleLLM` selects a provider in this order:

1. `NOOA_BASE_URL`, with `NOOA_API_KEY` and `NOOA_MODEL`
2. `OPENAI_BASE_URL`, with `OPENAI_API_KEY` and `OPENAI_MODEL`
3. OpenAI using `OPENAI_API_KEY`
4. Local Ollama using `NOOA_MODEL` or the default model

Set the environment explicitly before running a live example. The examples do
not require credentials to compile or test.

## Build and run

From the repository root:

```bash
mvn -pl examples -am test
```

Run an entry point with the Maven Exec plugin:

```bash
mvn -pl examples -am compile exec:java \
  -Dexec.mainClass=ai.nooa.examples.QuickstartExamples
```

Replace the main class with any entry point in the catalog below. Examples that
call a model make live requests and may incur provider costs.

## Example catalog

| Entry point or type | Demonstrates | Runtime requirements |
| --- | --- | --- |
| `QuickstartExamples` | Basic `@Generate`, `PredictStrategy`, Java helper facts, and a Java-owned workflow | Model endpoint |
| `QuickstartAdvanced` | Progressive disclosure, dynamic context, and explicit context blocks | Model endpoint |
| `StrategyComparisonDemo` | Executes CodeAct, `PredictStrategy`, and `ReflexionStrategy` against one problem | Model endpoint |
| `TracingDemo` | Enables JSONL tracing and makes one traced generated call | Model endpoint; writes `traces_demo/` |
| `SummarizationDemo` | Installs a token-budget summarizer and sends one message | Model endpoint |
| `SnapshotDemo` | Saves and restores agent context events | Java only; writes `snapshot_demo.json` |
| `ShellToolsDemo` | Runs a trusted-environment shell command through `ShellTools` | Shell access |
| `MemoryDemo` | SQLite-backed memory lifecycle | Java; creates `.nooa-demo-memory.db` |
| `McpDemo` | MCP filesystem tool discovery and call over stdio | Node.js and `@modelcontextprotocol/server-filesystem` |
| `Examples04to15` | Compatibility launcher pointing to the focused strategy, tracing, summarization, snapshot, and shell demos | No model call; prints guidance |
| `Examples10to11` | Compatibility launcher pointing to the memory and MCP demos | No model call; prints guidance |
| `GreetingAgent` | Plain text `@Generate` capability | Model endpoint |
| `SentimentAgent` | Typed JSON output with `PredictStrategy` | Model endpoint |
| `SupportAgent` | Hidden Java state and helper-provided inventory facts | Model endpoint |
| `ResearchAgent` | Model-visible helper methods and progressive disclosure | Model endpoint |
| `StrategyDemoAgent` | Default CodeAct, `PredictStrategy`, and `ReflexionStrategy` side by side | Model endpoint |
| `ProjectAgent` | Java-owned mutable state rendered through dynamic context | Model endpoint |
| `DebugAgent` | Explicit context blocks for a debugging task | Model endpoint |
| `MemoryDemoAgent` | Memory writes, recall, tags, relationships, forgetting, and reflection | SQLite file `.nooa-demo-memory.db` |
| `McpDemoAgent` | Agent type for MCP-backed capabilities | Model endpoint and configured MCP transport |
| `NewsDigestAgent` | Deterministic input preparation followed by typed model summarization | Model endpoint |
| `WeatherAgent` | HTTP/JSON data collection, typed records, two-stage typed generation, and Java orchestration | Model endpoint and network access to `api.weather.gov` |
| `LegalIntakeDemo` | Structured intake output with a legal-safety prompt boundary | Model endpoint |
| `ShellDemoAgent` | Java helper output supplied to a generated code-review response | Model endpoint and local file access |
| `SnapshotDemoAgent` | Agent context and event snapshot/restore | Model endpoint when its generated method is called |
| `SummarizationDemoAgent` | Token-budget summarizer installation around a generated conversation | Model endpoint |
| `TraceDemoAgent` | Agent/LLM/code tracing setup | Model endpoint when invoked; writes JSONL traces |

## What each example should teach

### Core agent shape

`GreetingAgent` is the smallest agent: extend `Agent`, add `@Generate`, and let
`AgentFactory.create(...)` provide the instrumented subclass. `SentimentAgent`
adds a record and `PredictStrategy`, showing where typed output validation belongs.

`SupportAgent`, `ResearchAgent`, and `ProjectAgent` demonstrate the boundary
between deterministic Java and model behavior. Java owns inventory, helper
results, state transitions, and orchestration; the model receives those facts
through the runtime capability surface and context rendering.

### Strategies and workflows

`StrategyDemoAgent` compares the default CodeAct loop with single-shot typed
prediction and a reflexion loop. `NewsDigestAgent` and `WeatherAgent` show a
stronger production shape: fetch or construct facts in Java, then call narrow
generated methods with typed inputs and outputs.

`LegalIntakeDemo` is an example of constrained classification, not legal advice.
Its prompt and result schema classify and route an intake message; an application
must still apply its own legal review and escalation policy.

### Runtime services

`QuickstartAdvanced` covers context and visibility. The focused runtime demos
cover tracing, summarization, snapshots, shell access, durable memory, and MCP
individually. The compatibility launchers remain for older commands but do not
run the demos themselves.

## Safety and cleanup

The examples are educational, not security boundaries. In particular:

- `ShellTools` executes local commands and should only be run in a disposable or
  explicitly trusted environment.
- `MemoryDemoAgent` creates `.nooa-demo-memory.db` in the working directory.
- `SnapshotDemo` writes `snapshot_demo.json`; `TracingDemo` writes `traces_demo/`.
- MCP and weather examples access external processes or networks.
- In-process JShell execution is defense-in-depth, not process/container isolation.

Remove those generated files after a run if they are not needed. Do not place
production credentials in prompts, context, snapshots, or trace output.