# NOOA Core Package Reference

`nooa-core` is organized by responsibility. This page is the package-level map for
users and maintainers; class-level API details remain in JavaDoc.

## Package map

| Package | Responsibility | Start here |
|---|---|---|
| `ai.nooa` | Agent base type, factory, standalone generation, typed generation errors | `Agent`, `AgentFactory` |
| `ai.nooa.annotations` | Generation, strategy, prompt, visibility, and tracing metadata | `@Generate`, `@Strategy`, `@SystemPrompt`, `@Hidden` |
| `ai.nooa.agentdoc` | Runtime documentation and visible API inspection | `AgentDoc` |
| `ai.nooa.atif` | ATIF trajectory export for evaluation and analysis | `AtifExporter` |
| `ai.nooa.cli` | Interactive console and programmatic turn queues | `InteractiveAgent`, `QueueManager` |
| `ai.nooa.config` | Immutable agent, strategy, execution, and truncation settings | `AgentConfig`, `CodeActConfig`, `PredictConfig` |
| `ai.nooa.context` | Context blocks, events, and context-window statistics | `ContextBlock`, `Event` |
| `ai.nooa.eval` | Evaluation harness: datasets, runner, scorers, reliability metrics, reports, baseline/history, CLI, JUnit extension | `EvalRunner`, `EvalDataset` |
| `ai.nooa.llm` | Provider abstraction, messages, tools, responses, and structured output | `UnifiedLLM`, `StructuredOutputHelper` |
| `ai.nooa.mcp` | MCP client, tool discovery, JSON-RPC, stdio, and SSE transports | `McpManager`, `McpClient` |
| `ai.nooa.media` | Image, audio, video, and file content values | `Media` |
| `ai.nooa.memory` | Persistent records, recall, relationships, and memory skill support | `MemoryStore`, `MemorySkill` |
| `ai.nooa.observability` | Prompt capture and offline prompt inspection | `PromptRecorder` |
| `ai.nooa.runtime` | Prompt assembly, event management, snapshots, sessions, middleware, and expression evaluation | `ActorRuntime`, `SessionStore` |
| `ai.nooa.runtime.sandbox` | Pluggable CodeAct execution and sandbox bindings | `SandboxExecutor`, `JShellSandbox` |
| `ai.nooa.security` | Resource permissions and approval callbacks | `Permissions`, `PermissionCallback` |
| `ai.nooa.skills` | Runtime capability registration, discovery, and activation | `SkillRegistry` |
| `ai.nooa.strategy` | CodeAct, Predict, Reflexion, Template, conditions, prefills, and runtime services | `GenerationStrategy` |
| `ai.nooa.tools` | Shell sessions and model-visible todo tracking | `ShellTools`, `TodoManager` |
| `ai.nooa.tracing` | OpenTelemetry and JSONL tracing | `Tracing` |

## Strategy selection

| Strategy | Use it when | Main tradeoff |
|---|---|---|
| `CodeActStrategy` | The model must inspect state, call helpers, execute Java, and iterate | More flexible, but requires strict permissions, timeouts, and iteration limits |
| `PredictStrategy` | The task is one focused classification or extraction with a typed result | More constrained and predictable, but not suited to multi-step tool use |
| `ReflexionStrategy` | A draft should be generated, critiqued, and improved | Uses additional model calls and latency |
| `TemplateStrategy` | The output follows a deterministic prompt/template pattern | Less autonomous; best when the application controls the shape |

The default is `CodeActStrategy`. Select a strategy per method with
`@Strategy(...)` or per agent with `AgentConfig.withDefaultStrategy(...)` /
`withStrategy(...)`. Keep orchestration and side effects in ordinary Java methods.

## Configuration guidance

Start with defaults, then tune the smallest relevant scope:

- `CodeActConfig.maxIterations`: upper bound on the agent loop. Lower it for
  interactive or latency-sensitive requests; raise it only for genuinely iterative
  tasks.
- `CodeActConfig.maxRetries`: retries for recoverable generation failures.
- `CodeActConfig.cellTimeoutMillis`: maximum time for one generated JShell cell.
- `CodeActConfig.allowTextFallback`: whether text-only model responses can be accepted
  when no tool call is returned.
- `PredictConfig.maxRetries`: retries for invalid structured output.
- `PredictConfig.maxTokens`, `temperature`, and `reasoningEffort`: provider/model
  sampling controls; leave unset unless the task needs a specific policy.
- `TruncationConfig`: bounds stdout, stderr, errors, strings, containers, and nesting
  depth before content enters the model context.
- `AgentConfig.maxNestingDepth`: protects against runaway nested generated calls.
- `AgentConfig.middleware`: applies cross-cutting policy around LLM and code operations.
- `AgentConfig.withSandboxExecutor(...)`: injects an executor factory for a worker
  process, container, VM, or remote sandbox. The default is in-process JShell.

Configuration is immutable. Build a configured `AgentConfig` before creating the
agent; avoid mutating global settings from inside generated methods.

## Security-sensitive packages

### `runtime.sandbox`

The sandbox executes generated Java through JShell and applies timeout, API-blocking,
and permission checks. `SandboxExecutor` makes the execution boundary pluggable; the
default is still in-process and is not a substitute for process or container
isolation. Treat generated code as untrusted, use least-privilege permissions, set
execution limits, and avoid granting access to production secrets. Applications
that need hard containment must inject an externally isolated executor.

The package now has dedicated regression coverage for initialization, variable
binding, explicit returns, timeouts, cleanup, blocked APIs, permission overrides, and
cross-thread context state. Keep treating it as a defense-in-depth control rather
than a complete security boundary.

### `security` and `tools`

`Permissions` is the policy layer for files, commands, URLs, classes, and related
resources. `ShellTools` executes persistent shell sessions and must be governed by
that policy. A tool being available to the model does not mean it should be allowed in
every deployment.

## Integration package notes

- `llm`: choose a provider through `UnifiedLLM`; use `FakeLLMClient` in deterministic
  tests and reserve live-provider tests for a small integration suite.
- `mcp`: use stdio for local child-process servers and SSE for network services. Add
  application-level lifecycle, timeout, authentication, and reconnect policy around
  the transport.
- `media`: media values carry content and MIME metadata; validate size and type before
  forwarding user-provided files to a provider.
- `memory`: use context blocks for short-lived prompt facts; use `MemoryStore` for
  durable searchable knowledge that should outlive a single agent call.
- `atif`, `observability`, and `tracing`: enable them selectively in evaluation or
  production diagnostics, and redact sensitive prompts/events before exporting them.
- `eval`: define cases and datasets, run agents in LIVE/RECORD/REPLAY, score with the
  built-in scorers, and gate on `pass@k`/`pass^k` and regression baselines. See
  [eval-guide.md](eval-guide.md) for usage and [eval-roadmap.md](eval-roadmap.md) for
  design history.

## Audit status

The package inventory, source JavaDocs, and existing tests have been reviewed. Most
packages are usable and have regression coverage, but package completeness is not
uniform. The sandbox contract is covered by focused tests; secondary gaps remain in
deeper examples and contract tests for event/media/MCP edge cases. This page is
intentionally explicit about those limits so users do not infer stronger guarantees
than the current test suite establishes.
