# Prompt Engineering in NOOA Java

This guide explains how NOOA turns an ordinary Java method call into the messages
sent to an LLM. The important boundary is simple:

- Java owns facts, state, I/O, validation, and orchestration.
- `@SystemPrompt` defines the agent-level role and rules.
- `@Generate(prompt = "...")` defines the task instruction for one capability.
- Method names and signatures describe the available API in the model-facing
    agent metadata.
- Method arguments provide the concrete input for that invocation.
- Context blocks and events carry durable and conversational state.
- The selected strategy adds execution or output-format instructions.

The runtime does not use Java Javadoc as a runtime prompt. Use the annotation
values and Java-owned data paths described below.

## Why Method Names Matter

Method names are part of the prompt, but they enter through the agent API
metadata rather than by being prepended to every task instruction. The protected
`<self>` context block contains `AgentDoc`, which lists visible methods with:

- the method name
- parameter types and names
- the return type
- a `[generated]` marker for `@Generate` methods
- the stored `@Generate(prompt = "...")` text, when present

For example:

```java
@Generate(prompt = "Classify the support ticket and return its priority.")
public Priority classifySupportTicket(String ticketText) {
     throw new UnsupportedOperationException();
}
```

The model-facing API includes metadata similar to:

```text
Methods:
  [generated] Priority classifySupportTicket(String ticketText)
     — Classify the support ticket and return its priority.
```

This metadata helps CodeAct choose and call the right capability. Prefer names
that describe the action and object clearly, such as `classifySupportTicket`,
`summarizeIncident`, or `findOpenOrders`. Avoid names like `run`, `process`, or
`handle` when the method has a more specific meaning.

There are two separate paths to keep in mind:

1. With an explicit `@Generate(prompt = "...")`, the active task text is the
    annotation value followed by the rendered inputs. The method name is still
    available through `AgentDoc`.
2. Without an explicit prompt, NOOA falls back to an instruction derived from
    the declaring class, method name, and parameter types. This fallback makes a
    descriptive method name even more important, but an explicit prompt is still
    preferable for production behavior.

The same principle applies to deterministic helper methods. Their names and
signatures are visible in `AgentDoc`, so use names that tell the model what fact
or action the helper provides. Keep helpers narrow and make their return values
easy to interpret.

## The Assembly Pipeline

For a generated method call, the runtime follows this sequence:

1. `AgentFactory.create(...)` instruments the `@Generate` method and stores its
   `prompt` value.
2. The invocation becomes a `CurrentCall` containing the method, named arguments,
   return type, and runtime prompt.
3. The runtime appends a `Task` event containing the prompt and rendered inputs.
4. Before each LLM request, context blocks are rendered into one system message.
5. Events are converted into user and assistant messages and appended after the
   system message.
6. The strategy may add a strategy instruction, tools, schema information, or
   retry feedback.
7. Middleware can make a final request transformation before the LLM client is
   called.

Conceptually, the request is:

```text
system message:
  protected context blocks
  user context blocks
  strategy supplement, when applicable

conversation messages:
  prior task, output, execution, feedback, error, and summary events
  current generated-method task with named inputs

request metadata:
  tools
  declared output type
  sampling overrides
```

The exact final request can be inspected with `PromptBuilt` events or
`PromptRecorder`.

## Agent-Level Instructions

Use `@SystemPrompt` for stable role, safety, domain, and quality rules that
should apply to every generated method on the agent.

```java
@SystemPrompt("""
    You are a support operations assistant.
    Use Java-provided inventory facts as the source of truth.
    Never invent stock levels, prices, or order status.
    Keep answers concise and state the next action clearly.
    """)
public class SupportAgent extends Agent {
    public SupportAgent(UnifiedLLM llm) {
        super(llm);
    }

    @Generate(prompt = "Check the requested order against the inventory facts.")
    public String checkOrder(String item, int quantity) {
        throw new UnsupportedOperationException();
    }
}
```

If no `@SystemPrompt` is present, the agent class name is used as the fallback
system prompt. `@SystemPrompt` supports expressions resolved against the agent,
for example `{self.currentProject()}` or `{type.name}`. Prefer context blocks
for values that change during the agent's lifetime.

## Capability Instructions

Use `@Generate(prompt = "...")` for the task-specific instruction. Keep it
narrow: state the task, important constraints, and the desired answer behavior.
Use the method name to identify the capability and the signature to express its
typed contract; the annotation should not have to repeat either mechanically.

```java
@Generate(prompt = """
    Summarize the article for a technical project manager.
    Mention the main change, practical impact, and one uncertainty.
    Do not invent facts or citations.
    """)
public String summarize(String articleText) {
    throw new UnsupportedOperationException();
}
```

At runtime, the call task is approximately:

```text
Summarize the article for a technical project manager.
Mention the main change, practical impact, and one uncertainty.
Do not invent facts or citations.

Inputs:
- articleText: [the argument value, bounded by the runtime argument limit]
```

The argument is rendered automatically. Do not duplicate a large argument inside
the annotation string. Doing so wastes context and can move untrusted content
into the instruction text. Use the argument as data and the annotation as policy.

When an annotation has no prompt, NOOA falls back to an instruction derived from
the declaring class, method name, and parameter types. That fallback is useful for
prototyping, but an explicit prompt is better for production behavior.

## Context Blocks

Context blocks are rendered inside the system message. Protected blocks are
managed by the framework and appear first:

- `system_prompt` - the resolved `@SystemPrompt` value
- `self` - `AgentDoc` for the model-visible agent API
- `state` - visible instance field values

User blocks follow in insertion order. Each block is wrapped in a named tag.
For example:

```java
context().put("project_status", "2 of 5 tasks complete");
context().putDynamic("current_focus", "self.focusSummary()");
```

Produces a system-message section like:

```text
<project_status>
2 of 5 tasks complete
</project_status>

<current_focus>
Fix the authentication timeout
</current_focus>
```

Use a static block for a value that is known when it is assigned. Use a dynamic
block when the value must be re-evaluated before each LLM turn.

```java
context().put("tenant_policy", policyText);       // cached value
context().putDynamic("project_status", "self.formatStatus()");
```

Context is not a replacement for arguments. Put durable shared state and current
agent state in context; pass the specific object being acted on as a method
argument.

## Events and Conversation History

The runtime records tasks, model outputs, execution output, feedback, errors, and
summaries as events. `EventManager.toMessages()` renders the conversational subset:

| Event | Message role/content |
|---|---|
| `Task` | user message containing the task or input |
| `LLMOutput` | assistant message containing model text |
| `ExecutionOutput` | user message containing stdout or an error |
| `ErrorEvent` | user message containing the error |
| `Feedback` | user message containing corrective feedback |
| `Summary` | assistant message containing a compacted summary |

Lifecycle events such as `BeforeTurn` and `LLMCallStart` are tracked but are not
sent as ordinary conversation messages. This distinction matters when debugging:
an event can exist for tracing without becoming prompt text.

A normal generated call therefore adds a new task to the existing conversation.
Repeated calls on the same agent can see earlier rendered events unless a
summarizer, snapshot restore, or explicit event operation changes that history.

## Strategy-Specific Prompting

The strategy changes the final request beyond the common agent context.

### CodeAct (default)

`CodeActStrategy` adds execution instructions and exposes tools such as
`executeJava` and `returnResult`. The generated code can use the live agent,
context, events, and Java helpers:

```java
@Generate(prompt = "Calculate the total and explain the result briefly.")
public int calculateTotal(List<Integer> values) {
    throw new UnsupportedOperationException();
}
```

For this method, the model receives the task, the input values, the agent context,
and CodeAct instructions explaining how to execute Java and return the declared
`int` result. The model should compute from the supplied values rather than asking
the user to repeat them.

### Predict

`PredictStrategy` makes a single structured-output request. The declared return
record becomes the output contract, and failed parsing adds retry feedback to the
next attempt.

```java
record Classification(String category, double confidence) {}

@Generate(prompt = "Classify the ticket using the stated category definitions.")
@Strategy(PredictStrategy.class)
public Classification classify(String ticket) {
    throw new UnsupportedOperationException();
}
```

Use the prompt to define semantic categories and boundaries. The record defines
field names and types. Do not ask the prompt to redefine the JSON shape that the
return type already establishes.

### Reflexion

`ReflexionStrategy` adds a review-and-correction loop around the task. The prompt
should describe the desired result and the quality criteria; the strategy handles
asking for a review rather than requiring the prompt to narrate every loop step.

## A Complete Grounded Example

This pattern keeps facts in Java, passes the immediate input as an argument, and
uses context only for state that should persist across turns:

```java
@SystemPrompt("""
    You are a release assistant.
    Use repository facts supplied by Java as authoritative.
    Never claim a command ran unless its output is present.
    """)
public final class ReleaseAgent extends Agent {
    private final String branch;

    public ReleaseAgent(UnifiedLLM llm, String branch) {
        super(llm);
        this.branch = branch;
        context().putDynamic("release_state", "self.releaseState()");
    }

    public String releaseState() {
        return "branch=" + branch + ", status=awaiting-review";
    }

    @Generate(prompt = """
        Review the supplied build output.
        Report failures first, then the smallest practical next step.
        Treat missing output as unknown, not as success.
        """)
    public String reviewBuild(String buildOutput) {
        throw new UnsupportedOperationException();
    }

    public String reviewLatestBuild() {
        String output = loadBuildOutputFromJava();
        return reviewBuild(output);
    }

    private String loadBuildOutputFromJava() {
        return "Tests: 42 passed; packaging: success";
    }
}
```

The design separates three kinds of information:

- stable role and policy in `@SystemPrompt`
- changing agent state in `release_state`
- the concrete build result in `reviewBuild(buildOutput)`

That separation makes prompts easier to inspect, less repetitive, and safer when
inputs contain untrusted or very large text.

## Prompt Design Checklist

Before adding or changing a generated method, ask:

1. Is this instruction stable for the whole agent (`@SystemPrompt`) or specific to
   one capability (`@Generate(prompt)`)?
2. Is the immediate input passed as a typed method argument rather than copied into
   an instruction string?
3. Are durable facts and changing state represented with the right context block
   type?
4. Does the return type express the contract, especially for structured output?
5. Does the prompt say what to do with uncertainty, missing data, or conflicting
   facts?
6. Does the prompt identify the source of truth for the task?
7. Is the instruction short enough that the model can distinguish policy, data, and
   requested output?
8. Have you inspected a real `PromptBuilt` event for a representative call?

## Inspecting the Actual Request

Enable prompt events and attach a recorder when tuning a prompt:

```bash
export NOOA_LOG_PROMPTS=true
export NOOA_LOG_PROMPTS_RAW=false
```

```java
var recorder = PromptRecorder.attach(agent, Path.of("prompts.jsonl"));
try {
    agent.reviewLatestBuild();
} finally {
    recorder.close();
}
```

By default, prompt content is redacted for common credential patterns. Raw prompt
logging should be limited to trusted local debugging because arguments, context,
and event history may contain sensitive data.

Use the captured request to check the actual system message, event messages, tool
names, output model, and sampling parameters. Do not infer prompt assembly only
from the Java source; middleware, truncation, retries, and dynamic blocks can
change the final request.

## Limits and Safety

Prompt assembly is not a security boundary. `@Hidden` controls model-facing
`AgentDoc` and state rendering; it does not erase Java values or isolate code
execution. Use permissions and an external process or container boundary when
untrusted generated code must be isolated.

Arguments and context are bounded by runtime configuration where applicable. Large
inputs should be normalized or summarized in deterministic Java before being sent
to a generated method. Keep credentials out of prompts, context blocks, snapshots,
and trace files.
