# Architecture

NOOA keeps the primary abstraction a Java object and moves the agent machinery
into the runtime. This page describes the end-to-end execution path and the
components involved. For the conceptual model, see [concepts.md](concepts.md).

## Execution pipeline

Creating an agent instruments its class; calling a `@Generate` method routes the
call through `ActorRuntime`, the configured strategy, the LLM provider, and (for
CodeAct) the JShell sandbox, emitting events along the way.

```mermaid
sequenceDiagram
    participant User
    participant Agent as Agent<br/>(Your Class)
    participant AF as AgentFactory<br/>(ByteBuddy)
    participant AR as ActorRuntime
    participant S as Strategy<br/>(CodeAct/Predict)
    participant EM as EventManager
    participant CM as ContextManager
    participant LLM as UnifiedLLM<br/>(API Call)
    participant JS as JShellSandbox

    rect rgb(240, 248, 255)
        note right of User: 1. Create the agent
        User->>AF: AgentFactory.create(MyAgent.class, llm)
        AF->>AF: Scan @Generate methods
        AF->>AF: ByteBuddy subclass + interceptor
        AF-->>User: instrumented agent instance
    end

    rect rgb(255, 248, 240)
        note right of User: 2. Call a @Generate method
        User->>Agent: agent.analyze("input")
        Agent->>AF: interceptor fires
        AF->>AR: callPlan(strategy, call)
        AR->>EM: add BeforeAgentCall
        AR->>EM: add Task("input")
    end

    rect rgb(240, 255, 240)
        note right of User: 3. Strategy loop
        loop Until done or max iterations
            AR->>CM: render context blocks
            AR->>EM: toMessages()
            AR->>LLM: chat(messages, tools, outputSchema)
            LLM-->>AR: LLMResponse

            alt tool call: executeJava
                AR->>JS: execute(code)
                JS-->>AR: ExecutionResult(stdout, stderr)
                AR->>EM: add ExecutionOutput
            else tool call: returnResult
                AR-->>Agent: result value
            else structured output (Predict)
                S->>S: validate against Record
                AR-->>Agent: typed result
            else text-only
                AR->>EM: add LLMOutput
            end
        end
    end

    rect rgb(255, 240, 255)
        note right of User: 4. Return
        AR->>EM: add AfterAgentCall
        AR-->>User: result
    end
```

## Component map

```
┌──────────────────────────────────────────────────────────┐
│                    Your Agent Class                        │
│  ┌──────────┐  ┌──────────┐  ┌────────────────────────┐  │
│  │ @Generate │  │ @Generate│  │ public String helper() │  │
│  │ String    │  │ @Strategy│  │ { return "done"; }     │  │
│  │ analyze() │  │ classify │  │                        │  │
│  └────┬─────┘  └────┬─────┘  └───────────┬────────────┘  │
│       │              │                    │               │
│  ┌────┴──────────────┴────────────────────┴───────────┐  │
│  │              AgentFactory (ByteBuddy)               │  │
│  │  Intercepts @Generate → routes to ActorRuntime     │  │
│  └──────────────────────┬─────────────────────────────┘  │
└─────────────────────────┼────────────────────────────────┘
                          │
┌─────────────────────────┼────────────────────────────────┐
│                 ActorRuntime                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │generate()│  │execute() │  │callPlan()│  │ stats()  │ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └──────────┘ │
└───────┼─────────────┼─────────────┼──────────────────────┘
        │             │             │
  ┌─────┴─────┐ ┌─────┴──────┐ ┌──┴────────────┐
  │ UnifiedLLM │ │JShellSandbox│ │Strategy        │
  │ (HTTP)     │ │ (JDK built- │ │ CodeAct        │
  │ OpenAI     │ │  in JShell) │ │ Predict        │
  │ Anthropic  │ │ Timeout     │ │ Reflexion      │
  │ OpenRouter │ │ Permissions │ └───────────────┘
  │ DeepInfra  │ └────────────┘
  │ Groq       │
  │ Ollama     │
  └────────────┘

┌──────────────────────────────────────────────────────────┐
│                 Supporting Services                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │ Context  │  │  Event   │  │  Memory  │  │ Tracing  │ │
│  │ Manager  │  │ Manager  │  │  (SQLite)│  │ (OTel)   │ │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘ │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │  Shell   │  │   MCP    │  │   Todo   │  │Snapshot  │ │
│  │  Tools   │  │ Manager  │  │ Manager  │  │Save/Rest │ │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘ │
└──────────────────────────────────────────────────────────┘
```

The package-level map and class starting points live in
[package-reference.md](package-reference.md).

## Observations from the pipeline

- **Instrumentation is one-time and cached.** `AgentFactory` builds a ByteBuddy
  subclass per agent class and reuses it, so creating many instances is cheap.
- **Everything is event-sourced.** Context, tracing, prompt capture, snapshots,
  ATIF export, and evaluation all consume the same `Event` stream (see
  [observability.md](observability.md)). This is why the eval layer needs no
  separate instrumentation.
- **The sandbox is a permission boundary, not isolation.** CodeAct cells run in
  an in-process JShell sandbox behind a static permission gate; see
  [security.md](security.md).
