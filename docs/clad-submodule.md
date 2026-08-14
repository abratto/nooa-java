# CLAD repository boundaries

This repository contains the Java SDK for NOOA. The CLAD process and methodology live in a separate repository, and the concrete CLAD agent runtime lives in a dedicated agent repo.

## What this means

- The root repository is the Java implementation of the NOOA SDK.
- The CLAD methodology repo is the source of truth for process, artefact rules, and stage semantics.
- The CLAD agent repo is the concrete runtime realization of that methodology using the NOOA SDK.
- The SDK repo intentionally does not own the CLAD process or its agent implementation as a built-in component.

## Why this is structured this way

The Java SDK here and the CLAD repos serve related but distinct purposes:

- The Java SDK provides runtime APIs, libraries, and agent-building blocks for Java users.
- The CLAD repo provides the methodology, workflows, and review discipline.
- The CLAD agent repo provides the concrete agent implementation of that methodology.

This split keeps the framework general-purpose while making the CLAD-specific workflow a distinct application and implementation concern.

## SDK maintenance and consumption model

This repository is the maintenance home for the Java SDK itself. It contains the
source for the framework runtime, agent primitives, LLM integrations, and the
Maven module structure used to build the library.

The maintenance contract is:

- the SDK source lives here
- versioning and framework compatibility are managed here
- downstream Java projects consume the library through Maven coordinates from this repo
- CLAD-specific process guidance and implementation remain in the CLAD repos

For external consumers, the expected flow is:

1. build and publish the SDK artifacts from this repo
2. declare the library as a normal Maven dependency in downstream projects
3. keep the CLAD methodology and concrete CLAD agent separate from the generic SDK lifecycle

This avoids conflating a reusable framework library with a single application workflow.

## Practical guidance

- Keep Java SDK and framework work in this repository.
- Treat the CLAD methodology repo as the authoritative home for CLAD semantics.
- Treat the CLAD agent repo as the realization of CLAD using the SDK.
- Do not treat the SDK repo as the master home for CLAD process or tooling.

This repository is intentionally split so that the framework, methodology, and implementation can evolve independently while remaining clearly connected.
