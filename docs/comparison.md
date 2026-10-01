# Comparison with Other Agent Frameworks

The most important difference between agent frameworks is not whether they are
"agentic"; it is what the primary abstraction is.

## LangGraph: graph-first orchestration

LangGraph treats the workflow as the main artifact. The program is expressed as
nodes, edges, conditions, and state transitions. This is excellent when the
business process itself needs to be inspected, debugged, or modified as a graph,
and it is a natural fit for explicit workflows, loops, and human-in-the-loop
handoffs.

The cost is that the mental model is graph-heavy: you often design workflow
structure before designing the domain object that owns the task.

## Role-based systems: agent-first collaboration

Frameworks built around roles, teams, and message passing treat agents as
participants in a collaborative process. A manager agent may delegate to worker
agents, which pass outcomes back through conversation or tool calls. This is
useful when the system is inherently social: multiple specialists, debate,
planning, delegation, or trial-and-error coordination.

The tradeoff is that orchestration often sits above the business object. The agent
becomes a participant in a larger coordination layer rather than a native part of
the domain model.

## NOOA: object-first orchestration

NOOA's primary abstraction is a Java object:

- state lives in fields
- helpers are regular Java methods
- `@Generate` methods are model-powered capabilities
- orchestration is plain Java control flow
- the runtime handles prompt generation, tool execution, and result validation

That means the workflow can be modeled as a method-driven state machine without
introducing a separate graph or workflow DSL. The business process is represented
in the class itself, in idiomatic Java.

| Framework | Primary abstraction | Orchestration style | Best fit |
|---|---|---|---|
| LangGraph | graph | explicit node/edge transitions | workflow-heavy systems with visible branching |
| Role-based agent systems | specialized agents | delegation and collaboration | multi-agent task decomposition |
| NOOA | Java object | method calls + runtime strategy loop | domain objects that need agent capabilities |

## Why NOOA feels simpler in Java

For Java developers this is often the most natural shape:

- keep the business logic in normal Java classes
- express the workflow as method sequencing and state transitions
- reserve the LLM for the capability step, not the entire orchestration layer

Instead of rewriting the app around a graph engine, you put the agent where it
belongs: as a stateful Java object participating in your application logic. This
is especially valuable when the agent is embedded inside an operational workflow,
a service, or a domain object that already has real state and business rules.

## Related

- [concepts.md](concepts.md) — the object-first model in practice.
- [architecture.md](architecture.md) — how the runtime executes an agent.
