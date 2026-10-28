# Contributing Guide

We welcome contributions to **CupOfTea**! Whether you are reporting a bug, proposing an optimization, adding a new format provider, or improving documentation, here is how to get started.

---

## 🛠️ Development Setup

### Prerequisites
- JDK 17, 21, or 25
- Apache Maven 3.8+
- Python 3.10+ (with `uv` or `pip` for MkDocs)

### Build & Run Tests

```bash
# Clone the repository
git clone git@github.com:filippo-pequini/CupOfTea.git
cd CupOfTea

# Run all unit and integration tests across modules
mvn clean test

# Verify all artifacts
mvn verify
```

---

## 📚 Running Documentation Locally

The documentation is powered by **MkDocs Material**. You can preview it with live-reload:

```bash
# Using uv (fast, isolated)
uv run --with mkdocs-material mkdocs serve

# Or using standard pip
pip install mkdocs-material
mkdocs serve
```

Visit `http://127.0.0.1:8000` in your browser.

---

## 📐 Coding Conventions

1. **Primitive-First Storage**: When designing columnar operations, prefer primitive vector access (`int[]`, `double[]`) rather than boxed collections.
2. **Preserve Immutability on Row**: Do not leak internal mutable maps or state from `Row` or `ColumnVector`.
3. **Null Semantics**: Ensure operations respect `com.cupoftea.table.NA` and do not conflate empty strings `""` with `null`.
4. **Tests**: All new features and bug fixes must include JUnit 5 tests.
