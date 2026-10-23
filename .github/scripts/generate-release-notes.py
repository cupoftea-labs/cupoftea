#!/usr/bin/env python3
"""
Generate comprehensive release notes for CupOfTea by comparing:
- The previous release tag (current tag on main)
- The new release tag (target tag from pom.xml)

Extracts:
1. Curated CHANGELOG.md notes for the version (if present).
2. All commits and PRs merged between previous_tag and new_tag, categorized by Conventional Commits.
3. Git comparison link.
"""

import argparse
import os
import re
import subprocess
import sys


def run_cmd(cmd):
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=True)
    return res.stdout.strip()


def get_previous_tag(new_tag):
    try:
        # Fetch tags to ensure local cache is complete (if network available)
        subprocess.run(
            ["git", "fetch", "--tags", "--quiet"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
        output = run_cmd(["git", "tag", "--sort=-version:refname"])
        tags = [t.strip() for t in output.splitlines() if t.strip()]
        for t in tags:
            # Match standard semver tags (e.g. v0.1.0, 0.1.0)
            if re.match(r"^v?\d+\.\d+\.\d+", t):
                clean_t = t.lstrip("vV")
                clean_new = new_tag.lstrip("vV")
                if clean_t != clean_new:
                    return t
    except Exception:
        pass
    return None


def get_commits_between(prev_tag, head_ref="HEAD"):
    rev_range = f"{prev_tag}..{head_ref}" if prev_tag else head_ref
    cmd = ["git", "log", rev_range, "--pretty=format:%H%x09%s%x09%an%x09%b%x1e"]
    try:
        output = run_cmd(cmd)
    except Exception as e:
        print(f"Warning: git log failed for range '{rev_range}': {e}", file=sys.stderr)
        return []

    commits = []
    raw_records = output.split("\x1e")
    for rec in raw_records:
        rec = rec.strip()
        if not rec:
            continue
        parts = rec.split("\x09")
        sha = parts[0]
        subject = parts[1] if len(parts) > 1 else ""
        author = parts[2] if len(parts) > 2 else ""
        body = parts[3] if len(parts) > 3 else ""
        commits.append({
            "sha": sha,
            "short_sha": sha[:7],
            "subject": subject,
            "author": author,
            "body": body,
        })
    return commits


def categorize_commits(commits):
    categories = {
        "breaking": [],
        "feat": [],
        "fix": [],
        "perf": [],
        "refactor": [],
        "docs": [],
        "deps": [],
        "ci_chore": [],
        "other": [],
    }

    # Regex patterns for conventional commits
    conv_pattern = re.compile(r"^([a-zA-Z]+)(?:\(([^)]+)\))?(!)?:\s*(.*)$")

    for c in commits:
        sub = c["subject"].strip()
        # Skip merge commit noise if desired, but capture PR numbers
        is_merge = sub.startswith("Merge pull request #") or sub.startswith("Merge branch ")

        # Check for breaking change markers
        if "BREAKING CHANGE:" in c["body"] or "BREAKING CHANGE:" in sub:
            categories["breaking"].append(c)
            continue

        m = conv_pattern.match(sub)
        if m:
            ctype = m.group(1).lower()
            is_breaking = bool(m.group(3))
            if is_breaking:
                categories["breaking"].append(c)
                continue

            if ctype in ("feat", "feature"):
                categories["feat"].append(c)
            elif ctype in ("fix", "bugfix"):
                categories["fix"].append(c)
            elif ctype in ("perf",):
                categories["perf"].append(c)
            elif ctype in ("refactor",):
                categories["refactor"].append(c)
            elif ctype in ("docs",):
                categories["docs"].append(c)
            elif ctype in ("deps", "build") or "dependabot" in sub.lower():
                categories["deps"].append(c)
            elif ctype in ("ci", "chore", "test"):
                categories["ci_chore"].append(c)
            else:
                categories["other"].append(c)
        else:
            if not is_merge:
                categories["other"].append(c)

    return categories


def extract_changelog_section(version, changelog_path="CHANGELOG.md"):
    if not os.path.isfile(changelog_path):
        return ""
    clean_ver = version.lstrip("vV").replace("-SNAPSHOT", "")
    with open(changelog_path, "r", encoding="utf-8") as f:
        content = f.read()

    lines = content.splitlines()
    in_section = False
    section_lines = []

    pattern = re.compile(rf"^##\s*\[?v?{re.escape(clean_ver)}\]?")
    for line in lines:
        if pattern.match(line):
            in_section = True
            continue
        elif in_section:
            if line.startswith("## "):
                break
            section_lines.append(line)

    return "\n".join(section_lines).strip()


def build_markdown(new_tag, prev_tag, changelog_notes, categorized, repo_url=None):
    md = []
    md.append(f"# Release {new_tag}\n")

    if prev_tag:
        md.append(f"> **Changes between `{prev_tag}` and `{new_tag}`**\n")
    else:
        md.append(f"> **Initial Release `{new_tag}`**\n")

    # 1. Highlights from CHANGELOG.md if available
    if changelog_notes:
        md.append("## 🌟 Release Summary & Highlights\n")
        md.append(changelog_notes)
        md.append("\n")

    # 2. Detailed Change Log from commits
    md.append("## 📋 What's Changed\n")

    def format_list(commits_list):
        items = []
        for c in commits_list:
            sha = c["short_sha"]
            sub = c["subject"]
            author = c["author"]
            items.append(f"- {sub} (`{sha}`) by @{author}")
        return "\n".join(items)

    sections = [
        ("💥 Breaking Changes", categorized["breaking"]),
        ("🚀 New Features", categorized["feat"]),
        ("🐛 Bug Fixes", categorized["fix"]),
        ("⚡ Performance Improvements", categorized["perf"]),
        ("🔄 Refactoring & API Improvements", categorized["refactor"]),
        ("📝 Documentation", categorized["docs"]),
        ("📦 Dependencies & Build", categorized["deps"]),
        ("🧪 CI & Testing", categorized["ci_chore"]),
        ("🔧 Other Changes", categorized["other"]),
    ]

    has_entries = False
    for title, group in sections:
        if group:
            has_entries = True
            md.append(f"### {title}\n")
            md.append(format_list(group))
            md.append("\n")

    if not has_entries and not changelog_notes:
        md.append("- Maintenance and stability enhancements.\n")

    # 3. Full Comparison Link
    if prev_tag and repo_url:
        md.append("---")
        md.append(f"**Full Changelog**: {repo_url}/compare/{prev_tag}...{new_tag}\n")
    elif prev_tag:
        md.append("---")
        md.append(f"**Full Comparison**: `{prev_tag}...{new_tag}`\n")

    return "\n".join(md)


def main():
    parser = argparse.ArgumentParser(description="Generate release notes between git tags.")
    parser.add_argument("--new-tag", required=True, help="New tag being released (e.g. v0.2.0)")
    parser.add_argument("--prev-tag", default=None, help="Previous release tag (e.g. v0.1.0)")
    parser.add_argument("--changelog", default="CHANGELOG.md", help="Path to CHANGELOG.md")
    parser.add_argument("--output", default="RELEASE_NOTES.md", help="Output markdown file path")
    parser.add_argument("--repo-url", default=None, help="GitHub repository URL (for compare link)")

    args = parser.parse_args()

    new_tag = args.new_tag
    prev_tag = args.prev_tag or get_previous_tag(new_tag)
    repo_url = args.repo_url or os.environ.get("GITHUB_SERVER_URL", "https://github.com") + "/" + os.environ.get("GITHUB_REPOSITORY", "filippo-pequini/CupOfTea")

    print(f"Generating release notes: {prev_tag or 'START'} -> {new_tag}")

    commits = get_commits_between(prev_tag)
    categorized = categorize_commits(commits)
    changelog_notes = extract_changelog_section(new_tag, args.changelog)

    content = build_markdown(new_tag, prev_tag, changelog_notes, categorized, repo_url)

    with open(args.output, "w", encoding="utf-8") as f:
        f.write(content)

    print(f"Release notes written to {args.output} ({len(commits)} commits processed)")


if __name__ == "__main__":
    main()
