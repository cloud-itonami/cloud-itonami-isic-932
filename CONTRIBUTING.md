# Contributing

This project is part of the cloud-itonami fleet and follows the governance outlined in its parent organization.

## Development Setup

```bash
git clone https://github.com/cloud-itonami/cloud-itonami-isic-932
cd cloud-itonami-isic-932
clojure -M:dev
```

## Running Tests

```bash
clojure -M:test
```

## Running Lint

```bash
clojure -M:lint
```

## Code Style

- Use `.cljc` for ClojureScript + JVM compatibility
- All modules must pass clj-kondo lint (0 errors)
- Follow the established governor pattern (three HARD checks, no overrides)
- Keep operations in the closed allowlist; scope exclusion is permanent

## Submitting Changes

1. Create a feature branch
2. Make your changes (tests must pass, lint clean)
3. Submit a pull request with a clear description of the changes
4. The project maintainers will review and merge

## Code of Conduct

Please note that this project is released with a Contributor Covenant Code of Conduct. By participating in this project you agree to abide by its terms.
