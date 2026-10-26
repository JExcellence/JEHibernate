# Contributing to JEHibernate

Thanks for your interest in improving JEHibernate. This guide covers how to build, test, and style
your changes.

## Prerequisites

- A JDK to run Gradle (JDK 21 is used in CI). The build compiles with a **Java 24 toolchain**, which
  Gradle auto-provisions via the foojay resolver — you do not need JDK 24 installed locally.
- The library targets **Java 17 bytecode**, so it runs on Java 17+ (Spigot/Paper on 21, Spring Boot,
  standalone).
- [Docker](https://www.docker.com/) is optional. Testcontainers-backed integration tests
  (PostgreSQL / MySQL / MariaDB) **skip** when Docker is unavailable; H2 tests always run.

Use the Gradle wrapper (`./gradlew`), not a local Gradle install.

## Build and test

```bash
# Full build (compile + test + all modules)
./gradlew build

# Run tests only
./gradlew test

# A single module
./gradlew :jehibernate-core:test
```

Please run `./gradlew build` and make sure it is green before opening a pull request.

## Project layout

| Module | Contents |
|---|---|
| `jehibernate-core` | The library (required). |
| `jehibernate-spring-boot` | Spring Boot auto-configuration. |
| `jehibernate-plugin` | Spigot/Paper data-folder convenience (targets Java 21). |
| `jehibernate-testing` | Testcontainers + fixtures (test scope). |
| `jehibernate-bom` | Maven BOM aligning module versions. |

Architecture decisions live in [`docs/adr/`](docs/adr/); the public API is documented in
[`docs/reference.md`](docs/reference.md).

## Code style

Static analysis runs on every push via **Qodana** (`qodana.starter`, JVM Community linter) — see
[`qodana.yaml`](qodana.yaml). Changes should also pass SonarQube-style clean-code checks. In
practice:

- Keep methods below a cognitive-complexity of 15; extract cohesive helpers.
- Use lazy logging (`logger.log(Level.INFO, () -> ...)`), never eager `String.format` in log calls.
- Give every `switch` a `default`; never nest ternaries; one `break`/`continue` per loop.
- Extract a `private static final` constant once a string literal appears 3+ times.
- No commented-out code, no empty blocks without an explaining comment, no unused variables.
- Place imports at the top of the file; avoid fully-qualified inline references.

## Commits and pull requests

- Keep commits small and focused; write a short imperative subject line and a plain-prose body
  explaining what changed and why.
- Describe the change and link any related issue in the PR. Fill in the PR template.
- Add or update tests for behavioural changes. Note in the PR if a change was only verified on H2
  (Docker-backed databases skipped locally).

## Reporting bugs and requesting features

Open an issue using the [bug report](.github/ISSUE_TEMPLATE/bug_report.yml) or
[feature request](.github/ISSUE_TEMPLATE/feature_request.yml) form. Include the JEHibernate version,
Java version, database, and a minimal reproduction where possible.

By contributing, you agree that your contributions are licensed under the project's
[MIT License](LICENSE).
