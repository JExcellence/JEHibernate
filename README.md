<p align="center">
  <h1 align="center">JEHibernate</h1>
  <p align="center">
    Modern Hibernate/JPA utility library for Java 17+<br>
    Built for Minecraft plugins. Works everywhere.
  </p>
  <p align="center">
    <img src="https://img.shields.io/badge/Java-17%2B-orange" alt="Java 17+">
    <img src="https://img.shields.io/badge/Hibernate-7.x-59666C" alt="Hibernate 7.x">
    <img src="https://img.shields.io/maven-central/v/de.jexcellence.hibernate/jehibernate-core?label=Maven%20Central" alt="Maven Central">
    <img src="https://img.shields.io/badge/License-MIT-blue" alt="License: MIT">
  </p>
</p>

---

JEHibernate wraps Hibernate ORM with a fluent, type-safe repository API designed around the
Minecraft plugin lifecycle: async operations that never block the main thread, cached repositories
for fast player lookups, and session-scoped lazy loading. It runs anywhere Hibernate does, so the
same code works in a plugin, a Spring Boot service, or a standalone application.

**Runs on:** Spigot, Paper, Folia, Spring Boot, standalone Java applications.
**Requires:** Java 17+ (virtual threads auto-enabled on 21+), Hibernate 7.x, Jakarta Persistence 3.1+.

---

## Install

Replace `4.2.0` with the current version shown on the Maven Central badge above.

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("de.jexcellence.hibernate:jehibernate-core:4.2.0")

    // Pick your database driver
    runtimeOnly("com.h2database:h2:2.4.240")          // H2 (embedded, dev/testing)
    runtimeOnly("com.mysql:mysql-connector-j:9.3.0")  // MySQL
    runtimeOnly("org.postgresql:postgresql:42.7.7")   // PostgreSQL
}
```

### Maven

```xml
<dependency>
    <groupId>de.jexcellence.hibernate</groupId>
    <artifactId>jehibernate-core</artifactId>
    <version>4.2.0</version>
</dependency>
```

Multi-module projects can import the [BOM](#modules) (`jehibernate-bom`) to align every module on one
version.

---

## Quick Start

**1. Define an entity**

```java
@Entity
@Table(name = "players")
public class PlayerData extends UuidEntity {
    @Column(nullable = false)
    private String username;
    private long balance;

    protected PlayerData() {}

    public PlayerData(UUID uuid, String username) {
        setId(uuid);
        this.username = username;
    }

    public String getUsername() { return username; }
    public long getBalance()    { return balance; }
    public void setBalance(long balance) { this.balance = balance; }
}
```

**2. Define a repository**

```java
public class PlayerRepository extends AbstractCrudRepository<PlayerData, UUID> {
    public PlayerRepository(ExecutorService executor, EntityManagerFactory emf) {
        super(executor, emf);   // entity type inferred from the generic superclass
    }
}
```

**3. Save and load asynchronously**

```java
var je = JEHibernate.builder()
    .configuration(config -> config
        .database(DatabaseType.H2)
        .url("jdbc:h2:file:./plugins/MyPlugin/database/mydb")
        .credentials("sa", "")
        .ddlAuto("update"))
    .scanPackages("com.example.myplugin")
    .build();

var players = je.repositories().get(PlayerRepository.class);
var uuid = UUID.randomUUID();

// Save, then load — off the main thread, callbacks on completion
players.createAsync(new PlayerData(uuid, "alice"))
    .thenCompose(saved -> players.findByIdAsync(saved.getId()))
    .thenAccept(found -> found.ifPresent(p ->
        System.out.println("Loaded " + p.getUsername() + " (balance " + p.getBalance() + ")")));

je.close(); // on shutdown
```

Every repository method has a synchronous form (`create`, `findById`, `save`, …) and an async
`*Async` variant returning `CompletableFuture`. On Java 21+ the async variants run on virtual
threads; on Java 17 they use a cached thread pool.

For the full API — query builder, specifications, caching, transactions, dependency injection,
and platform integration — see the [API reference](docs/reference.md).

---

## Modules

Pick the modules you need:

| Module | Use it for |
|---|---|
| `jehibernate-core` | **Required.** The library — repositories, sessions, pooling, migration. |
| `jehibernate-spring-boot` | Spring Boot auto-configuration that reuses your `DataSource` bean. |
| `jehibernate-plugin` | Spigot/Paper `PropertyLoader` data-folder convenience. |
| `jehibernate-testing` | Testcontainers + fixtures (test scope only). |
| `jehibernate-bom` | Maven BOM that aligns the versions of all modules. |

---

## Configuration

### Builder API

```java
var jeHibernate = JEHibernate.builder()
    .configuration(config -> config
        .database(DatabaseType.POSTGRESQL)
        .url("jdbc:postgresql://localhost:5432/mydb")
        .credentials("user", "pass")
        .ddlAuto("validate")
        .batchSize(50)
        .showSql(true)
        .formatSql(true)
        .connectionPool(5, 20))
    .scanPackages("com.example")
    .build();
```

### Properties File

Load from filesystem, classpath, or a Bukkit plugin data folder:

```java
// Classpath or filesystem
var jeh = JEHibernate.fromProperties("hibernate.properties");

// Bukkit plugin data folder
var jeh = JEHibernate.fromProperties(getDataFolder(), "database", "hibernate.properties");
```

Place `hibernate.properties` in `src/main/resources/database/`:

```properties
# Database type. Supported: H2, MYSQL, MARIADB, POSTGRESQL, ORACLE, MSSQL_SERVER, SQLITE, HSQLDB
database.type=H2

# H2 (embedded, no external database needed)
h2.url=jdbc:h2:file:./plugins/MyPlugin/database/mydb;MODE=MySQL;AUTO_SERVER=TRUE
h2.username=sa
h2.password=

# MySQL (uncomment and set database.type=MYSQL)
# mysql.url=jdbc:mysql://localhost:3306/mydb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
# mysql.username=root
# mysql.password=change_me

# PostgreSQL (uncomment and set database.type=POSTGRESQL)
# postgresql.url=jdbc:postgresql://localhost:5432/mydb
# postgresql.username=postgres
# postgresql.password=change_me

# Hibernate settings
hibernate.hbm2ddl.auto=update
hibernate.show_sql=false
hibernate.format_sql=false
```

Server admins switch databases by changing `database.type` and uncommenting the relevant section —
no recompilation needed. All `hibernate.*` properties pass through directly to Hibernate.

Property format: `{prefix}.url`, `{prefix}.username`, `{prefix}.password`. Optionally
`{prefix}.driver` and `{prefix}.dialect` to override auto-detected defaults.

### Supported Databases

| Type | Prefix | Default Driver |
|------|--------|---------------|
| H2 | `h2` | `org.h2.Driver` |
| MySQL | `mysql` | `com.mysql.cj.jdbc.Driver` |
| MariaDB | `mariadb` | `org.mariadb.jdbc.Driver` |
| PostgreSQL | `postgresql` | `org.postgresql.Driver` |
| Oracle | `oracle` | `oracle.jdbc.OracleDriver` |
| SQL Server | `mssql` | `com.microsoft.sqlserver.jdbc.SQLServerDriver` |
| SQLite | `sqlite` | `org.sqlite.JDBC` |
| HSQLDB | `hsqldb` | `org.hsqldb.jdbc.JDBCDriver` |

### Connection Pooling

HikariCP is the default connection pool and is bundled with `jehibernate-core` — no extra
dependency needed. Size it via the builder:

```java
.connectionPool(5, 20)  // min 5, max 20 connections
```

For finer control (idle timeout, leak detection, max lifetime) pass a `PoolConfig`, and inspect the
pool at runtime via `jeHibernate.getPoolHealth()`.

### Second-Level Cache

Add a JCache provider to enable Hibernate's second-level cache:

```kotlin
implementation("org.hibernate.orm:hibernate-jcache")
```

```java
.enableSecondLevelCache()
```

---

## Entity Base Classes

All base classes provide automatic `createdAt` / `updatedAt` timestamps, optimistic locking via
`@Version`, and correct `equals` / `hashCode` (safe for `HashSet` / `HashMap` even before
persistence).

| Class | ID Type | Use Case |
|-------|---------|----------|
| `LongIdEntity` | Auto-increment `Long` | Most entities (warps, homes, shops) |
| `UuidEntity` | `UUID` (native per dialect: `uuid` / `binary(16)`) | Players, distributed systems |
| `StringIdEntity` | Custom `String` | Natural keys (world names, permission nodes) |

```java
@Entity
public class Warp extends LongIdEntity {
    private String name;
    private String world;
    private double x, y, z;
    // ...
}
```

---

## Spring Boot Starter

Add the starter — auto-configuration reuses your Spring `DataSource` and registers a `JEHibernate`
bean. No `@Bean` boilerplate needed:

```kotlin
implementation("de.jexcellence.hibernate:jehibernate-spring-boot:4.2.0")
```

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/app
    username: app
    password: secret
jehibernate:
  database: POSTGRESQL
  ddl-auto: validate
  scan-packages: [com.example.domain]
  migration-enabled: false   # let Spring Boot's own Flyway run migrations
```

```java
@Service
class UserService {
    private final UserRepository users;
    UserService(JEHibernate jeHibernate) {          // auto-configured bean
        this.users = jeHibernate.repositories().get(UserRepository.class);
    }
}
```

The auto-configuration activates when a `DataSource` bean is present, reuses it (JEHibernate never
closes Spring's pool), and is driven by `jehibernate.*` properties. To configure programmatically
instead, define your own `JEHibernate` `@Bean` (auto-config backs off via `@ConditionalOnMissingBean`).
Set `jehibernate.enabled=false` to disable it entirely. See the
[API reference](docs/reference.md#spring-boot-integration) for the programmatic path.

---

## Schema Migration

Migrations own the schema (`ddl-auto` defaults to `validate`). Flyway runs before Hibernate boots.

| Property | Default | Meaning |
|---|---|---|
| `jehibernate.migration.enabled` | `true` | Run migrations at bootstrap |
| `jehibernate.migration.tool` | `flyway` | `flyway` \| `liquibase` \| `none` |
| `jehibernate.migration.location` | `classpath:db/migration` | Flyway location / Liquibase changelog path |
| `jehibernate.pool.maximumPoolSize` | `10` | HikariCP max connections |
| `jehibernate.pool.minimumIdle` | `2` | HikariCP min idle connections |

Add `org.flywaydb:flyway-core` to the classpath to enable Flyway; omit it and migration is a silent
no-op. Put SQL files in `src/main/resources/db/migration/V001__init.sql`, `V002__...`, etc.

To keep the pre-4.0 `hbm2ddl.auto=update` behaviour instead of migrations:

```java
.configuration(c -> c.database(...).url(...).ddlAuto("update"))
// or set jehibernate.migration.enabled=false
```

---

## Multi-Tenancy (optional, off by default)

```java
// DISCRIMINATOR: a @TenantId column on entities; strict leak-guard throws if no tenant is bound.
config.multiTenancy(MultiTenancyConfig.discriminator());

try (var ignored = TenantContext.open("acme")) {
    noteRepo.findAll();   // sees only acme's data
}
```

Strategies: `DISCRIMINATOR` (row-level), `SCHEMA` (schema-per-tenant, shared pool), `DATABASE`
(database-per-tenant). Full details in [docs/multi-tenancy-guide.md](docs/multi-tenancy-guide.md).

---

## Documentation

- [API reference](docs/reference.md) — repository operations, query builder, specifications,
  caching, transactions, dependency injection, Bukkit/Paper and Spring Boot integration,
  logging, troubleshooting, and architecture.
- [Lazy-loading guide](docs/lazy-loading-guide.md) — session scoping vs. read-only vs. EntityGraph vs. OSIV.
- [Audit guide](docs/audit-guide.md) — Hibernate Envers audit trail, with DSGVO retention/erasure notes.
- [Multi-tenancy guide](docs/multi-tenancy-guide.md) — DISCRIMINATOR / SCHEMA / DATABASE strategies.
- [Testing guide](docs/testing-guide.md) — `@JEHibernateTest`, Testcontainers, fixtures.
- [Architecture decision records](docs/adr/) — the rationale behind the 4.0 design.
- [CHANGELOG](CHANGELOG.md) — release history and migration notes.
- [CONTRIBUTING](CONTRIBUTING.md) — build, test, and code-style guidance for contributors.

---

## Upgrading to 4.0

4.0 is a multi-module release. The key changes:

- **Published coordinate:** `de.jexcellence.hibernate:JEHibernate` → `de.jexcellence.hibernate:jehibernate-core`.
- **HikariCP** replaced Agroal as the default connection pool (bundled; `connectionPool(min, max)` unchanged).
- **`ddl-auto` defaults to `validate`** (was `update`) — migrations own the schema.
- **Flyway migrations** run before Hibernate boots; Liquibase is opt-in.
- **Audit trail** via Hibernate Envers (`@Audited` + `enableAudit()`) and **multi-tenancy** (DISCRIMINATOR/SCHEMA/DATABASE) added.

```kotlin
// before
implementation("de.jexcellence.hibernate:JEHibernate:3.0.4")
// after
implementation("de.jexcellence.hibernate:jehibernate-core:4.2.0")
implementation("org.flywaydb:flyway-core:11.1.1") // optional: enables migrations
```

See the [CHANGELOG](CHANGELOG.md) and [docs/adr/](docs/adr/) for the full migration notes.

---

## License

[MIT License](LICENSE)

Copyright (c) 2025 JExcellence
