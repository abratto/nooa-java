# NOOA CLAD — Contract-Led, Artefact-Driven Agent

A CLI tool and agent that implements the [CLAD methodology](../clad/README.md)
using the NOOA Java SDK.

## Quick Start

```bash
# Build
cd nooa-java && mvn install -DskipTests -pl nooa-clad -am

# Create a project
java -jar nooa-clad/target/nooa-clad-0.1.0-SNAPSHOT.jar init my-library

# Run through CLAD stages
cd my-library
java -jar ../nooa-clad/target/nooa-clad-0.1.0-SNAPSHOT.jar run
```

## Commands

### `nooa clad init <project-name>`

Creates a new CLAD project with:
- `methodology/` — CLAD methodology docs (from bundled submodule)
- `features/_system/` — system-level stages (00_actor-goal)
- `pom.xml` — Maven project with `nooa-clad-runtime` dependency
- `README.md` — getting started guide

Options:
- `--dir <path>` — target directory (default: current directory)

### `nooa clad run`

Executes CLAD stages in order. For each uncompleted stage:
1. Reads `CONTEXT.md` contract
2. Produces output artefacts using the LLM
3. Runs `Verify` checklist
4. Writes `.gate-receipt.json` on success
5. Stops at human gates for review

Options:
- `--auto` — auto-advance through non-gate stages
- `--stage <id>` — run only the specified stage (e.g., `01_usecase`)
- `--feature <dir>` — focus on a specific feature directory
- `--model <name>` — LLM model (default: `gpt-4o`)
- `--dir <path>` — project directory (default: current)

Environment:
- `OPENAI_API_KEY` — required

## How It Works

The CLAD agent is a NOOA `Agent` subclass with three LLM-powered methods:

| Method | Strategy | What it does |
|---|---|---|
| `parseContract()` | PredictStrategy | Extracts structured StageContract from CONTEXT.md |
| `executeProcess()` | CodeActStrategy | Generates output files per contract instructions |
| `presentGate()` | @Generate | Summarizes artefacts for human review |

The orchestrator (`executeStage()`) is pure Java — it calls these methods
in sequence, runs deterministic verification, and manages gate progression.

## Project Structure After `init`

```
my-library/
├── methodology/              # CLAD docs (from submodule)
│   ├── core/CLAD.md
│   ├── architecture/CONCEPTS.md
│   └── implementation/STAGES.md
├── features/
│   └── _system/
│       └── stages/
│           └── 00_actor-goal/CONTEXT.md
├── pom.xml
├── README.md
└── CLAUDE.md
```

## Generated Code

When the agent reaches Stage 04 (implementation), it generates Java classes
that extend the CLAD runtime engine:

```java
import ai.nooa.clad.runtime.engine.ConceptAgent;
import ai.nooa.clad.runtime.engine.ActionRecord;

class UserConcept extends ConceptAgent {
    public UserConcept(ConceptContext ctx) { super(ctx); }

    protected void processInvocation(ActionRecord inv) {
        switch (inv.actionName()) {
            case "lookupByUsername" -> {
                var user = findUser(inv.bindings().get("username"));
                if (user != null) {
                    complete(inv, Map.of("status", "FOUND", "userId", user.id));
                } else {
                    refuse(inv, "User not found");
                }
            }
        }
    }
}
```

## Sync with CLAD Repo

The `clad/` submodule pins a specific commit of the methodology docs.
To update:

```bash
cd clad && git pull origin main && cd ..
git add clad && git commit -m "Update CLAD to latest"
```

## License

Apache 2.0 — see [LICENSE](../LICENSE)
