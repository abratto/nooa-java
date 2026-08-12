# CLAD submodule and repository boundaries

This repository contains the Java SDK for NOOA, while the CLAD methodology and agent tooling live in a separate repository that is linked here as a Git submodule.

## What this means

- The root repository is the Java implementation of the NOOA SDK.
- The [clad](../clad) directory is a separate Git repository checked out inside this repository.
- The parent repository tracks the submodule by commit ID, not by copying the CLAD files directly.
- The CLAD repository can evolve independently, and this repository can choose when to pull a newer CLAD commit.

This keeps the Java SDK and the CLAD methodology in sync without forcing all CLAD changes to be merged into the Java SDK as one monorepo.

## Why it is structured this way

The Java SDK here and the CLAD repo serve related but distinct purposes:

- The Java SDK provides runtime APIs, libraries, and agent implementations for Java users.
- The CLAD repo provides the methodology, workflow scaffolding, quality gates, and agent-oriented development process.

When the CLAD repo improves, this repository can adopt those upstream changes intentionally by updating the submodule pointer.

## Normal workflow

From the root of this repository:

```bash
git submodule update --remote clad
```

This updates the checked-out CLAD submodule to the latest commit from its configured remote. After that, you can review the change and commit the updated submodule pointer in the parent repo:

```bash
git add clad
git commit -m "Update CLAD submodule to latest"
```

## Important rule

A dirty or changed submodule should be treated as a separate repository state. Do not assume that editing the submodule is a normal file edit in the root repo.

If the submodule is modified, the parent repository will report it as a different Git state until the submodule pointer is intentionally updated and committed.

## Practical guidance

- Keep the Java SDK upgrade work in the root repository.
- Treat the CLAD submodule as an independent repo with its own changes and version history.
- Only update the submodule when you intentionally want the parent repo to track a newer CLAD revision.

This repo is intentionally split so that methodology and implementation can evolve at different rates while still being linked together when needed.
