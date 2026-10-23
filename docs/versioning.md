# Semantic Versioning & Release Best Practices

CupOfTea adheres to [Semantic Versioning 2.0.0](https://semver.org/) and follows [Keep a Changelog](https://keepachangelog.com/).

Current Version: **`v0.2.0`**

---

## 1. Version Format: `MAJOR.MINOR.PATCH`

| Component | Trigger | Example |
| :--- | :--- | :--- |
| **`MAJOR`** | Incompatible API changes, removals, breaking behavior | `1.0.0` &rarr; `2.0.0` |
| **`MINOR`** | Backwards-compatible new features, aggregations, formats | `0.2.0` &rarr; `0.3.0` |
| **`PATCH`** | Backwards-compatible bug fixes and performance tuning | `0.2.0` &rarr; `0.2.1` |

---

## 2. Commit Best Practices (Conventional Commits)

Use structured prefix messages:

- `feat(scope): ...` &rarr; New feature (**MINOR**)
- `fix(scope): ...` &rarr; Bug fix (**PATCH**)
- `perf(scope): ...` &rarr; Performance optimization (**PATCH**)
- `feat(scope)!: ...` or `BREAKING CHANGE: ...` &rarr; Breaking change (**MAJOR**)
- `docs(scope): ...` &rarr; Documentation only
- `refactor(scope): ...` &rarr; Code refactoring without behavior change
- `test(scope): ...` &rarr; Adding or modifying unit/integration tests
- `chore(scope): ...` &rarr; Build configuration, dependencies, or tooling

---

## 3. How to Release a New Version

1. **Bump Version Across POMs**:
   Update `pom.xml` in the root and all 9 module folders:
   ```bash
   # Or via Maven Versions Plugin:
   mvn versions:set -DnewVersion=0.3.0 -DgenerateBackupPoms=false
   ```

2. **Update `CHANGELOG.md`**:
   Add a new section for the release:
   ```markdown
   ## [0.3.0] - YYYY-MM-DD

   ### Added
   - Description of new features...
   ```

3. **Merge into Main/Master**:
   When merged into `master` (or `main`), the automated GitHub Actions workflow (`.github/workflows/release.yml`) will:
   - Verify the version in `pom.xml` (`0.3.0`).
   - Automatically tag `v0.3.0`.
   - Extract the release notes from `CHANGELOG.md`.
   - Build all module JAR packages.
   - Publish the GitHub Release with attached binaries.
