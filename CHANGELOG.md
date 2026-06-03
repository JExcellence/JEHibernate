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
- **Multi-tenancy** (TODO-5, ADR-0004), off by default:
  - `MultiTenancyStrategy` (NONE/DATABASE/SCHEMA/DISCRIMINATOR), `MultiTenancyConfig`,
    `ConfigurationBuilder.multiTenancy(...)`.
  - `TenantContext` (thread-local, nesting `AutoCloseable` scope), `TenantResolver` SPI,
    `TenantContextResolver` (Hibernate `CurrentTenantIdentifierResolver` adapter with strict
    leak-guard that throws when no tenant is bound).
  - `SchemaMultiTenantConnectionProvider` (one shared pool, per-tenant `setSchema`, reset on
    release), `DatabaseMultiTenantConnectionProvider` (tenant → DataSource).
  - `docs/multi-tenancy-guide.md`.
  - Note: TODO-4 (Envers audit) is skipped, so the planned `tenant_id` column on the audit
    revision entity is deferred with it.
- Integration tests: `PoolIntegrationTest` (50 parallel queries, health, defaults),
  `MigrationIntegrationTest` (apply-on-empty, no-op-on-restart, disabled, Liquibase config),
  `EntityGraphIntegrationTest` (single-query collection fetch verified via Hibernate Statistics),
  `DiscriminatorMultiTenancyTest` (tenant isolation, leak-guard throws, thread switch, shared pool),
  `MultiTenantConnectionProviderTest` (SCHEMA/DATABASE provider unit coverage).
- **Testing module** `jehibernate-testing` (TODO-6):
  - `@JEHibernateTest` + `JEHibernateExtension` (JUnit 5): boots JEHibernate against H2 or a
    Testcontainers container (PostgreSQL/MySQL/MariaDB/MSSQL), injects `JEHibernate`/
    `EntityManagerFactory` as test parameters, resets the DB after each test.
  - `TestDatabase`, `DatabaseReset` (NONE/TRUNCATE_ALL/DROP_CREATE/ROLLBACK_TX, FK-aware via
    Hibernate `SchemaManager`), `Fixtures` + `Fixtures.Builder` fixture pattern.
  - Container-backed tests skip (not fail) when Docker is unavailable.
  - `docs/testing-guide.md`. The Spring `@JEHibernateRepositoryTest` slice is deferred until the
    Spring Boot auto-configuration exists.
- **Plugin-bias decoupling** (TODO-7):
  - `PropertyLoader.fromClasspath(String)` and `fromFile(Path)` named entry points in core.
  - `PluginPropertyLoader.fromPluginDataFolder(...)` in `jehibernate-plugin` (the File/data-folder
    convenience now lives in the plugin module).
  - `slf4j-api` is now an `implementation` dependency of core (bundled transitively) so
    standalone/Spring consumers get the logging facade without manual setup.
  - `examples/spring-boot-demo/` (bootstrap < 50 lines) and `examples/spigot-plugin-demo/`
    (unchanged plugin API) added; `jehibernate-plugin` targets Java 21 (Paper runtime).
- Integration/extension tests: `H2JEHibernateExtensionTest` (boot + reset between tests);
  a shared `AbstractCrudAcrossDatabasesIT` CRUD+query scenario run per database via
  `H2CrudIT` (always runs) and `PostgresCrudIT`/`MySqlCrudIT`/`MariaDbCrudIT`
  (Testcontainers, skipped without Docker).
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
