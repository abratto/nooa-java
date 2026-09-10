# Building Agents with NOOA Java

This guide explains how to choose NOOA Java capabilities for different application
contexts. The SDK keeps the public programming model synchronous and Java-native:
ordinary Java owns orchestration, while `@Generate` methods provide model-powered
capabilities.

## Choose the runtime shape first

| Context | Recommended entry point | What Java should own |
|---|---|---|
| Request/response service | `AgentFactory.create(...)` and a normal service method | Request validation, authentication, persistence, retries, response mapping |
| Interactive CLI or operator tool | `InteractiveAgent.create(...)` or `.run(...)` | Input transport, command policy, session lifecycle |
| Background worker | `AgentFactory.create(...)` plus `SessionStore` | Job leases, scheduling, idempotency, checkpoint timing |
| Structured extraction/classification | `@Strategy(PredictStrategy.class)` | Schema definition and validation policy |
| Tool-using reasoning | default `CodeActStrategy` | Permissions, allowed helpers, execution limits |
| Multi-step business workflow | normal Java orchestrator calling several `@Generate` methods | Branching, state transitions, escalation, side effects |

Do not make the model own the application workflow. Keep deterministic boundaries in
Java and make each generated method one focused model task.

## Choose a generation strategy

The default strategy is `CodeActStrategy`, which gives the model an iterative Java
execution loop. Use it when the task genuinely needs helper calls, state inspection,
or multiple tool steps. For focused typed extraction or classification, prefer
`PredictStrategy`:

```java
@Generate @Strategy(PredictStrategy.class)
Classification classify(String request) {
    throw new UnsupportedOperationException("Generated at runtime");
}
```

Use `ReflexionStrategy` when a draft-critique-revision loop is worth the extra model
calls. Use `TemplateStrategy` when the application controls the output shape and the
model should fill a constrained template. Configure an agent-wide default with
`AgentConfig.withDefaultStrategy(...)`, or override a method with
`AgentConfig.withStrategy(...)`.

For CodeAct, tune `CodeActConfig.maxIterations`, `maxRetries`, and
`cellTimeoutMillis` to the request's latency and risk budget. For Predict, tune
`PredictConfig.maxRetries` and only set sampling fields such as `temperature` or
`reasoningEffort` when the task needs them. Keep the default strategy conservative in
interactive and production request paths; give exploratory workers their own explicit
configuration.

## Choose the code execution boundary

CodeAct uses a `SandboxExecutor` for generated Java. The default executor is
`JShellSandbox`, which runs in the application process and provides timeout,
API-blocking, and permission checks. This is appropriate for trusted or reviewed
agents, but it is not an OS-level security boundary.

Applications that process untrusted prompts, repositories, or customer code can
provide an executor backed by a worker process, container, VM, or remote sandbox:

```java
AgentConfig config = AgentConfig.defaults()
    .withSandboxExecutor(agent -> new IsolatedWorkerExecutor(workerClient, agent));
```

`SandboxExecutor` is intentionally an SPI rather than a container manager. The
embedding application owns the isolated worker's lifecycle, credentials, resource
limits, and network policy. Use the in-process default for low-overhead embedding;
use an externally isolated executor when generated code must be treated as hostile.

## Service and request/response agents

Create one agent per request or per explicitly managed lifecycle. Use regular Java
methods for I/O and policy checks, and keep generated methods free of hidden side
effects.

```java
@SystemPrompt("You classify support requests for the operations team.")
final class SupportAgent extends Agent {
    SupportAgent(UnifiedLLM llm, AgentConfig config) {
        super(llm, config);
    }

    @Generate @Strategy(PredictStrategy.class)
    Classification classify(String request) {
        throw new UnsupportedOperationException("Generated at runtime");
    }
}

record Classification(String category, double confidence, String summary) {}
```

The surrounding service should validate the request, call `classify`, inspect the
typed result, and decide whether to continue, ask for clarification, or escalate.
Do not let an untrusted request choose arbitrary helper methods or bypass Java-side
authorization.

## Interactive agents and typed turns

`InteractiveAgent` supports both a console loop and programmatic turn processing.
The programmatic form is useful for a terminal UI, WebSocket adapter, test harness,
or another transport that already owns its event loop.

```java
var interactive = InteractiveAgent.create(agent)
    .onOutput(System.out::println)
    .command("reset", "Reset the current case", args -> agent.eventManager().clear());

interactive.submit("Summarize the current case");
InteractiveAgent.TurnResult result = interactive.turn();

switch (result.status()) {
    case COMPLETED -> use(result.value());
    case COMMAND -> logCommand(result.value());
    case NEED_INPUT -> requestMoreInput(result.output());
    case ERROR -> reportFailure(result.error());
}
```

Slash commands can be submitted through the same queue or executed directly with
`commandResult("/model")`. Built-ins include `/help`, `/clear`, `/history`,
`/model`, and `/exit`. An unknown command returns `TurnStatus.ERROR`; it does not
silently become a model prompt.

Use this mode when the transport, UI, or operator approval flow is external to the
SDK. Keep command handlers deterministic and short. A command handler should not
perform an unbounded model loop without an explicit application policy.

## Durable sessions and resumable workers

`SessionStore` saves agent context and event history as a JSON snapshot. Save after a
meaningful checkpoint, not after every token or transient UI update.

```java
var store = new SessionStore(Path.of("var/nooa-sessions"));
store.save(job.sessionId(), agent);

// Later, in another process or worker attempt:
store.restore(job.sessionId(), agent);
store.appendEvent(job.sessionId(), new Event.Task("resume processing"));
```

Session IDs may contain only letters, digits, `.`, `_`, and `-`. Call `save` before
`appendEvent`; appending requires an existing snapshot. `appendEvent` preserves the
saved context and event order. Treat the snapshot directory as application data:
use filesystem permissions, backup policy, retention, and encryption appropriate to
the data being stored.

Snapshots are a checkpoint mechanism, not a distributed lock or exactly-once job
system. A worker still needs a lease/idempotency strategy around side effects.

## Context blocks and context budgets

Use context blocks for facts that should be available to every generated call.
Use static blocks for values that change only when application code updates them, and
dynamic blocks when the value must be evaluated for every call.

```java
agent.context().put("customer", customerRecord);
agent.context().put("policy", policySummary);
agent.context().putDynamic("current_state", "self.currentState()");
```

The framework-protected `system_prompt`, `self`, and `state` blocks cannot be
removed or overridden. Rendering can be bounded with
`agent.contextManager().render(agent, maxChars)`; when the bound is exceeded, the
rendered context is truncated in insertion order. Choose the bound with the model's
context window and the rest of the prompt in mind.

Structured values retain their object form in snapshots. Their prompt rendering uses
`String.valueOf`, so provide a stable `toString()` or store a deliberately formatted
summary when prompt readability matters. Do not put secrets into context unless the
prompt path and persistence policy explicitly allow them.

## Skills and runtime capability packs

A `Skill` is an application-defined capability pack with attach/detach lifecycle
hooks. `SkillRegistry` keeps discovered skills separate from active skills and
checks declared dependencies before activation.

Register directly:

```java
var skills = new SkillRegistry()
    .register(new SearchSkill())
    .register(new TicketingSkill());
skills.activate("search");
```

For plugin-style discovery, publish implementations under
`META-INF/services/ai.nooa.skills.Skill`, then call:

```java
var skills = new SkillRegistry().discover();
skills.activate("search");
```

Discovery does not activate skills, attach them to an agent, or grant permissions.
Those are application decisions. Deactivate skills during shutdown or when a
request-scoped capability should no longer be available.

## Middleware, policy, and observability

Use `CallMiddleware` for cross-cutting operation policy: metrics, tracing metadata,
request normalization, sampling overrides, response inspection, or execution gates.
Existing `beforeLlm` and `beforeCode` hooks remain available. The request forms add
explicit rewriting and rejection:

```java
CallMiddleware policy = new CallMiddleware() {
    @Override
    public LlmRequest beforeLlmRequest(Agent agent, LlmRequest request) {
        if (request.messages().size() > 40) {
            return LlmRequest.reject("prompt exceeds policy limit");
        }
        return request.withSamplingParams(Map.of("temperature", 0.1));
    }

    @Override
    public CodeRequest beforeCodeRequest(Agent agent, CodeRequest request) {
        return request.withCode(request.code());
    }
};
```

A rejection raises the typed `AbortError`. Middleware runs around LLM and code
operations, so use it for policy that must apply consistently across generated
methods. Keep business decisions in the agent or service layer where they remain
visible and testable.

## Typed failures and testing

Handle failures by category instead of matching log text:

- `ValidationError`: a precondition, postcondition, or invariant failed.
- `RestrictedCodeError`: generated code attempted a blocked sandbox operation.
- `AbortError`: application or middleware intentionally stopped generation.
- `GenerationError`: model, parsing, or generation failure.
- `SessionStorageError`: durable session I/O failed.

Use `FakeLLMClient` for deterministic unit tests, assert typed values for
`PredictStrategy`, and test middleware and permission boundaries without making live
provider calls. Reserve live-model tests for a small integration suite with explicit
credentials and cost controls.

## Production checklist

Before deploying an agent, decide:

1. Which methods are generated, and which validation and side effects stay in Java?
2. What context is safe to send to the model and safe to persist?
3. Which code, URL, file, and class permissions are allowed?
4. Where are sessions checkpointed, retained, encrypted, and recovered?
5. Which middleware policies apply to every LLM and code operation?
6. Which typed failures are retryable, user-visible, or terminal?
7. How will prompts, events, traces, and model usage be inspected without leaking secrets?
