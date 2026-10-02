# Core Concepts

This guide explains how to think in NOOA: an agent is a Java object, methods are
capabilities, and orchestration is ordinary Java control flow. If you have not
built an agent yet, start with the [Quick Start](../README.md#quick-start).

## An agent is a Java class

Every agent extends `Agent`. Each method has one of three roles:

| Method type | How to write it | What happens |
|---|---|---|
| **Generation** | `@Generate(prompt = "...")` | The body is replaced at runtime by the configured strategy; the prompt is the instruction. |
| **Deterministic helper** | Public method with a normal body | Runs as regular Java and is exposed in `AgentDoc`. |
| **Orchestrator** | Normal method body calling other methods | Pure Java workflow — classify → route → act. |

```java
@SystemPrompt("You are a legal intake specialist.")
class LegalIntakeAgent extends Agent {

    public LegalIntakeAgent(UnifiedLLM llm) { super(llm); }

    // Deterministic helper (the LLM can call this)
    boolean isEmergency(String message) {
        return message.toLowerCase().contains("urgent");
    }

    // Generation method (the LLM completes this)
    @Generate(prompt = "Classify the matter and return its topic, urgency, and a one-sentence summary.")
    @Strategy(PredictStrategy.class)
    public Classification classify(String message) {
        throw new UnsupportedOperationException();
    }

    // Orchestrator (pure Java — no LLM calls itself)
    public String handle(String message) {
        var classification = classify(message);
        if (classification.urgency() == Priority.EMERGENCY) {
            context().put("priority", "EMERGENCY — respond in under 30 seconds");
        }
        return "classified: " + classification.topic();
    }
}

enum Priority { LOW, MEDIUM, HIGH, EMERGENCY }
record Classification(String topic, Priority urgency, String summary) {}
```

Key design rule: **one method = one LLM task**. Do not make a single method do
classification *and* implementation; split into classify → route → act.
Orchestrators stay pure Java.

## Developer mental model

- Deterministic Java handles I/O, validation, orchestration, and state.
- `@Generate` methods define the model-powered capabilities, and their
  `prompt = "..."` is the runtime instruction.
- The method signature (name, parameters, return type) and the current agent
  context bind the data and the structured-output contract.
- Helper methods and fields are the agent's tools, memory, and state.

The framework feels like ordinary Java, with a runtime that wraps each generated
method in a disciplined prompt-and-tool loop.

### A practical pattern

```java
@SystemPrompt("You are a news summarizer.")
class NewsAgent extends Agent {
    public NewsAgent(UnifiedLLM llm) { super(llm); }

    // Deterministic Java: fetch and normalize inputs
    String fetchLatestHeadline() {
        return "Acme launches a battery chemistry that cuts charge time by 40%";
    }

    // Model-powered capability
    @Generate(prompt = "Summarize the provided article in two concise sentences.")
    public String summarizeNews(String articleText) {
        throw new UnsupportedOperationException("Generated at runtime");
    }

    // Pure Java orchestrator: gather data, then delegate to the model
    public String summarizeCurrentNews() {
        var article = fetchLatestHeadline();
        return summarizeNews(article);
    }
}
```

The method body is not the instruction — the `@Generate(prompt = "...")` is. The
method's name, parameters, and return type still matter: arguments are bound into
the call as typed inputs, and the return type is enforced as the structured output
contract.

### One agent, one job

Keep an agent focused on a single business capability — summarize articles,
classify tickets, extract fields, plan the next action, or answer from a known
domain model. Avoid one giant agent that does everything; split the work into
small capabilities and orchestrate them in Java.

| Concern | Place it here |
|---|---|
| HTTP calls, file access, DB logic, validation | regular Java methods |
| classification, summarization, extraction, planning | `@Generate` methods |
| branching and sequencing | Java orchestrator methods |
| persistent memory and shared state | agent fields, context blocks, `MemoryStore` |
| model/tool instructions | `@SystemPrompt`, `@Generate(prompt = "...")`, method naming, strategy selection |

## Structured output without provider lock-in

Ask the model for JSON and parse it into a Java record using `PredictStrategy`:

```java
record NewsBrief(String headline, String impact, String summary) {}

class NewsAgent extends Agent {
    public NewsAgent(UnifiedLLM llm) { super(llm); }

    @Generate(prompt = "Extract the article into a brief with headline, impact, and summary fields.")
    @Strategy(PredictStrategy.class)
    public NewsBrief extractBrief(String article) {
        throw new UnsupportedOperationException("Generated at runtime");
    }
}
```

The model call is mediated by `UnifiedLLM`, while your Java type is the contract.
If a model returns invalid or partial JSON the runtime retries with a corrective
diagnostic instead of accepting bad output silently. The same code works with
OpenAI-compatible endpoints, local Ollama, and other backends.

### Simple example: a periodic news agent

```java
class PeriodicNewsAgent extends Agent {
    public PeriodicNewsAgent(UnifiedLLM llm) { super(llm); }

    String fetchLatestNews() {
        return "Acme unveiled a battery chemistry that cuts charge time by 40%.";
    }

    @Generate(prompt = "Summarize the article into a brief with headline, impact, and summary fields.")
    @Strategy(PredictStrategy.class)
    public NewsBrief summarize(String article) {
        throw new UnsupportedOperationException("Generated at runtime");
    }

    public void pollOnce() {
        var brief = summarize(fetchLatestNews());
        System.out.println(brief.summary());
    }
}
```

## State-machine agents

Many agent systems are described as graphs or state machines. In NOOA the state
machine is expressed in Java, not a separate workflow DSL, and you can pick the
Java idiom that fits the problem.

| Idiom | Use when | Trade-off |
|---|---|---|
| **Enum with abstract transition methods** | States carry no data and the FSM is small (gates, toggles, pipelines) | Zero boilerplate and one-file locality, but states cannot hold payloads |
| **Sealed interfaces + records + pattern-matching `switch`** | Real workflows where states carry data and transitions have guards | More types, but exhaustive compile-time checks and payloads per state |

### Enum style (payload-free)

```java
public enum ReleasePhase {
    PLANNED  { public ReleasePhase advance(ReleaseEvent e) { return switch (e) {
        case START_BUILD -> BUILDING; default -> illegal(this, e); } } },
    BUILDING { public ReleasePhase advance(ReleaseEvent e) { return switch (e) {
        case TESTS_PASS -> TESTING; case TESTS_FAIL -> REJECTED; default -> illegal(this, e); } } },
    // ... APPROVED, DEPLOYED, REJECTED, ROLLED_BACK
    ;
    public abstract ReleasePhase advance(ReleaseEvent event);
}
```

### Sealed-types style (states carry data)

```java
public sealed interface IncidentState permits Detected, Triaged, /* ... */ Escalated {
    record Detected(IncidentReport report) implements IncidentState {}
    record Triaged(Severity severity, String summary) implements IncidentState {}
    record Mitigating(RemediationPlan plan, String approver, boolean verified) implements IncidentState {}
    // ...
}

static Transition transition(IncidentState current, IncidentEvent event) {
    return switch (current) {
        case Mitigating state -> switch (event) {
            case Verify e when e.healthy() -> accepted(new Mitigating(state.plan(), state.approver(), true));
            case Resolve e when state.verified() -> accepted(new Resolved(e.postmortem()));
            case Resolve e -> reject(state, event);          // must verify first
            default -> reject(state, event);
        };
        // ... every other state (the compiler enforces exhaustiveness)
    };
}
```

The workflow is not a separate engine; it is the agent's Java control flow plus
the runtime-provided LLM capabilities:

- **Java owns the transitions.** The agent's orchestrator fires events; illegal
  transitions are returned as data (`Transition.Rejected`) and surfaced as a
  typed error only at the boundary.
- **The model produces typed payloads.** `@Generate` methods return records
  (`Severity`, `Hypothesis`, `RemediationPlan`, `Postmortem`) via `Predict`, or
  slice telemetry with helper methods via CodeAct.
- **The model always sees the current state** through a dynamic context block
  (`context().putDynamic("incident_state", "self.status()")`).
- **Humans stay in the loop** for consequential steps via an `ApprovalGate`.

Two runnable examples demonstrate both idioms:

- `ai.nooa.examples.incident` — the sealed-types incident-response agent
  (`IncidentState`, `IncidentEvent`, `IncidentStateMachine`, `IncidentAgent`),
  with scenarios, human approval, and an `EvalRunner` test.
- `ai.nooa.examples.release` — the enum-style release gate (`ReleasePhase`,
  `ReleaseGateAgent`).


## Complete walkthrough: a research agent

```java
@SystemPrompt("You research topics and write structured reports.")
class ResearchAgent extends Agent {

    record Report(String title, String summary, List<String> keyFindings) {}

    public ResearchAgent(UnifiedLLM llm) { super(llm); }

    // The LLM can use this tool from generated code
    String searchWeb(String query) {
        return "Results for: " + query; // real impl would call an API
    }

    // Phase 1: gather information (CodeActStrategy — default)
    @Generate(prompt = "Gather a list of key facts about the topic using the available helper methods.")
    public List<String> gatherFacts(String topic) {
        throw new UnsupportedOperationException();
    }

    // Phase 2: write a structured report (PredictStrategy)
    @Generate(prompt = "Write a structured report from the topic and the gathered facts.")
    @Strategy(PredictStrategy.class)
    public Report writeReport(String topic, List<String> facts) {
        throw new UnsupportedOperationException();
    }

    // Orchestrator — pure Java
    public Report research(String topic) {
        context().put("topic", topic);
        context().putDynamic("progress", "self.getProgress()");

        var facts = gatherFacts(topic);
        return writeReport(topic, facts);
    }

    public String getProgress() {
        return "Gathered facts: analyzing " + eventManager().size() + " events";
    }
}

// Usage:
var agent = AgentFactory.create(ResearchAgent.class, llm);
Report report = agent.research("AI agent frameworks");
```

## Next steps

- [agent-building-guide.md](agent-building-guide.md) — choosing runtime shapes,
  strategies, sessions, skills, and middleware.
- [prompt-engineering.md](prompt-engineering.md) — how prompts are assembled and
  how to design them.
- [architecture.md](architecture.md) — the runtime pipeline.
