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

## Practical guidance

- Keep Java SDK and framework work in this repository.
- Treat the CLAD methodology repo as the authoritative home for CLAD semantics.
- Treat the CLAD agent repo as the realization of CLAD using the SDK.
- Do not treat the SDK repo as the master home for CLAD process or tooling.

This repository is intentionally split so that the framework, methodology, and implementation can evolve independently while remaining clearly connected.
