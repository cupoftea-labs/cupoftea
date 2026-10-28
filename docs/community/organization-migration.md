# Repository & Organization Migration Guide

As the **CupOfTea** project grows, the repository will transition from a personal GitHub account to a dedicated GitHub Organization (e.g., `github.com/<org>/CupOfTea`).

This guide details what will happen, what needs updating, and best practices to ensure seamless transition without breaking downstream builds or GitHub Pages deployments.

---

## 🔁 GitHub Automatic Redirects

When a repository is transferred to an organization on GitHub:
- **Git URLs**: GitHub automatically redirects `git clone`, `git fetch`, and `git push` from `filippo-pequini/CupOfTea` to the new organization repository URL.
- **Web URLs**: All web links to issues, pull requests, commits, and releases automatically forward to the new URL.
- **GitHub Pages**: The GitHub Pages URL changes from `https://filippo-pequini.github.io/CupOfTea/` to `https://<org>.github.io/CupOfTea/` (or a custom domain if configured).

---

## 📋 Checklist for Organization Transfer

When the repository is transferred, update the following configurations:

### 1. `mkdocs.yml`
Update `site_url` and `repo_url`:

```yaml
site_url: https://<new-org>.github.io/CupOfTea/
repo_url: https://github.com/<new-org>/CupOfTea
```

### 2. GitHub Pages Settings
In the organization repository settings:
- Navigate to **Settings** > **Pages**.
- Ensure the **Build and deployment** source is set to **GitHub Actions**.

### 3. Local Git Remotes
Update your local clone's remote URL:

```bash
git remote set-url origin git@github.com:<new-org>/CupOfTea.git
```

### 4. Maven `pom.xml` Distribution / SCM URLs
When publishing to Maven Central under the organization namespace:
Update the `<scm>` block in the parent `pom.xml`:

```xml
<scm>
    <connection>scm:git:git://github.com/<new-org>/CupOfTea.git</connection>
    <developerConnection>scm:git:ssh://github.com:<new-org>/CupOfTea.git</developerConnection>
    <url>https://github.com/<new-org>/CupOfTea</url>
</scm>
```
