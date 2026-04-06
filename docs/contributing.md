# Contributing

Floci is MIT licensed and welcomes contributions of all kinds.

## Ways to Help 

- **Bug reports** — open a [GitHub issue](https://github.com/floci-io/floci/issues/new?template=bug_report.md) with a minimal reproduction
- **Missing API actions** — open a [feature request](https://github.com/floci-io/floci/issues/new?template=feature_request.md)
- **Pull requests** — new service actions, bug fixes, documentation improvements

## Development Setup

```bash
# Clone
git clone https://github.com/floci-io/floci.git
cd floci

# Run in dev mode (hot reload, port 4566)
mvn quarkus:dev

# Run all tests
mvn test

# Run a specific test
mvn test -Dtest=SsmIntegrationTest
mvn test -Dtest=SsmIntegrationTest#putParameter
```

## Commit Message Format

This project uses [Conventional Commits](https://www.conventionalcommits.org/) — required for semantic-release to generate the changelog and version bumps automatically.

| Prefix | Effect |
|---|---|
| `feat:` | New feature → minor version bump |
| `fix:` | Bug fix → patch version bump |
| `perf:` | Performance improvement → patch |
| `docs:` | Documentation only → no version bump |
| `chore:` | Build/CI → no version bump |
| `feat!:` or `BREAKING CHANGE:` | Breaking change → major bump |

## Adding a New AWS Service

See [AGENT.md](https://github.com/floci-io/floci/blob/main/AGENT.md) for the full architecture guide. `AGENT.md` is the canonical agent instructions file for this repository. If your coding agent expects a different filename, create a local symlink to `AGENT.md` instead of copying it.

```bash
ln -s AGENT.md CLAUDE.md
ln -s AGENT.md GEMINI.md
ln -s AGENT.md COPILOT.md
```

Quick summary:

1. Create `src/main/java/.../services/<service>/` with a Controller, Service, and `model/` package
2. Pick the right protocol (see the protocol table in `AGENT.md`)
3. Register the service in `ServiceRegistry`
4. Add config in `EmulatorConfig.java` and `application.yml`
5. Add `*IntegrationTest.java` tests

## Pull Request Checklist

- [ ] `mvn test` passes
- [ ] New or updated integration test added
- [ ] Commit messages follow Conventional Commits

## Reporting Security Issues

Do **not** open public issues for security vulnerabilities. Use [GitHub private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing/privately-reporting-a-security-vulnerability) instead.
