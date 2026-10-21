# Changelog

All notable changes to JEHibernate are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [4.2.1] — 2026-10-21

### Fixed — critical, affects external DataSources (Spring Boot) on PostgreSQL & MySQL/MariaDB

- **`hibernate.connection.provider_disables_autocommit` is only set when it is true.** JEHibernate set it
  unconditionally, promising Hibernate that every connection arrives with autocommit off. 4.2.0 made that
  true for the owned HikariCP pool, but a `DataSource` passed via `ConfigurationBuilder.dataSource(...)` —
  which is what the Spring Boot starter does — is used as is, and Spring Boot's HikariCP defaults to
  `autoCommit=true`. Hibernate then skipped `setAutoCommit(false)` and committed against an autocommit
  connection: **PostgreSQL and MySQL reject the commit** (`Cannot commit when autoCommit is enabled`,
  `Can't call commit when autocommit=true`), so every write threw even though each statement had already
  been auto-committed, and rollbacks undid nothing. MariaDB accepts the commit but likewise loses
  rollbacks. Present in 4.0.0–4.2.0 for external data sources; the owned pool was fine as of 4.2.0.
- The flag is now `true` for the owned pool and for an external `DataSource` that is known to have
  autocommit off: a `HikariDataSource` is checked via `isAutoCommit()`, any other `DataSource` by reading
  `getAutoCommit()` from one connection at bootstrap. With DATABASE multi-tenancy every tenant data source
  must qualify. Anything unconfirmed leaves the flag `false`, so Hibernate disables autocommit per
  transaction itself. An explicit `property(...)` value still takes precedence.
- **Workaround on 4.0.0–4.2.0:** set `spring.datasource.hikari.auto-commit=false` (or disable autocommit on
  the `DataSource` you pass in).

### Tests

- `AutoCommitContractTest` (core, H2) pins the flag for the owned pool, external HikariCP with autocommit
  on/off, a plain `DataSource`, and an explicit override.
- `PostgresExternalDataSourceIT`, `MySqlExternalDataSourceIT`, `MariaDbExternalDataSourceIT` run CRUD and a
  rolled-back transaction through an external HikariCP pool with `autoCommit=true` and `false` against real
  databases (skipped without Docker). Without the fix, 5 of their 9 checks fail, plus 2 of the 5 in
  `AutoCommitContractTest`.

## [4.2.0] — 2026-10-21

### Fixed — critical, affects PostgreSQL & MySQL

- **The owned HikariCP pool now disables autocommit (`hikari.setAutoCommit(false)`).** JEHibernate sets
  `hibernate.connection.provider_disables_autocommit=true`, telling Hibernate the pool already turned
  autocommit off — but the pool was left at HikariCP's default (`autoCommit=true`). Hibernate then
  committed its transactions against an autocommit connection, which **PostgreSQL and MySQL/MariaDB
  reject** (`Cannot commit when autoCommit is enabled`), so every write failed on those databases when
  using the JEHibernate-owned pool. H2 tolerates it, which is why the defect was invisible to the
  H2-only quick tests and only the Testcontainers CRUD suite (PostgreSQL/MySQL/MariaDB) caught it.
  Present in 4.0.0–4.1.0. **Consumers on PostgreSQL/MySQL/MariaDB with the owned pool should upgrade.**
  Verified by `PostgresCrudIT`, `MySqlCrudIT`, and `MariaDbCrudIT` against real databases.

### Added

- **`jehibernate-bom`** — a Maven BOM (`de.jexcellence.hibernate:jehibernate-bom`) that aligns the
  versions of all JEHibernate modules. Import it once and omit per-module versions.
- **Entity-class-inferring repository constructors.** Repositories may now omit the `Class<T>` argument:
  `super(executor, emf)` on `AbstractCrudRepository`, and `super(executor, emf, keyExtractor)` on
  `AbstractCachedRepository`. The entity type is resolved from the generic superclass chain. The explicit
  `Class<T>` constructors remain for repositories whose type cannot be resolved by reflection.

### Changed

- `AbstractCrudRepository.findAllMatching(Predicate)` Javadoc now prominently warns that it is an
  in-memory full-table scan, and points to `query()` / `findAll(Specification)` for database-level
  filtering. (No behavior change.)

## [4.1.0] — 2026-10-21

### Changed — Hibernate 7.4 and the `@Version` column contract

- **Hibernate ORM `7.1.4 → 7.4.13`.** Hibernate 7.2+ maps `@Version` columns as **`NOT NULL`
  unconditionally** (it ignores `@Column(nullable = true)`). JEHibernate's `BaseEntity.version` is now
  mapped `@Version @ColumnDefault("0")`, so a fresh schema creates `version INTEGER NOT NULL DEFAULT 0`
  and versions start at 0. The field stays a boxed `Integer` and the `getVersion()` accessor stays
  null-safe purely for transition tolerance (see migration below); no public API change.

- **The 4.0.1/4.0.2 nullable-`@Version` behavior is gone** because Hibernate closed it: on 7.2+,
  `hbm2ddl.auto=update` **cannot** add the `NOT NULL` version column to an already-populated table (the
  `ADD COLUMN` fails for existing rows and aborts the entire schema update, so the timestamp columns are
  left unadded too). This is consistent with 4.0's "migrations own the schema" stance (ADR-0002):
  evolving an existing table is a Flyway/Liquibase step, not an `hbm2ddl` one.

#### Migration — consumers adopting 4.1.0 on an EXISTING database

Run once per `BaseEntity`/`LongIdEntity`/`UuidEntity`/`StringIdEntity` table (replace `your_table`):

- **You are coming from 4.0.1/4.0.2** (the column exists but is **nullable**):

  ```sql
  UPDATE your_table SET version = 0 WHERE version IS NULL;
  ALTER TABLE your_table ALTER COLUMN version SET NOT NULL;   -- PostgreSQL/H2
  -- MySQL/MariaDB: ALTER TABLE your_table MODIFY version INTEGER NOT NULL DEFAULT 0;
  ```

- **The table never had a version column** (adding optimistic locking now):

  ```sql
  ALTER TABLE your_table ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
  ```

  Ship this as a Flyway migration (`V…__add_version.sql` under `classpath:db/migration`); do not rely on
  `hbm2ddl.auto=update`. A fresh database created by 4.1.0 needs no migration. `VersionMigrationTest`
  covers the fresh-schema, optimistic-locking, and migrated-populated-table (`validate`) paths.

### Changed — build toolchain

- **Gradle `8.8 → 8.14.6`.** 8.8 did not officially support the JDK 24 toolchain, bundled a JUnit
  Platform launcher too old for JUnit 5.12+, and broke the dependency-versions plugin. 8.14.6 runs
  cleanly on JDK 24.
- **Spring Boot `3.4.1 → 3.5.16`** and **JUnit `5.11.4 → 5.14.4`** — unblocked by the Gradle upgrade
  (both were held back in 4.0.4 for exactly this reason).
- Added `org.junit.platform:junit-platform-launcher:1.14.4` to each module's test runtime so Gradle
  drives the matching JUnit Platform engine (prevents the test-teardown crash with JUnit 5.12+).

## [4.0.4] — 2026-10-21

### Changed — dependency refresh (verified; full test suite green)

Within-major, backward-compatible updates only. No API or behavior change.

- Hibernate HikariCP pool `6.2.1 → 6.3.0`; Caffeine `3.2.0 → 3.3.1`; Jackson `2.18.2 → 2.22.4`;
  Flyway `11.1.1 → 11.20.3`; SLF4J `2.0.16 → 2.0.20`.
- JDBC drivers: PostgreSQL `42.7.7 → 42.7.14`; MariaDB `3.5.2 → 3.5.10`; H2 (test/compile-only)
  `2.4.240 → 2.5.252`.
- Spring Framework (spring-orm/spring-test) `6.2.1 → 6.2.19`; JetBrains annotations `26.0.1 → 26.1.0`.
- Test tooling: AssertJ `3.27.3 → 3.27.7`; Mockito `5.15.2 → 5.24.0`; Testcontainers `1.20.4 → 1.21.4`.

### Held back deliberately (require a decision, not a patch)

- **Hibernate `7.1.4 → 7.4.x`** — Hibernate 7.2+ generates `@Version` columns as `NOT NULL`
  **unconditionally** (it ignores `@Column(nullable = true)`). That defeats the nullable-`@Version`
  migration behavior added in 4.0.1/4.0.2 (verified: `VersionMigrationTest` fails on 7.4). Moving to
  7.2+ needs a different approach to the populated-table-migration case first.
- **Spring Boot `3.4.1 → 3.5.x` and JUnit `5.11 → 5.12+`** — both pull a JUnit Platform newer than the
  launcher bundled with Gradle 8.8, which crashes test teardown. These unblock after a Gradle upgrade
  (8.8 also does not officially support the JDK 24 toolchain in use).
- **MySQL connector `9.3.0 → 26.x`, MSSQL `12.8.x → 13.x`, Liquibase `4.30 → 5.x`** — driver/tool major
  versions; deferred pending a compatibility check.

## [4.0.3] — 2026-10-21

### Fixed

- **fix(config): let Hibernate auto-detect the dialect instead of pinning it.** `ConfigurationBuilder`
  unconditionally set `hibernate.dialect` (filled from the `DatabaseType` default), so every boot logged
  Hibernate's `HHH90000025` deprecation warning (*"H2Dialect does not need to be specified explicitly …"*).
  Hibernate 6/7 resolves the dialect from the JDBC connection metadata, so JEHibernate now leaves it unset
  unless the caller forces one via `.dialect(...)` or a namespaced `<type>.dialect` property.
  `DatabaseType.getDialectClass()` remains available as the explicit escape hatch. No API change.

### Note — `No SLF4J providers were found` in shaded plugins

- This warning is **not** emitted by JEHibernate; it comes from a plugin that shades and **relocates**
  `org.slf4j` (e.g. to `…remapped.org.slf4j`) while bundling the slf4j **API without a provider**. On
  Paper, the fix is to **not relocate `org.slf4j`** — the server already provides slf4j-api and a
  Log4j2-backed provider, so an un-relocated facade binds to the console. If you must relocate, bundle a
  provider (`slf4j-jdk-platform-logging` or `slf4j-simple`) so its `META-INF/services` entry is relocated
  with it. slf4j-api is present transitively via `reflections`, so this cannot be resolved library-side.

## [4.0.2] — 2026-09-30

### Fixed

- **fix(entity): map `UuidEntity` id via `SqlTypes.UUID` instead of a hard-coded `BINARY(16)` literal.**
  `UuidEntity` declared `@Column(columnDefinition = "BINARY(16)")`, forcing that raw DDL fragment into
  `CREATE TABLE`. PostgreSQL has no `BINARY` type, so **table creation failed entirely on PostgreSQL** —
  the entity only ever worked on H2/MySQL/MariaDB. The id now uses `@JdbcTypeCode(SqlTypes.UUID)`, so
  Hibernate emits the dialect-native type: `uuid` on PostgreSQL and H2, `binary(16)` on MySQL/MariaDB,
  `uniqueidentifier` on SQL Server. No API change (`getId()` is still `UUID`).

  **Storage impact per database:**
  - **PostgreSQL** — previously broken; now creates a native `uuid` column. No migration (there was no
    working table to migrate).
  - **MySQL / MariaDB** — column stays `binary(16)`. **No migration needed.**
  - **H2** — the type name changes from `BINARY(16)` to `UUID`. A fresh schema is created correctly. An
    **existing H2 database with persisted rows** created by 4.0.0/4.0.1 will fail `ddl-auto=validate`
    after upgrading, because the stored column is `BINARY(16)` but the mapping now expects `UUID`. H2
    stores both as the same 16 bytes, so only the column *type* must be changed. Manual migration
    (run once against each affected H2 database, replacing `your_table`):

    ```sql
    ALTER TABLE your_table ALTER COLUMN id UUID;
    ```

    Repeat for every `UuidEntity` table (including join tables with a UUID FK). If you have no persisted
    H2 data (embedded/test databases recreated on boot), no action is required.

  Covered by `IntegrationTest.uuidIdMapsToNativeDialectType` (asserts the H2 column type is `UUID`, not
  `BINARY`, guarding against a regression to the hard-coded literal).

## [4.0.1] — 2026-08-06

### Fixed

- **fix(entity): make @Version column nullable so hbm2ddl=update can add it to populated tables.**
  `BaseEntity.version` was a primitive `int`, which maps to a `NOT NULL` column. When a consumer
  adopted `BaseEntity`/`LongIdEntity`/`UuidEntity`/`StringIdEntity` on an existing table with rows,
  `hbm2ddl.auto=update` emitted `add column version integer not null`, which fails the H2/MySQL table
  rebuild on the existing rows (Hibernate logged it as a WARNING and continued, leaving the column
  missing and later inserts/updates broken). The field is now `Integer` (nullable column) defaulted
  to `0`; the public accessor stays `int getVersion()` and is null-safe (rows predating the column
  read as `0`). No API change. `@Version` on `Integer` is permitted by the JPA spec. Optimistic
  locking, fresh-schema creation, and pre-existing stricter (`NOT NULL DEFAULT 0`) columns are
  unaffected — covered by `VersionMigrationTest`.

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
- **Audit trail via Hibernate Envers** (TODO-4, ADR-0005), opt-in:
  - Entities opt in with `@org.hibernate.envers.Audited`; `ConfigurationBuilder.enableAudit()`
    registers `JEHibernateRevisionEntity` (revision_id, timestamp, user_id, source_ip, tenant_id).
  - `AuditUserResolver` SPI + `AuditContext` holder; `JEHibernateRevisionListener` records the
    resolved user/IP and the current `TenantContext` tenant per revision.
  - Query API `Audit.getRevisions` / `getRevisionNumbers` / `getRevisionInfo`.
  - `hibernate-envers` is an optional (`compileOnly`) dependency; auto-activates when present.
  - `docs/audit-guide.md` with DSGVO retention/erasure guidance (Art. 5/17/32, § 26 BDSG).
    Note: no built-in purge job and no non-Envers listener hook (documented as out of scope).
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
- **Spring Boot auto-configuration** (`jehibernate-spring-boot`):
  - `JEHibernateAutoConfiguration` (`@AutoConfiguration`, after `DataSourceAutoConfiguration`)
    registers a `JEHibernate` bean that reuses the application `DataSource` (Spring's pool, never
    closed by JEHibernate) and a `RepositoryRegistry` bean.
  - `JEHibernateProperties` binds `jehibernate.*` (database, url, scan-packages, ddl-auto,
    migration-enabled, multi-tenancy, enabled). Registered via
    `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
  - Backs off when a `JEHibernate` bean is user-defined (`@ConditionalOnMissingBean`); disable via
    `jehibernate.enabled=false`. Verified by a Spring context test on H2 (no Docker).
  - `@JEHibernateRepositoryTest` test slice (analog of `@DataJpaTest`): boots a minimal context
    with an embedded DataSource + the JEHibernate auto-configuration; attributes
    (`database`, `ddlAuto`, `scanPackages`, `migrationEnabled`) map to `jehibernate.*` properties.
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
