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

## A workflow is the agent's control flow

Many agent systems are described as graphs or state machines. In NOOA the state
machine is expressed in Java, not a separate workflow DSL.

```java
enum WorkflowState { INTAKE, VALIDATE, ROUTE, INVESTIGATE, PLAN, EXECUTE, REVIEW, ESCALATED }

@SystemPrompt("You are a support and operations agent for customer requests.")
class SupportWorkflowAgent extends Agent {
    private WorkflowState state = WorkflowState.INTAKE;
    private final List<String> notes = new ArrayList<>();

    public SupportWorkflowAgent(UnifiedLLM llm) { super(llm); }

    // Deterministic Java transitions
    void markValidated() { state = WorkflowState.ROUTE; }
    void markNeedsReview() { state = WorkflowState.INVESTIGATE; }
    void markEscalated() { state = WorkflowState.ESCALATED; }

    // Model-powered steps
    @Generate(prompt = "Validate the request. Reply 'ok' or 'needs_review' plus a one-line reason.")
    public String validateCase(String request) { throw new UnsupportedOperationException(); }

    @Generate(prompt = "Investigate the request and list the relevant findings.")
    public String investigateIssue(String request) { throw new UnsupportedOperationException(); }

    @Generate(prompt = "Create a short, concrete action plan from the issue summary.")
    public String createPlan(String issueSummary) { throw new UnsupportedOperationException(); }

    @Generate(prompt = "Execute the plan and describe the outcome in one or two sentences.")
    public String executeAction(String plan) { throw new UnsupportedOperationException(); }

    // Orchestrator: this is the workflow
    public String handleRequest(String request) {
        state = WorkflowState.INTAKE;
        notes.add(request);

        var validation = validateCase(request);
        if (validation.contains("needs_review")) {
            state = WorkflowState.INVESTIGATE;
            var plan = createPlan(investigateIssue(request));
            state = WorkflowState.PLAN;
            return executeAction(plan);
        }
        state = WorkflowState.ROUTE;
        return validation;
    }
}
```

The workflow is not a separate engine; it is the agent's Java control flow plus
the runtime-provided LLM capabilities. It can loop, escalate, and revisit states:

- validate → loop back to investigate if information is missing
- plan → execute → review → loop if the result is incomplete
- escalate when the agent hits a blocker or risk threshold

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
