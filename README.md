# NOOA Java — Object-Oriented Agents for Java

**An independent Java port of [NVIDIA's Object-Oriented Agents (NOOA)](https://github.com/nvidia-nemo/labs-OO-Agents) framework.**

An agent is a single Java class. Methods are capabilities, fields are state, and
annotations are metadata. The SDK handles LLM generation, code execution, and
structured output enforcement, preserving the six core design ideas of the
original framework:

- **Typed I/O** — records are enforced return-type contracts
- **Pass by reference** — live objects in JShell, not serialized text
- **Code as action** — the model writes Java, executed in a sandbox
- **Programmable loops** — pluggable `GenerationStrategy` implementations
- **Object state** — instance fields on the agent
- **Model-callable harness APIs** — `context()`, `events()`, and helpers from generated code

## Requirements

Java 25+ · Maven 3.9+

## Install

```xml
<dependency>
  <groupId>ai.nooa</groupId>
  <artifactId>nooa-core</artifactId>
  <version>0.9.3</version>
</dependency>
```

For production use, pin to the released version (`0.9.3`); active development on
`main` uses the `0.9.4-SNAPSHOT` version. To build locally and install into your
local Maven repository:

```bash
mvn install
```

## Quick Start

```java
import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;

@SystemPrompt("You are a greeting agent. Be warm and concise.")
class GreetingAgent extends Agent {
    public GreetingAgent(UnifiedLLM llm) { super(llm); }

    @Generate(prompt = "Write a warm, personalized greeting for the given name in one sentence.")
    public String greet(String name) {
        throw new UnsupportedOperationException("Generated at runtime");
    }
}

var llm = UnifiedLLM.create(
    UnifiedLLM.openAI(System.getenv("OPENAI_API_KEY"), "gpt-4o").build());
var agent = AgentFactory.create(GreetingAgent.class, llm);
System.out.println(agent.greet("Alice"));
```

The class-level `@SystemPrompt` sets the persona for every model call; each
`@Generate(prompt = "...")` method carries the instruction for that capability.
The method body is never executed — `AgentFactory` instruments `@Generate`
methods and routes calls through the configured strategy.

## Run the examples

```bash
./examples/run.sh --all        # run every example, print a pass/fail summary
./examples/run.sh --list       # list runnable examples
```

Examples need a model endpoint (local Ollama by default); see
[examples/README.md](examples/README.md) for configuration and the full catalog.

## Documentation

**Foundations**
- [Core concepts](docs/concepts.md) — the object-first model, patterns, and a full walkthrough
- [Architecture](docs/architecture.md) — runtime pipeline and component map
- [agent-building-guide.md](docs/agent-building-guide.md) — choosing runtime shapes, strategies, sessions, skills, and middleware
- [prompt-engineering.md](docs/prompt-engineering.md) — how prompts are assembled and designed

**Capabilities**
- [Security and permissions](docs/security.md) — `Permissions`, the sandbox gate, and guarantee levels
- [Observability](docs/observability.md) — events, tracing, prompt capture, ATIF
- [Integrations](docs/integrations.md) — providers, memory, MCP, shell, task tracking
- [Evaluating agents](docs/eval-guide.md) — datasets, scorers, reliability metrics, reports
- [Comparison](docs/comparison.md) — NOOA vs graph-first and role-based frameworks

**Reference**
- [Package reference](docs/package-reference.md) — package map and configuration guidance
- [Examples](examples/README.md) — runnable catalog
- [SDK versioning](docs/sdk-versioning.md) — release and compatibility policy
- [Contributing](CONTRIBUTING.md) — build, release, and contribution process
- Roadmaps: [permission hardening](docs/roadmaps/permission-hardening.md), [evaluation](docs/roadmaps/eval.md)

## Why Java?

The Python NOOA framework shows that **agent-as-a-single-class** produces better
results across SWE-bench, ARC-AGI-3, and CyberGym. The six ideas map directly onto
the JVM:

| Idea | Java implementation |
|---|---|
| Typed input/output | Records as enforced return-type contracts |
| Pass by reference | Live objects in JShell, not serialized text |
| Code as action | The model writes Java, executed in JShell |
| Programmable loops | Plug-and-play `GenerationStrategy` implementations |
| Object state | Instance fields on the Agent |
| Model-callable APIs | `context()`, `events()`, `memory()` from generated code |

## License

Apache 2.0
