# Security and Permissions

Agents can read files, call tools, and execute model-generated Java. This page
covers the permission model, the sandbox gate, and the guarantee levels you can
rely on. For the staged hardening plan, see
[roadmaps/permission-hardening.md](roadmaps/permission-hardening.md).

## Permissions

`Permissions` is a deny-by-default policy for files, commands, URLs, and class
loading. You add ALLOW or ASK rules; everything unmatched is denied.

```java
var perms = new Permissions()
    .file("src/**", Permissions.Level.ALLOW)     // read project source
    .file("/etc/**", Permissions.Level.DENY)      // never touch system config
    .command("git *", Permissions.Level.ALLOW)    // git commands allowed
    .command("rm *", Permissions.Level.ASK)       // destructive — ask the user
    .url("https://api.example.com/**", Permissions.Level.ALLOW)
    .url("*", Permissions.Level.DENY);            // block all other URLs

agent.setPermissions(perms);
```

- Rules use globs. `**` matches across path separators; `*` matches within a
  path segment (files) or anything (commands/URLs).
- The **most-specific** matching rule wins, regardless of the order rules were
  added.
- File checks **resolve symlinks**, so a link inside an allowed root cannot be
  used to escape it.
- `ASK` requires a `PermissionCallback`. Without one, the request is **denied**
  (fail closed).

```java
agent.setPermissionCallback((resource, detail) -> {
    System.out.println("Allow " + resource + ": " + detail + "?");
    return System.console().readLine().trim().equalsIgnoreCase("y");
});
```

## The sandbox permission gate

Before a CodeAct cell executes, `CodePermissionAnalyzer` parses it and resolves
restricted API usage by AST node (imports, qualified types, object creations,
reflection and sink calls, string literals) rather than substring matching, then
decides ALLOW / ASK / DENY against the agent's `Permissions`:

- restricted file, URL, command, and class-load APIs are denied unless an
  explicit rule allows them;
- wildcard imports of restricted packages, `Class.forName` built from pieces,
  reflective methods, and non-literal arguments to sensitive sinks are caught;
- dynamic resources route to the agent's `PermissionCallback` and are denied
  when no callback is registered;
- every restricted resource in a cell must be allowed (fail closed);
- each decision is emitted as a structured `Event.PermissionDecision` for audit,
  and exported through ATIF and snapshots.

The same gate governs `ShellTools`: `run(...)` checks `checkCommand`, and a
command at the `ASK` level with no callback is denied.

```java
var shell = new ShellTools(Path.of("/tmp/workspace"), permissions, permissionCallback);
```

## Guarantee level

The in-process gate is a strong **static/allow-list control** for trusted or
reviewed code. It is **not** a hostile-code isolation boundary: CodeAct executes
in the same JVM as the agent.

- For trusted or reviewed code, the permission gate plus timeouts and iteration
  limits are appropriate.
- For **untrusted model output**, also use an isolated `SandboxExecutor` backend
  (or an equivalent external sandbox such as a container). `SandboxExecutor` is
  the pluggable boundary; the in-process JShell implementation is the default.

`AgentConfig.withSandboxExecutor(...)` injects an executor factory for a worker
process, container, VM, or remote sandbox.

## Visibility is not security

`@Hidden` controls what appears in `AgentDoc` (the model-facing API description)
and in context state rendering. It does **not** change Java access control or
provide a security boundary. To restrict what generated code can actually do, use
`Permissions`, timeouts, iteration limits, and an isolated executor.

## Related

- [roadmaps/permission-hardening.md](roadmaps/permission-hardening.md) — staged
  hardening plan, including the isolation backend.
- [package-reference.md](package-reference.md#security-sensitive-packages) —
  package-level notes.
