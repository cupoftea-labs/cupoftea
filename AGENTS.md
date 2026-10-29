# Project Rules & Guidelines

## Semantic Versioning (SemVer)
This project strictly follows [Semantic Versioning 2.0.0](https://semver.org/).
- Current version: **v0.5.0**
- See detailed rules and best practices in [.agents/rules/semver.md](.agents/rules/semver.md).
- Automated releases and tags (`vX.Y.Z`) are handled via `.github/workflows/release.yml` on push to `master` / `main`.
- Pull Requests **must** increment the project version (`new_version > base_version`), enforced via `.github/workflows/semver-check.yml` and `.github/scripts/check-semver.py`. Merging is blocked if the version is not incremented.
