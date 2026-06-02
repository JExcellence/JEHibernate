# Changelog

All notable changes to JEHibernate are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [4.0.0] — 2026-06-02

Major release. The library is now a **multi-module build** and the connection-pool and
schema-management defaults changed. See the migration notes below.

### Added

- **Multi-module build** (ADR-0001): `jehibernate-core` (mandatory), `jehibernate-spring-boot`,
  `jehibernate-plugin`, `jehibernate-testing`. Gradle version catalog at
  `gradle/libs.versions.toml`. Shared build config in the root `subprojects {}` block.
- **HikariCP connection pool** as the default (TODO-1, ADR-0003):
  - `PoolConfig` record with `maximumPoolSize`, `minimumIdle`, `idleTimeout`, `connectionTimeout`,
    `maxLifetime`, `leakDetectionThreshold`, `validationTimeout` (+ sensible defaults).
  - `ConfigurationBuilder.pool(PoolConfig)` and `ConfigurationBuilder.dataSource(DataSource)` for
    reusing an external (e.g. Spring) `DataSource`.
  - `JEHibernate.getPoolHealth()` → `PoolHealth(active, idle, total, threadsAwaiting, available)`.
  - `JEHibernate.getDataSource()`.
  - Pool properties readable from `hibernate.properties` via `jehibernate.pool.*`.
  - Deterministic pool shutdown in `JEHibernate.close()` (only when JEHibernate owns the pool).
- **Schema migration** (TODO-2, ADR-0002):
  - Flyway by default (`V001__init.sql` under `classpath:db/migration`); Liquibase opt-in.
  - `MigrationConfig`, `MigrationTool`, `MigrationRunner`, `MigrationSupport`,
    `FlywayMigrationRunner`, `LiquibaseMigrationRunner`.
  - `ConfigurationBuilder.migration(MigrationConfig)`; properties `jehibernate.migration.{enabled,
    tool,location}`.
  - Migrations run before `SessionFactory.build()`. Absent tool → silent no-op.
- **Lazy-loading / EntityGraph helpers** (TODO-3):
  - `AbstractCrudRepository.findByIdWithGraph(id, paths...)`, `findAllWithGraph(paths...)`,
    `findByIdWithNamedGraph(id, name)` — fetch named associations in a single query (no N+1),
    applied as a JPA `loadgraph` hint. Dot-separated paths supported for nested graphs.
  - `docs/lazy-loading-guide.md` with a when-to-use decision table (scoping vs read-only vs
    EntityGraph vs OSIV). OSIV is documented as a copy-ready last-resort pattern, deliberately
    not shipped as a bean.
- Integration tests: `PoolIntegrationTest` (50 parallel queries, health, defaults),
  `MigrationIntegrationTest` (apply-on-empty, no-op-on-restart, disabled, Liquibase config),
  `EntityGraphIntegrationTest` (single-query collection fetch verified via Hibernate Statistics).
- ADRs under `docs/adr/`.

### Changed

- **BREAKING — published coordinate:** `de.jexcellence.hibernate:JEHibernate` →
  `de.jexcellence.hibernate:jehibernate-core`.
- **BREAKING — `ddl-auto` default:** `update` → `validate`. Migrations now own the schema. Callers
  relying on the implicit `update` default must set `ddlAuto("update")` explicitly, provide
  migrations, or set `jehibernate.migration.enabled=false`.
- **BREAKING — connection pool:** `connectionPool(min, max)` now configures HikariCP via
  `PoolConfig` instead of writing `hibernate.agroal.*`. Method signature unchanged. Agroal
  `compileOnly` dependencies removed.
- `BukkitPluginExample` and other samples moved to `jehibernate-plugin/examples/`.

### Fixed

- `EntityScanner` / `RepositoryScanner` now also enumerate the package via the classloader in
  addition to JEHibernate's code-source URL, so entities/repositories located in a different
  output root than the JEHibernate jar (e.g. test sources, non-shaded consumers) are discovered.
  Previously scanning only the code-source URL returned zero results in those layouts.
- `Specifications.equal(...)` reference in `IntegrationTest` updated to the renamed `equalTo(...)`
  (the suite no longer compiled after the 3.x rename).

### Migration from 3.x

```kotlin
// before
implementation("de.jexcellence.hibernate:JEHibernate:3.0.4")
// after
implementation("de.jexcellence.hibernate:jehibernate-core:4.0.0")
implementation("org.flywaydb:flyway-core:11.1.1") // optional: enables migrations
```

If you relied on Hibernate auto-DDL, keep the old behaviour explicitly:

```java
.configuration(c -> c.database(...).url(...).ddlAuto("update"))
```
