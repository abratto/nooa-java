# Changelog

## v0.1.0 (unreleased)

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
