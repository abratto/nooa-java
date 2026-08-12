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

Requirements: Java 21+, Maven 3.9+

## Release

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

## Code Style

- Java 21 idioms: records, sealed interfaces, pattern matching, virtual threads
- One public class per file
- `@Hidden` on framework internals, public by default
- SLF4J for logging (not System.out)
- Precompiled Patterns for repeated regex use
- `.toList()` over `.collect(Collectors.toList())`

## License

Apache 2.0. All contributions must be under this license.
