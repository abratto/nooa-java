# Contributing

## Development

Trunk-based development on `main`. All changes go through short-lived branches:

```
main ────────────────────────────────────● v0.2.0
  │
  ├── feature/my-feature ──●── merge ──┘
  ├── fix/bug-fix ──●── merge
  └── chore/cleanup ──●── merge
```

**Branch naming:**
- `feature/<name>` — new capabilities
- `fix/<name>` — bug fixes
- `chore/<name>` — maintenance, refactoring, cleanup
- `release/vX.Y.Z` — release preparation

**Merge requirements:**
- All tests pass (`mvn test`)
- No new SonarQube findings (false positives documented)
- Branch is short-lived (hours to days)
- Squash merge preferred for clean history

## Build

```bash
mvn clean install -DskipTests   # build all modules
mvn test                         # run all tests
```

Requirements: Java 25+, Maven 3.9+

## Release

Before tagging, run the release checklist:

1. `mvn clean install && mvn test` — full reactor builds and all tests pass
2. `./examples/run.sh --all` — example smoke gate passes (model examples need a
   configured endpoint; graceful skips for Node/network are acceptable)
3. `CHANGELOG.md` — move the `Unreleased` section under the new version with
   today's date; every public API or behavior change is represented
4. README Maven coordinates show the new release version
5. `grep -rn "SNAPSHOT" README.md docs/` — no stale version references
6. `git diff --check` — no whitespace errors

Then cut the release:

```bash
git checkout -b release/vX.Y.Z
# Bump version in pom.xml, update CHANGELOG.md
# Remove -SNAPSHOT suffix
git add . && git commit -m "Release vX.Y.Z"
git checkout main && git merge release/vX.Y.Z
git tag vX.Y.Z
git push origin main vX.Y.Z
# Then bump to next -SNAPSHOT on main
```

Version policy, compatibility rules, and the snapshot model live in
[docs/sdk-versioning.md](docs/sdk-versioning.md).

## Code Style

- Java 25 idioms: records, sealed interfaces, pattern matching, virtual threads
- One public class per file
- `@Hidden` on framework internals, public by default
- SLF4J for logging (not System.out)
- Precompiled Patterns for repeated regex use
- `.toList()` over `.collect(Collectors.toList())`

## License

Apache 2.0. All contributions must be under this license.
