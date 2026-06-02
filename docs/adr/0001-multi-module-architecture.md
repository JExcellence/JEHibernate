# ADR-0001 — Multi-module Gradle build

- Status: Accepted
- Date: 2026-06-02
- Deciders: JExcellence
- Supersedes: single-module `JEHibernate` artifact (3.x)

## Context

JEHibernate 3.x shipped as a single artifact (`de.jexcellence.hibernate:JEHibernate`) used
primarily inside Spigot/Paper plugins. We now also want to serve classic Spring Boot enterprise
backends and a resellable multi-tenant SaaS stack — without dragging Spring, Paper, or
Testcontainers dependencies into the plugin build, and without breaking the existing plugin API.

Two options were considered:

1. **Single module with `compileOnly` Spring/Paper deps and reflection-based activation.**
   Non-breaking, but every consumer pulls one fat artifact; optional integrations are wired with
   reflection glue; the dependency graph published in the POM cannot express "Spring only".
2. **Multi-module split** with a mandatory core and optional satellites.

## Decision

Split into four Gradle modules under one root build:

| Module | Purpose | Mandatory |
|---|---|---|
| `jehibernate-core` | Plugin-agnostic, Spring-agnostic Hibernate/JPA library | yes |
| `jehibernate-spring-boot` | Spring Boot auto-configuration / starter | no |
| `jehibernate-plugin` | Spigot/Paper helpers (`PropertyLoader` data-folder convenience) | no |
| `jehibernate-testing` | Testcontainers integration + test fixtures (testCompile only) | no |

Shared build logic (Java toolchain 24, **bytecode release 17**, encoding, test, javadoc) lives in
the root `subprojects {}` block. A Gradle version catalog (`gradle/libs.versions.toml`) centralises
versions. `jehibernate-core` keeps the Maven publishing + signing configuration from 3.x under the
new coordinate `de.jexcellence.hibernate:jehibernate-core`.

### Java version

The toolchain compiles with JDK 24 but **emits Java 17 bytecode** (`options.release = 17`). This
preserves the plugin runtime contract (Paper runs on Java 21) and keeps the library consumable by
any Java 17+ backend. The "Java 24" framing in earlier docs referred to the build JDK, not the
target — clarified here to avoid using Java 24-only APIs in source.

## Consequences

- **Breaking (major bump to 4.0.0).** The published coordinate changes from `JEHibernate` to
  `jehibernate-core`; plugin consumers must update their dependency declaration. See CHANGELOG and
  the README migration note.
- Spring dependencies never reach the plugin classpath; plugin consumers depend only on
  `jehibernate-core` (+ optionally `jehibernate-plugin`).
- The native Hibernate bootstrap (`MetadataSources` + `BootstrapServiceRegistryBuilder`) and its
  classloader handling stay in `jehibernate-core` unchanged; all new integrations (HikariCP,
  migration) wire through that path, not Spring's.
- The existing `CrudRepository` / `SessionContext` / `TransactionTemplate` public API is unchanged.
