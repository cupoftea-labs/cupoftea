# Semantic Versioning (SemVer) & Release Guidelines

All changes, contributions, and releases in CupOfTea must adhere strictly to **Semantic Versioning 2.0.0** (`MAJOR.MINOR.PATCH`).

## 1. Versioning Specification (`MAJOR.MINOR.PATCH`)

Given a version number `MAJOR.MINOR.PATCH`:

1. **`MAJOR` (Breaking Changes)**:
   - Incremented when incompatible API changes are introduced.
   - Examples: changing public method signatures in `CupOfTea`, `IDataFrame`, `ColumnVector`, removing deprecated methods, changing exceptions thrown.
   - Commit type: `feat!:` or commit body containing `BREAKING CHANGE:`.

2. **`MINOR` (New Features & Enhancements)**:
   - Incremented when new functionality is added in a backwards-compatible manner.
   - Examples: adding new statistical methods (`describe()`, `median()`), new join types, new format adapters, new converters.
   - Commit type: `feat:`.

3. **`PATCH` (Bug Fixes & Maintenance)**:
   - Incremented when backwards-compatible bug fixes or internal optimizations are made.
   - Examples: fixing off-by-one errors, null checks, performance tuning, dependency bumps.
   - Commit type: `fix:`, `perf:`.

---

## 2. Conventional Commits Standard

Every commit message must follow the Conventional Commits format:

```text
<type>(<scope>): <short description> (#<issue>)

[optional body explaining context and rationale]

[optional footer: Closes #123 / BREAKING CHANGE: description]
```

Allowed types:
- `feat`: A new feature (bumps MINOR)
- `fix`: A bug fix (bumps PATCH)
- `perf`: Performance improvement (bumps PATCH)
- `docs`: Documentation updates only (no version bump)
- `refactor`: Code change that neither fixes a bug nor adds a feature (no version bump)
- `test`: Adding or correcting tests (no version bump)
- `chore` / `build`: Build tool, pom dependency, or CI updates (no version bump)

---

## 3. Multi-Module Reactor Version Consistency

CupOfTea is a multi-module Maven reactor. When bumping versions:
1. Root `pom.xml`:
   - `<version>X.Y.Z</version>`
   - `<cupoftea.version>X.Y.Z</cupoftea.version>`
2. All 9 submodule `pom.xml` files (`cupoftea-core`, `cupoftea-csv`, `cupoftea-json`, `cupoftea-excel`, `cupoftea-parquet`, `cupoftea-html`, `cupoftea-yaml`, `cupoftea-xml`, `cupoftea-all`):
   - Parent `<version>X.Y.Z</version>`
3. All Maven artifacts and references in `README.md` must match the release version.

---

## 4. Changelog Maintenance

- Every user-facing feature, fix, or deprecation must be documented in `CHANGELOG.md` following [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
- Structure:
  ```markdown
  ## [X.Y.Z] - YYYY-MM-DD

  ### Added
  - Feature description ([#issue](url)).

  ### Changed
  - Behavior change description ([#issue](url)).

  ### Fixed
  - Bug fix description ([#issue](url)).
  ```

---

## 5. Automated GitHub Tagging & Release Flow

- **Development Branch**: `develop` is the default target for pull requests and feature development.
- **Production Release Branch**: `master` (or `main`).
- **Automation Workflow** (`.github/workflows/release.yml`):
  - When changes are pushed or merged into `master` / `main`, GitHub Actions automatically:
    1. Reads the current version from `pom.xml` (`X.Y.Z`).
    2. Checks if the Git tag `vX.Y.Z` already exists.
    3. If not, creates and pushes the Git tag `vX.Y.Z`.
    4. Identifies the previous release tag on `main` and aggregates **all changes in between** (via `.github/scripts/generate-release-notes.py`), combining CHANGELOG highlights with categorized commits and PR references so no intermediate features are omitted.
    5. Builds and packages all JAR assets (`cupoftea-all.jar`, `cupoftea-core.jar`, etc.).
    6. Publishes a GitHub Release with the compiled release notes and packaged binaries attached.

---

## 6. Pull Request Version Check & Merge Gate

- **Merge Blocking Rule**: A Pull Request **cannot be merged** if the version is not updated.
- **Strictly Greater Version (`new_version > base_version`)**: Every PR must increment the version so that the new version is strictly greater than the base branch version. Unchanged versions or version downgrades will fail the check and block merging.
- **Automated Verification Workflow** (`.github/workflows/semver-check.yml`):
  - Runs automatically on every pull request targeting `develop`, `master`, or `main`.
  - Executes `.github/scripts/check-semver.py` to verify:
    1. The PR version is strictly greater than the target base branch version.
    2. All 9 reactor submodules match the root `pom.xml` version.
    3. `CHANGELOG.md` contains an entry for the new version (`## [X.Y.Z] - YYYY-MM-DD`).

