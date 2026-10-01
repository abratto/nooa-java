# SDK versioning and compatibility policy

This repository is the canonical source for the NOOA Java SDK. The versioning policy below defines how the framework artifacts are released, evolved, and consumed by downstream Java projects.

## Scope

This policy applies to the Java SDK in this repository, including the core framework runtime and its published Maven artifacts.

## SemVer baseline

The SDK follows Semantic Versioning:

- MAJOR: incompatible API or behavior changes
- MINOR: backwards-compatible new features or framework affordances
- PATCH: backwards-compatible bug fixes and security updates

The repository uses Maven versions in the form:

- `X.Y.Z` for release versions
- `X.Y.Z-SNAPSHOT` for active development on `main`

Example:

- `0.8.6-SNAPSHOT` = current development line after the `v0.8.5` release
- `v0.8.5` = current tagged release for the corresponding artifact version

## Source-of-truth model

The SDK version is owned by this repository. The Maven parent and module versions are all aligned to the same release line so that consumers receive one coherent artifact set.

The practical rule is:

- the Java SDK source lives here
- the published artifact coordinates are built here
- the release tag and changelog are aligned here

## Snapshot policy

While work is being developed on `main`, the Maven version should include `-SNAPSHOT`.

This means:

- local builds can consume the latest in-progress changes
- CI and developer workflows can test unreleased APIs
- no public release should be treated as final until the snapshot is promoted to a release tag

## Release process

The release flow should match the project’s documented contributor process:

1. Create a release branch such as `release/vX.Y.Z`.
2. Update the changelog and verify the release notes.
3. Remove the `-SNAPSHOT` suffix from the version in the Maven build.
4. Commit the release changes.
5. Merge the release branch back to `main`.
6. Create and push the annotated Git tag `vX.Y.Z`.
7. Publish the corresponding Maven artifact from the released build.
8. Return `main` to the next `-SNAPSHOT` version for further development.

The tag is the authoritative Git release marker, and the Maven version should match it.

## Compatibility expectations

This project is pre-1.0, so the compatibility bar should be treated conservatively:

- breaking changes may still happen between minor versions
- major-version adoption should be treated as a compatibility boundary
- public API changes should be called out in the changelog before release
- downstream projects should pin to a specific released version in production

A good default is:

- use a fixed version in application builds
- review changelog entries before upgrading
- prefer upgrading in small, deliberate steps when the SDK is still maturing

## Dependency consumption guidance

Downstream Java projects should consume the SDK as a regular Maven dependency using the released version from a Maven repository.

Example:

```xml
<dependency>
  <groupId>ai.nooa</groupId>
  <artifactId>nooa-core</artifactId>
  <version>0.8.5</version>
</dependency>
```

For local development, the repo can be installed into the local Maven cache with:

```bash
mvn install
```

For team or production use, publish the release to an internal or public artifact repository and consume the published version there.

## Decision rule

If a change affects the public Java SDK API or runtime behavior, it should be accompanied by:

- a changelog entry
- a version bump according to SemVer
- a clear compatibility note for downstream consumers

This keeps the SDK predictable for integration teams while preserving a clear boundary between the reusable framework and CLAD-specific application work.

## Sandbox permission guarantees

The in-process sandbox applies a static permission gate (AST-based `CodePermissionAnalyzer`) plus resolved-path file checks and deny-by-default `Permissions` rules. This is a strong static/allow-list control for trusted or reviewed code, not a hostile-code isolation boundary. Executing untrusted model output additionally requires an isolated `SandboxExecutor` backend or an equivalent external sandbox; see `docs/roadmaps/permission-hardening.md` for the staged hardening plan and guarantee levels.
