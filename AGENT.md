# JEHibernate — Engineering Guide

This file gives automated agents and contributors a concise but actionable overview of the JEHibernate codebase. Follow the guidance here whenever you modify files inside this repository.

## Quick Instructions for Agents
- **Java Toolchain**: Target Java 21. Use Gradle tasks (`./gradlew`) instead of system `java`/`javac` commands whenever possible.
- **Testing**: Run `./gradlew test` for unit tests. Use `./gradlew check` to execute the full verification suite.
- **Style**: Follow existing patterns. Prefer composition over inheritance unless extending the provided abstractions (e.g., repository base classes). Avoid adding new logging frameworks; rely on SLF4J.
- **Documentation**: Update this file and `README.md` whenever you add public APIs, change build steps, or modify the release process.
- **Pull Requests**: Summaries should highlight repository/API changes, cache behaviour adjustments, and build tooling updates.

## Architecture Overview

JEHibernate wraps Hibernate ORM with pragmatic defaults:

| Layer | Package | Highlights |
| --- | --- | --- |
| Bootstrap | `de.jexcellence.hibernate` | `JEHibernate` orchestrates configuration loading and EntityManagerFactory creation. |
| Configuration | `de.jexcellence.hibernate.config` | `HibernateConfigManager` validates properties and drives bootstrap. |
| Persistence Utilities | `de.jexcellence.hibernate.util` | `DatabaseConnectionManager`, `DatabaseType`, and shared helper utilities. |
| Domain Base Classes | `de.jexcellence.hibernate.entity` | `AbstractEntity` (id/version auditing) and `UUIDConverter`. |
| Repositories | `de.jexcellence.hibernate.repository` | `AbstractCRUDRepository` (sync/async CRUD) and `GenericCachedRepository` (Caffeine-backed). |
| Naming & Extras | `de.jexcellence.hibernate.naming`, `locking`, `creation` | Naming strategy, optimistic lock helpers, and programmatic entity builders. |

All repositories expect a per-operation EntityManager. Async operations run on an injected `ExecutorService` to avoid thread-affinity issues.

## Usage Patterns

### Bootstrap Flow
1. Provide a `hibernate.properties` file with JDBC credentials and optional Hibernate overrides.
2. Instantiate `JEHibernate(filePath)` to create an EntityManagerFactory (EMF).
3. Extend `AbstractCRUDRepository<Entity, ID>` or `GenericCachedRepository<Entity, ID, K>` and inject the EMF plus an `ExecutorService`.
4. Use sync (`create`, `update`, `delete`, `find*`) or async (`*Async`) APIs. Async calls return `CompletableFuture`.
5. Shut down by closing the EMF and the executor.

### Caching Rules (`GenericCachedRepository`)
- Cache keys (`K`) must be stable and unique (String/UUID recommended).
- `findByCacheKey` queries the cache first, then populates on a miss.
- Create/update writes through to the cache; delete evicts the cached instance.
- Prefer matching the cache key with the query attribute (e.g., `Player::getUsername` ↔ `"username"`).
- Override or extend only if you need multi-level caching or special invalidation.

### Environment Recipes
- **Plain Java**: Instantiate `ExecutorService` and `JEHibernate` manually. Clean up on shutdown.
- **Spring**: Expose the EMF and `ExecutorService` as `@Bean`s. Inject repositories via constructor.
- **Spigot/Paper**: Store the EMF and executor in your plugin class, loading configuration from the plugin data directory.

## Build & Release Notes
- **Gradle**: Uses `build.gradle.kts` with `maven-publish`, `io.github.gradle-nexus.publish-plugin`, and `signing`.
- **Publication Targets**:
  - Releases go through [Sonatype Central](https://central.sonatype.com) via the Nexus Publish plugin. Provide `sonatypeUsername`/`sonatypePassword` (or the `CENTRAL_USERNAME`/`CENTRAL_TOKEN` environment variables from your portal user token). Optional overrides: `sonatypeNexusUrl`, `sonatypeSnapshotRepositoryUrl`.
  - Snapshots default to `https://central.sonatype.com/repository/maven-snapshots/` when the same credentials are present; otherwise they stay local (`mavenLocal`).
- **Release Flow**: Run `./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository` once artifacts and Javadocs are ready. For validation only, omit the release step and inspect the staging repository in the Central UI.
- **Signing**: Provide `signingKey`/`signingPassword` (or environment variables) for in-memory PGP signing. Signing is skipped if keys are absent.
- **Artifacts**: Sources and Javadoc JARs are generated automatically via Gradle configuration.

## Testing Guidance
- Prefer the in-memory H2 database for integration-style tests (`com.h2database:h2`).
- Reset caches between tests if state leakage is possible (`clearCache`).
- When testing async flows, use `CompletableFuture#get` with timeouts to avoid hanging builds.

## Extending the Repository Layer
- Derive custom repositories from `AbstractCRUDRepository` for synchronous-only behaviour.
- Use `GenericCachedRepository` for high-read workloads requiring caching.
- Add bespoke queries through protected `executeQuery`/`executeInTransaction` helpers.
- Ensure asynchronous overloads remain non-blocking and propagate exceptions predictably.

## Conventions & Pitfalls
- Never share `EntityManager` instances between threads; obtain a new instance for each operation.
- Ensure entity classes are annotated with `@Entity` and align with the naming strategy (`JENamingStrategy` produces snake_case identifiers).
- When updating cache keys that depend on mutable fields, manually evict stale entries if the key extractor output changes.
- Provide explicit transaction boundaries for complex operations; the base repositories offer convenience wrappers.

## Contribution Checklist
1. Update or create tests alongside feature changes or bug fixes.
2. Keep documentation synchronized (README + AGENT + JavaDoc comments where relevant).
3. Run `./gradlew test` (and `./gradlew check` when touching build logic) before submitting changes.
4. Note any build, repository, or cache-level adjustments in the PR summary.

_Last Updated: 2025-10-27_
