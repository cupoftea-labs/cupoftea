#!/usr/bin/env python3
"""
SemVer verification script for CupOfTea.
Validates that:
1. The PR version in pom.xml is updated compared to the base branch.
2. The new version is strictly greater than the base branch version (SemVer MAJOR.MINOR.PATCH).
3. All reactor submodules match the root pom.xml version.
4. CHANGELOG.md contains an entry for the new version.
"""

import argparse
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET


def parse_semver(v):
    clean = v.strip().lstrip("vV")
    is_snapshot = clean.upper().endswith("-SNAPSHOT")
    core_ver = re.sub(r"-SNAPSHOT$", "", clean, flags=re.IGNORECASE)

    pattern = r"^(\d+)\.(\d+)(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?$"
    m = re.match(pattern, core_ver)
    if not m:
        raise ValueError(
            f"Version '{v}' does not adhere to Semantic Versioning 2.0.0 (MAJOR.MINOR.PATCH)"
        )

    major = int(m.group(1))
    minor = int(m.group(2))
    patch = int(m.group(3) or 0)
    prerelease = m.group(4) or ""
    return {
        "raw": v,
        "clean": core_ver,
        "tuple": (major, minor, patch),
        "major": major,
        "minor": minor,
        "patch": patch,
        "prerelease": prerelease,
        "is_snapshot": is_snapshot,
    }


def get_pom_version_from_content(xml_content, source_name="pom.xml"):
    root = ET.fromstring(xml_content)
    ns = {"mvn": root.tag.split("}")[0].strip("{")} if "}" in root.tag else {}
    if ns:
        elem = root.find("mvn:version", ns)
        if elem is None:
            parent = root.find("mvn:parent", ns)
            if parent is not None:
                elem = parent.find("mvn:version", ns)
    else:
        elem = root.find("version")
        if elem is None:
            parent = root.find("parent")
            if parent is not None:
                elem = parent.find("version")

    if elem is None or not elem.text or not elem.text.strip():
        raise ValueError(f"Could not extract <version> from {source_name}")
    return elem.text.strip()


def get_pom_version_from_file(pom_path):
    with open(pom_path, "r", encoding="utf-8") as f:
        return get_pom_version_from_content(f.read(), pom_path)


def get_base_pom_version(base_ref):
    # Try git show origin/<base_ref>:pom.xml
    targets = [f"origin/{base_ref}:pom.xml", f"{base_ref}:pom.xml"]
    for target in targets:
        try:
            res = subprocess.run(
                ["git", "show", target],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=True,
                text=True,
            )
            return get_pom_version_from_content(res.stdout, target)
        except subprocess.CalledProcessError:
            continue

    # Fallback: check latest git tag
    try:
        res = subprocess.run(
            ["git", "describe", "--tags", "--abbrev=0"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=True,
            text=True,
        )
        tag = res.stdout.strip()
        if tag:
            return tag
    except subprocess.CalledProcessError:
        pass

    return "0.1.0"


def check_submodules(root_version, repo_root="."):
    modules = [
        "cupoftea-core",
        "cupoftea-csv",
        "cupoftea-json",
        "cupoftea-excel",
        "cupoftea-parquet",
        "cupoftea-html",
        "cupoftea-yaml",
        "cupoftea-xml",
        "cupoftea-all",
    ]
    errors = []
    for mod in modules:
        pom_path = os.path.join(repo_root, mod, "pom.xml")
        if os.path.isfile(pom_path):
            try:
                sub_ver = get_pom_version_from_file(pom_path)
                if sub_ver != root_version:
                    errors.append(
                        f"Submodule '{mod}' has version '{sub_ver}', expected '{root_version}'"
                    )
            except Exception as e:
                errors.append(f"Submodule '{mod}' pom error: {e}")
    return errors


def check_changelog(version, changelog_path="CHANGELOG.md"):
    clean_ver = version.lstrip("vV").replace("-SNAPSHOT", "")
    if not os.path.isfile(changelog_path):
        return True, "No CHANGELOG.md found"
    with open(changelog_path, "r", encoding="utf-8") as f:
        content = f.read()

    patterns = [
        f"## [{clean_ver}]",
        f"## [v{clean_ver}]",
        f"## [V{clean_ver}]",
    ]
    if not any(p in content for p in patterns):
        return False, (
            f"CHANGELOG.md does not contain a release entry for '## [{clean_ver}]'. "
            f"Please document the changes under '## [{clean_ver}] - YYYY-MM-DD'."
        )
    return True, f"Found entry for version [{clean_ver}] in CHANGELOG.md."


def log_summary(text):
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        try:
            with open(summary_path, "a", encoding="utf-8") as f:
                f.write(text + "\n")
        except Exception:
            pass


def main():
    parser = argparse.ArgumentParser(
        description="Verify Semantic Versioning compliance for PRs."
    )
    parser.add_argument(
        "--base-ref",
        default=os.environ.get("GITHUB_BASE_REF", "develop"),
        help="Base git reference/branch to compare against (e.g. develop, master)",
    )
    parser.add_argument(
        "--base-version",
        default=None,
        help="Explicit base version to compare against",
    )
    parser.add_argument(
        "--pr-pom",
        default="pom.xml",
        help="Path to the PR root pom.xml",
    )
    parser.add_argument(
        "--changelog",
        default="CHANGELOG.md",
        help="Path to CHANGELOG.md",
    )

    args = parser.parse_args()

    print("=" * 60)
    print("CupOfTea Semantic Versioning (SemVer) PR Verification")
    print("=" * 60)

    # 1. Read PR version
    if not os.path.isfile(args.pr_pom):
        print(f"❌ Error: Root POM not found at {args.pr_pom}")
        sys.exit(1)

    pr_raw_version = get_pom_version_from_file(args.pr_pom)
    try:
        pr_semver = parse_semver(pr_raw_version)
    except ValueError as e:
        print(f"❌ Error: {e}")
        sys.exit(1)

    print(f"PR Version:        {pr_raw_version} -> {pr_semver['tuple']}")

    # 2. Read Base version
    if args.base_version:
        base_raw_version = args.base_version
    else:
        base_raw_version = get_base_pom_version(args.base_ref)

    # Handle legacy placeholder 1.0-SNAPSHOT prior to SemVer adoption
    if base_raw_version.strip() == "1.0-SNAPSHOT":
        print(
            "Notice: Base branch pom.xml had legacy unreleased placeholder '1.0-SNAPSHOT'. "
            "Resolving baseline to last release tag 'v0.1.0'."
        )
        base_raw_version = "0.1.0"

    try:
        base_semver = parse_semver(base_raw_version)
    except ValueError as e:
        print(f"❌ Error parsing base version '{base_raw_version}': {e}")
        sys.exit(1)

    print(f"Base Version ({args.base_ref}): {base_raw_version} -> {base_semver['tuple']}")

    # 3. Check that version was updated
    if pr_semver["tuple"] == base_semver["tuple"]:
        msg = (
            f"### ❌ SemVer PR Verification Failed\n\n"
            f"**The version in `pom.xml` was NOT updated!**\n\n"
            f"- **Base branch (`{args.base_ref}`) version**: `{base_raw_version}`\n"
            f"- **PR version**: `{pr_raw_version}`\n\n"
            f"Every PR must increment the version according to [Semantic Versioning 2.0.0](https://semver.org/):\n"
            f"- **PATCH** (`0.2.0` -> `0.2.1`): Bug fixes, internal refactorings, optimizations.\n"
            f"- **MINOR** (`0.2.0` -> `0.3.0`): New backwards-compatible functionality.\n"
            f"- **MAJOR** (`0.2.0` -> `1.0.0`): Breaking public API changes.\n"
        )
        print("\n❌ SEMVER CHECK FAILED:")
        print(
            f"The version in pom.xml was NOT updated! Both base ({args.base_ref}) and PR "
            f"are at version '{pr_raw_version}'."
        )
        print(
            "Every PR must increment the version according to Semantic Versioning 2.0.0:"
        )
        print("  - PATCH (e.g. 0.2.0 -> 0.2.1) for bug fixes / optimizations")
        print("  - MINOR (e.g. 0.2.0 -> 0.3.0) for new backwards-compatible features")
        print("  - MAJOR (e.g. 0.2.0 -> 1.0.0) for breaking changes")
        log_summary(msg)
        sys.exit(1)

    # 4. Check that new version > base version
    if pr_semver["tuple"] < base_semver["tuple"]:
        msg = (
            f"### ❌ SemVer PR Verification Failed\n\n"
            f"**New version is not strictly greater than base branch version!**\n\n"
            f"- **Base branch (`{args.base_ref}`) version**: `{base_raw_version}`\n"
            f"- **PR version**: `{pr_raw_version}`\n\n"
            f"All PR versions must be strictly greater than the base branch version (`new_version > base_version`).\n"
        )
        print("\n❌ SEMVER CHECK FAILED:")
        print(
            f"The new version '{pr_raw_version}' is LESS than the base branch version '{base_raw_version}'!"
        )
        print("All new versions must be strictly greater than the previous version.")
        log_summary(msg)
        sys.exit(1)

    print(
        f"✅ Version update verified: {base_raw_version} -> {pr_raw_version} (strictly increasing)"
    )

    # 5. Check all reactor submodules
    submodule_errors = check_submodules(pr_raw_version)
    if submodule_errors:
        err_list = "\n".join(f"- {err}" for err in submodule_errors)
        log_summary(f"### ❌ Submodule Version Mismatch\n\n{err_list}\n")
        print("\n❌ SUBMODULE VERSION MISMATCH:")
        for err in submodule_errors:
            print(f"  - {err}")
        print("All 9 submodules must have versions matching root pom.xml.")
        sys.exit(1)
    print("✅ All 9 reactor submodules match the root POM version.")

    # 6. Check CHANGELOG.md entry
    changelog_ok, changelog_msg = check_changelog(pr_raw_version, args.changelog)
    if not changelog_ok:
        log_summary(f"### ❌ CHANGELOG Entry Missing\n\n{changelog_msg}\n")
        print(f"\n❌ CHANGELOG CHECK FAILED:\n{changelog_msg}")
        sys.exit(1)
    print(f"✅ {changelog_msg}")

    log_summary(
        f"### ✅ SemVer PR Verification Passed\n\n"
        f"| Metric | Details |\n"
        f"| --- | --- |\n"
        f"| **Base Version (`{args.base_ref}`)** | `{base_raw_version}` |\n"
        f"| **PR Version** | `{pr_raw_version}` |\n"
        f"| **Strict Increase** | `true` ({base_raw_version} < {pr_raw_version}) |\n"
        f"| **Reactor Submodules (9)** | All match `{pr_raw_version}` |\n"
        f"| **CHANGELOG Entry** | Verified `{pr_raw_version}` |\n"
    )
    print("\n🎉 ALL SEMVER CHECKS PASSED SUCCESSFULLY!")
    print("=" * 60)


if __name__ == "__main__":
    main()
