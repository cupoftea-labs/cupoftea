# Release & Publishing Guide

This document outlines the release process and publication workflow for **CupOfTea**.

---

## Architecture Overview

CupOfTea uses GitHub Actions to automate artifact compilation, signing, release note extraction, and multi-registry publication:

1. **GitHub Packages (SNAPSHOT releases)**: Automatically deployed on every push to `develop` or `master` via `.github/workflows/publish-snapshot.yml`.
2. **Maven Central Portal (Stable releases)**: Triggered automatically upon pushing tags matching `v*.*.*` or pushing a release commit to `master`/`main` via `.github/workflows/release.yml`.
3. **GitHub Releases**: Bundles shaded and individual module JARs, Source JARs, Javadoc JARs, and auto-generated release notes comparing current and previous Git tags.

---

## Automated Release Pipelines

### 1. SNAPSHOT Publishing (GitHub Packages)
- **Workflow**: `.github/workflows/publish-snapshot.yml`
- **Triggers**: Push to `develop` or `master`.
- **Target**: `https://maven.pkg.github.com/cupoftea-labs/cupoftea`
- **Authentication**: GitHub Actions internal `GITHUB_TOKEN` with `packages: write` permissions.

### 2. Maven Central Release
- **Workflow**: `.github/workflows/release.yml`
- **Triggers**:
  - Push of tag `vX.Y.Z` (e.g., `git push origin v0.12.0`).
  - Push to `master` / `main`.
  - Manual execution via `workflow_dispatch`.
- **Requirements**:
  - Artifacts signed via GPG (`maven-gpg-plugin`).
  - Source and Javadoc JARs generated and attached.
  - Published to Maven Central Portal using `central-publishing-maven-plugin`.

---

## Release Checklist for Maintainers

1. **Semantic Versioning check**:
   - Ensure the version increment follows [Semantic Versioning 2.0.0](versioning.md) (Major, Minor, or Patch).
   - Update `pom.xml` version across parent and submodules if cutting a new release.

2. **Changelog verification**:
   - Verify `CHANGELOG.md` has an entry for the new version under `## [X.Y.Z] - YYYY-MM-DD`.

3. **GPG & Sonatype Secrets configured**:
   - In GitHub Repository Secrets:
     - `GPG_SIGNING_KEY`: ASCII-armored private GPG key.
     - `GPG_PASSPHRASE`: Passphrase for the GPG private key.
     - `CENTRAL_USERNAME`: Sonatype Central Portal User Token username.
     - `CENTRAL_PASSWORD`: Sonatype Central Portal User Token password.

4. **Triggering the Release**:
   - Create and push a tag:
     ```bash
     git tag -a v0.12.0 -m "Release v0.12.0"
     git push origin v0.12.0
     ```
   - Alternatively, trigger via GitHub Actions UI -> **Release & Publish to Maven Central** -> Run workflow.

---

## Consuming Artifacts

### From Maven Central (Stable)

```xml
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-core</artifactId>
    <version>0.12.0</version>
</dependency>
```

### From GitHub Packages (Snapshot / Pre-release)

In your `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_TOKEN</password>
    </server>
  </servers>
</settings>
```

In your project `pom.xml`:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/cupoftea-labs/cupoftea</url>
    <snapshots><enabled>true</enabled></snapshots>
  </repository>
</repositories>
```
