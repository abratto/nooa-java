# Integrations

How NOOA connects to models and to external capabilities: providers, memory,
MCP tools, shell access, and task tracking.

## Providers

OpenAI, Anthropic, OpenRouter, DeepInfra, Groq, local Ollama, and any
OpenAI-compatible endpoint — all with automatic retry on 429/5xx.

```java
// OpenAI
var llm = UnifiedLLM.create(UnifiedLLM.openAI(key, "gpt-4o").build());

// Anthropic
var llm = UnifiedLLM.create(UnifiedLLM.anthropic(key, "claude-sonnet-4-5").build());

// OpenRouter
var llm = UnifiedLLM.create(
    UnifiedLLM.openRouter(key, "anthropic/claude-sonnet-4-5").build());

// DeepInfra (hosted open-source models)
var llm = UnifiedLLM.create(
    UnifiedLLM.deepInfra(key, "meta-llama/Llama-4-Maverick-17B-128E").build());

// Groq (fast inference)
var llm = UnifiedLLM.create(
    UnifiedLLM.groq(key, "llama-4-maverick-17b-128e").build());

// Local Ollama — no API key needed
var llm = UnifiedLLM.create(UnifiedLLM.ollama("llama3.2").build());

// Any OpenAI-compatible endpoint
var llm = UnifiedLLM.create(
    UnifiedLLM.custom("https://my-proxy.example.com/v1", key, "my-model").build());

// Retry configuration
var llm = UnifiedLLM.create(
    UnifiedLLM.openAI(key, "gpt-4o").maxRetries(5).build());
```

## Long-term memory

Agents accumulate knowledge across sessions in a human-readable SQLite file:

```java
var store = new MemoryStore("agent_memory.db");
store.scheduleReflection(3600); // prune/merge every hour

// Attach to an agent
var memory = new MemorySkill(agent, store);

// Write from generated code or orchestrators:
memory.write("fact", "User prefers dark mode", 0.8, List.of("preference", "ui"));
memory.write("episode", "Fixed auth bug in login flow", 0.9, List.of("auth", "bugfix"));

// Recall relevant memories:
var relevant = memory.recall(List.of("auth", "bugfix"), 5);

// Query by type:
var preferences = memory.query("preference", null, 10);

// Link records:
memory.relate(record1.id().toString(), "contradicts", record2.id().toString());

// Background reflection merges duplicates, distills episodes, and prunes stale records.
```

Use context blocks for short-lived prompt facts; use `MemoryStore` for durable,
searchable knowledge that should outlive a single agent call.

## MCP integration

Connect to Model Context Protocol servers for additional tools:

```java
var mcp = new McpManager()
    .connectStdio("filesystem", List.of("npx", "-y",
        "@modelcontextprotocol/server-filesystem", "/workspace"))
    .connectStdio("github", List.of("npx", "-y",
        "@modelcontextprotocol/server-github"));

// Discovered tools are available to the LLM
var tools = mcp.allTools(); // pass to generate() or CodeActStrategy

// Call from generated code or orchestrators:
mcp.callTool("filesystem", "read_file", Map.of("path", "/workspace/src/Main.java"));
```

Use stdio for local child-process servers and SSE for network services, and add
application-level lifecycle, timeout, authentication, and reconnect policy around
the transport.

## Shell access

`ShellTools` runs commands and reads/writes files inside a workspace, governed by
`Permissions` (see [security.md](security.md)):

```java
var shell = new ShellTools(Path.of("/tmp/workspace"));
shell.run("git diff HEAD~1");
String content = shell.read("src/Main.java");
shell.writeFile("output.txt", result);
String preview = shell.view("large_file.log"); // auto-truncated
```

Commands are executed as `/bin/bash -c <command>` in the workspace directory with
a timeout; file access is confined to the workspace, and command-level permissions
apply.

## Task tracking

A model-visible todo list for multi-step work:

```java
var todos = new TodoManager();
var task = todos.add("Implement login", "high");
todos.markInProgress(task.id());
todos.markCompleted(task.id());
System.out.println(todos.showActive());
```

## Related

- [agent-building-guide.md](agent-building-guide.md) — choosing runtime shapes and
  when to use these services.
- [package-reference.md](package-reference.md) — package map and configuration.
