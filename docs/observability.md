# Observability

NOOA is event-sourced: the runtime emits typed `Event`s for every LLM call, tool
call, turn, retry, and permission decision. Tracing, prompt capture, snapshots,
trajectory export, and evaluation all read from that one stream. This page covers
how to inspect, capture, and export what happened.

## Events

`agent.eventManager()` holds the conversation and lifecycle history and is the
subscription point for tooling:

```java
agent.eventManager().onEvent(event -> {
    if (event instanceof Event.PromptBuilt prompt) {
        // inspect the exact outbound prompt
    }
});
```

Key event types: `Task`, `LLMOutput`, `ExecutionOutput`, `ToolCallEvent`,
`ToolResultEvent`, `BeforeTurn`/`AfterTurn`, `BeforeAgentCall`/`AfterAgentCall`,
`LLMCallStart`/`LLMCallEnd`, `LLMComplete` (tokens), `Feedback`, `Summary`,
`PromptBuilt`, `Retry`, and `PermissionDecision`.

Context-window usage is available from the runtime:

```java
var stats = agent.runtime().stats(); // prompt/completion/total tokens, utilization
```

## Prompt capture

Emit the exact outbound prompt bundle before each LLM call for tuning:

```bash
export NOOA_LOG_PROMPTS=true      # redacted by default
export NOOA_LOG_PROMPTS_RAW=true  # disable redaction (trusted/debug only)
```

When enabled, the runtime emits a `PromptBuilt` event containing the model name,
full message list, tool names, output model, and sampling params. Redaction masks
common secret patterns (API keys, tokens, bearer credentials) — including values
whose rendered key looks sensitive (`token`, `secret`, `password`, `apiKey`), so a
legitimately-named argument such as `token` will appear as `[REDACTED]`. For trusted
local debugging, `NOOA_LOG_PROMPTS_RAW=true` disables redaction.

Where to inspect prompts:

- **In-process:** subscribe to `eventManager().onEvent(...)` and filter
  `PromptBuilt`.
- **JSONL recorder:** stream prompts to a file for offline tuning.

```java
var recorder = PromptRecorder.attach(agent, Path.of("prompts.jsonl"));
// ... agent work happens ...
recorder.close(); // writes are appended eagerly, so partial runs remain inspectable
```

- **Snapshot:** `PromptBuilt` is included in `AgentSnapshot` save/load.
- **ATIF:** `PromptBuilt` is exported by `AtifExporter` for trajectory analysis.

For how prompts are assembled and how to design them, see
[prompt-engineering.md](prompt-engineering.md).

## Tracing

Enable OpenTelemetry spans and/or JSONL tracing:

```java
Tracing.enable(Tracing.jsonl(Path.of("./traces")));
// or auto-enable from the environment:
//   NOOA_TRACE_DIR=./traces        → JSONL exporter
//   OTEL_EXPORTER_OTLP_ENDPOINT=…  → OTLP exporter
```

Spans are emitted for agent calls (`AGENT <Class>.<method>`), LLM calls
(`LLM generate`, `llm.model`), and code execution (`CODE execute`). Methods
annotated `@NoTrace` are excluded from agent spans.

## Trajectory export (ATIF)

`AtifExporter` writes a structured trajectory (steps, prompts, tool calls,
execution output, retries, permission decisions) for evaluation and analysis:

```java
try (var exporter = AtifExporter.attach(agent, Path.of("./trajectories"))) {
    // ... agent work ...
    exporter.flush();
}
```

## Middleware

`CallMiddleware` wraps generated calls and LLM/code operations for cross-cutting
policy and metrics — the only hook that sees the raw `LLMResponse` (including
`usage()`):

```java
CallMiddleware metrics = new CallMiddleware() {
    @Override public LLMResponse afterLlm(Agent agent, LLMResponse response) {
        // record tokens, latency, or provider-specific fields
        return response;
    }
};

new AgentConfig().withMiddleware(metrics);
```

See [agent-building-guide.md](agent-building-guide.md) for middleware patterns.

## Evaluation consumes the same signals

Because evaluation is event-sourced, an `ai.nooa.eval` run reads tokens, tool
calls, retries, permission decisions, and captured prompts directly from this
stream. See [eval-guide.md](eval-guide.md).
