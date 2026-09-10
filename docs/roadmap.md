# SDK Roadmap: Hardening CodeAct Permission Checks

This document is the staged execution plan for hardening the CodeAct
(`CodeActStrategy`) permission layer. It is written so an agent (or
contributor) can pick up any stage in isolation. Stages are ordered by
priority and bang-for-buck; each one is self-contained and can land
independently.

> **Security detail note:** most of the rules in this document use
> globs and DENY/ASK/ALLOW levels documented in this repo. The NOOA
> repo is a Java port of NVIDIA's Object-Oriented Agents (NOOA)
> framework, not an original design of the permission system — the
> port mirrors the upstream security ergonomics where possible.

## Codebase reference map

Everything named in this plan lives under
`nooa-core/src/main/java/ai/nooa/`:

| Concern | File |
|---|---|
| REPL execution, tool allowlist, cell guards | `strategy/CodeActStrategy.java` |
| Loop/timeout config | `config/CodeActConfig.java` |
| JShell integration + current (weak) checks | `runtime/sandbox/JShellSandbox.java` |
| Sandbox state exposed to code | `runtime/sandbox/SandboxContext.java` |
| Rule engine (DENY/ASK/ALLOW) | `security/Permissions.java` |
| User-approval hook | `security/PermissionCallback.java` |
| Blocklist source of truth | `JShellSandbox.BLOCKED_PACKAGES` (L30-36) |

Known weaknesses, to be closed by the stages below:

1. `code.contains(blocked)` blocklist at `JShellSandbox.java:212` —
   trivial to bypass via string concat, wildcard imports, reflection,
   or `Class.forName("java.io.File")` split across strings.
2. Path and URL extraction uses regex over string literals
   (`isFileAccessAllowed` / `isUrlAccessAllowed`, `JShellSandbox.java:235-259`).
   Returns ALLOW if **any one** literal matches an allow rule, and
   entirely misses computed paths.
3. Symlinks are not resolved; `..` in literals is caught, but a literal
   symlink alias pointing outside the project is not.
4. `Permissions.checkCommand` exists but is effectively unused in the
   sandbox path because `ProcessBuilder` detection depends on the weak
   blocklist.
5. The `ASK` level's fallback when no `PermissionCallback` is registered
   must be verified to default to DENY.
6. Audit trail is a single `log.warn`; no structured permission event.

## Current guards (out of scope for this plan, already present)

For completeness, these loop-level guards already exist and are NOT
part of this roadmap: `maxIterations` (50), `maxRetries` (3),
`maxConsecutiveTextOnly` (3), `cellTimeoutMillis`, tool allowlist
(`executeJava` / `returnResult` only), return-type schema enforcement.

---

## Stage 1 — AST-based gate + permission audit events

**Effort:** ~2-3 days. **First landing target. No API changes.**

1. Add JavaParser (or equivalent) as a dependency; parse each candidate
   code cell pre-execution.
2. Walk the AST to resolve qualified names, imports, and string
   concatenation. Detect blocked package usage by resolved symbol, not
   substring.
3. Flag any sink call whose argument is not a literal (`Files.*`,
   `URL` constructor, `ProcessBuilder.*`, `Class.forName`,
   `getDeclaredConstructor`, `Thread` constructors) and route it to
   `PermissionCallback`/ASK rather than silently allowing.
4. Keep the existing `contains` scan as a belt-and-suspenders fallback.
5. Add a structured `PermissionDecision` event (resource, matched rule,
   level, code excerpt) emitted through `EventManager` in place of the
   bare `log.warn`.

**Acceptance:** existing tests pass; new adversarial cells (string
concat, wildcard import, `Class.forName("java.io.File")` built from
pieces) are caught.

---

## Stage 2 — Check at the sink with resolved resources

**Effort:** ~4-6 days. **The real security fix.** Can be bundled with
Stage 3. In-process, no new process boundary.

1. Filesystem shim: wrap path-producing entry points (`Path.of`,
   `Files.*`, `File` constructors) in `SandboxContext`-aware proxies
   that call `perms.checkFile()` on the **resolved canonical path**
   (symlinks expanded).
2. Rework `isFileAccessAllowed` to fail **closed over all extracted
   args** — today it grants if any one literal matches an allow rule;
   a cell touching both an allowed and a denied path must be denied.
3. Network shim: delegating socket/URL factory consulting `checkUrl`
   with the resolved host at connect time.
4. Wire `checkCommand` up so `command(glob, ...)` rules are meaningful
   even when the AST pass missed the call site.
5. ASK hardening: explicitly test that an absent/null
   `PermissionCallback` results in DENY (fail closed), and that ASK
   results are logged with the resolved resource + code excerpt.
6. Fix rule semantics: replace "last rule wins" with
   "most-specific match wins" (explicit specificity ordering), with
   tests.

**Acceptance:** adversarial corpus computing paths at runtime or
traversing symlinks is denied; legit mixed-purpose cells still pass.

---

## Stage 3 — Tighten the reachable surface

**Effort:** ~2 days. **Bundle with Stage 2.**

1. Audit the implied default for `classLoad` rules and confirm/tighten
   the allowed-class set to `java.util.*`, `java.lang.*` (minus
   blocked members), `java.util.stream.*`; anything unknown denies.
2. Gate tool invocations (`ShellTools`, `McpManager`, memory) through
   the same `Permissions` rules as raw code — currently tool objects
   injected into the harness are unguarded.
3. Replace the ad-hoc `BLOCKED_PACKAGES` string set with a single
   declarative registry mapping package → gate handler, so the
   growing-scattered-list antipattern stops.
4. Add cell-level resource budget: max output size, max allocation
   (wrap streams), alongside the existing `cellTimeoutMillis`.

**Acceptance:** reflection escalation (`Class.forName`,
`getDeclaredConstructor`) reaches classes only if the classLoad rule
allows; tool calls are permission-gated.

---

## Stage 4 — Isolation boundary (required for untrusted code, largest lift)

The SDK exposes `SandboxExecutor` so an embedding application can supply an
isolated backend without making every `nooa-core` user manage containers. The
in-process JShell implementation remains the default for trusted or reviewed code.

1. Implement a reference `SandboxExecutor` backend that runs cells in an
   isolated child process / container:
   - restricted classloader: whitelisted packages only; `sun.*` and
     `jdk.internal.*` unreachable by construction
   - CPU / memory caps inherited from the container runtime
   - `SandboxContext` and permission shims marshalled over the pipe
2. Guard with a feature flag (`nooa.sandbox.mode = inprocess | isolated`)
   so the trust profile is explicit.

**Effort:** ~2-3 weeks. **Acceptance:** a hostile cell that defeats the
Stage 1-3 patterns cannot escalate beyond its container. Applications processing
untrusted model-generated code must use this stage or provide an equivalent
external isolation backend.

---

## Stage 5 — Regression corpus + audit export

**Effort:** ~2 days. **Bundle with Stage 2.**

1. Check the bypass corpus into `tests/` as named cases, each asserting
   DENY:
   - string concatenation to construct a blocked class name
   - reflection (`Class.forName`, `getDeclaredConstructor`)
   - symlink traversal (`link -> /etc`)
   - base64 / hex / unicode-escaped paths
   - wildcard import aliasing
   - `ProcessBuilder` via `getDeclaredConstructor`
2. Route `PermissionDecision` events into ATIF export and
   `AgentSnapshot` so post-hoc review captures why each decision fired.
3. Update `docs/sdk-versioning.md` and the root `README.md` to document
   the exact guarantee level of each stage, so SDK consumers know what
   hardening they have opted into.

---

## Final exit criteria

- The full Stage 5 bypass corpus denies under Stages 1-3 combined.
- The example workflow runs unchanged while touching only `src/**`-within-project
   paths and allow-listed commands.
- No `Permissions` public API breaks: every stage extends it.

## Out of scope (documented for the record)

The loop-level guards in `CodeActConfig` (iteration cap, text-only
cap, cell timeout, tool allowlist, return-type enforcement) are working
correctly and are not touched by this plan. The known-correctness gap —
CodeAct can produce confidently *wrong but valid* code — belongs to
`ReflexionStrategy` and deterministic verification steps (see the CLAD
example's `verifyStage()` pattern), not to the permission layer.
