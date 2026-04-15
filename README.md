<p align="center">
  <h1 align="center">JEHibernate</h1>
  <p align="center">
    Modern Hibernate/JPA utility library for Java 17+<br>
    Built for Minecraft plugins. Works everywhere.
  </p>
  <p align="center">
    <img src="https://img.shields.io/badge/Java-17%2B-orange" alt="Java 17+">
    <img src="https://img.shields.io/badge/Hibernate-7.x-59666C" alt="Hibernate 7.x">
    <img src="https://img.shields.io/badge/Tests-78%20passing-brightgreen" alt="Tests">
    <img src="https://img.shields.io/badge/License-Apache%202.0-blue" alt="License">
  </p>
</p>

---

JEHibernate wraps Hibernate ORM with a clean, fluent API that eliminates 65%+ of database boilerplate. Designed around the Minecraft plugin lifecycle -- async operations that never block the main thread, cached repositories for instant player lookups, and session-scoped lazy loading that actually works.

**Runs on:** Spigot, Paper, Folia, Spring Boot, standalone Java applications.
**Requires:** Java 17+ (virtual threads auto-enabled on 21+). Hibernate 7.x, Jakarta Persistence 3.1+.

## Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
  - [Builder API](#builder-api)
  - [Properties File](#properties-file)
  - [Supported Databases](#supported-databases)
  - [Connection Pooling](#connection-pooling)
  - [Second-Level Cache](#second-level-cache)
- [Entity Base Classes](#entity-base-classes)
- [Repository Operations](#repository-operations)
  - [CRUD](#crud)
  - [Batch Operations](#batch-operations)
  - [Async Operations](#async-operations)
- [Session-Scoped Operations](#session-scoped-operations)
- [Query Builder](#query-builder)
  - [Filtering](#filtering)
  - [OR Conditions](#or-conditions)
  - [Sorting](#sorting)
  - [Pagination](#pagination)
  - [Fetch Joins](#fetch-joins)
  - [Streaming](#streaming)
- [Specifications](#specifications)
- [Caching](#caching)
- [Transaction Management](#transaction-management)
- [Dependency Injection](#dependency-injection)
- [Bukkit / Paper Integration](#bukkit--paper-integration)
- [Spring Boot Integration](#spring-boot-integration)
- [Logging](#logging)
- [Troubleshooting](#troubleshooting)
- [Architecture](#architecture)
- [License](#license)

---

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("de.jexcellence.hibernate:JEHibernate:3.0.0")

    // Pick your database driver
    runtimeOnly("com.h2database:h2:2.4.240")         // H2 (embedded, dev/testing)
    runtimeOnly("com.mysql:mysql-connector-j:9.3.0")  // MySQL
    runtimeOnly("org.postgresql:postgresql:42.7.7")    // PostgreSQL
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'de.jexcellence.hibernate:JEHibernate:3.0.0'
    runtimeOnly 'com.h2database:h2:2.4.240'
}
```

### Maven

```xml
<dependency>
    <groupId>de.jexcellence.hibernate</groupId>
    <artifactId>JEHibernate</artifactId>
    <version>3.0.0</version>
</dependency>
```

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

    // getters + setters
}
```

**2. Define a repository**

```java
public class PlayerRepository extends AbstractCrudRepository<PlayerData, UUID> {
    public PlayerRepository(ExecutorService executor, EntityManagerFactory emf, Class<PlayerData> entityClass) {
        super(executor, emf, entityClass);
    }
}
```

**3. Initialize and use**

```java
var jeHibernate = JEHibernate.builder()
    .configuration(config -> config
        .database(DatabaseType.H2)
        .url("jdbc:h2:file:./plugins/MyPlugin/database/mydb")
        .credentials("sa", "")
        .ddlAuto("update"))
    .scanPackages("com.example.myplugin")
    .build();

var playerRepo = jeHibernate.repositories().get(PlayerRepository.class);

// Create
var player = playerRepo.create(new PlayerData(uuid, "alice"));

// Read
var found = playerRepo.findByIdOrThrow(uuid);

// Update
player.setBalance(1000);
playerRepo.save(player);

// Async (never blocks the main thread)
playerRepo.findByIdAsync(uuid).thenAccept(opt -> { ... });

// Shutdown
jeHibernate.close();
```

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
# ============================================
# Database Type
# ============================================
# Supported: H2, MYSQL, MARIADB, POSTGRESQL, ORACLE, MSSQL_SERVER, SQLITE, HSQLDB
database.type=H2

# ============================================
# H2 (embedded, no external database needed)
# ============================================
h2.url=jdbc:h2:file:./plugins/MyPlugin/database/mydb;MODE=MySQL;AUTO_SERVER=TRUE
h2.username=sa
h2.password=
# h2.driver=org.h2.Driver            # optional, auto-detected
# h2.dialect=org.hibernate.dialect.H2Dialect

# ============================================
# MySQL (uncomment and set database.type=MYSQL)
# ============================================
# mysql.url=jdbc:mysql://localhost:3306/mydb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
# mysql.username=root
# mysql.password=change_me

# ============================================
# PostgreSQL (uncomment and set database.type=POSTGRESQL)
# ============================================
# postgresql.url=jdbc:postgresql://localhost:5432/mydb
# postgresql.username=postgres
# postgresql.password=change_me

# ============================================
# Hibernate Settings
# ============================================
hibernate.hbm2ddl.auto=update
hibernate.show_sql=false
hibernate.format_sql=false
```

Server admins switch databases by changing `database.type` and uncommenting the relevant section. No recompilation needed. All `hibernate.*` properties pass through directly to Hibernate.

Property format: `{prefix}.url`, `{prefix}.username`, `{prefix}.password`. Optionally `{prefix}.driver` and `{prefix}.dialect` to override auto-detected defaults.

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

Add Agroal to your dependencies for production connection pooling:

```kotlin
implementation("org.hibernate.orm:hibernate-agroal")
implementation("io.agroal:agroal-pool:2.5")
```

```java
.connectionPool(5, 20)  // min 5, max 20 connections
```

### Second-Level Cache

```kotlin
implementation("org.hibernate.orm:hibernate-jcache")
```

```java
.enableSecondLevelCache()
```

---

## Entity Base Classes

All base classes provide automatic `createdAt` / `updatedAt` timestamps, optimistic locking via `@Version`, and correct `equals` / `hashCode` (safe for `HashSet` / `HashMap` even before persistence).

| Class | ID Type | Use Case |
|-------|---------|----------|
| `LongIdEntity` | Auto-increment `Long` | Most entities (warps, homes, shops) |
| `UuidEntity` | `UUID` stored as `BINARY(16)` | Players, distributed systems |
| `StringIdEntity` | Custom `String` | Natural keys (world names, permission nodes) |

```java
@Entity
public class Warp extends LongIdEntity {
    private String name;
    private String world;
    private double x, y, z;
    // ...
}

@Entity
public class PlayerData extends UuidEntity {
    private String username;
    private long balance;
    // ...
}
```

---

## Repository Operations

### CRUD

```java
// Create
var player = repo.create(new PlayerData(uuid, "alice"));

// Read
Optional<PlayerData> found = repo.findById(uuid);
PlayerData player = repo.findByIdOrThrow(uuid);
PlayerData player = repo.findByIdOrCreate(uuid, () -> new PlayerData(uuid, "alice"));
List<PlayerData> all = repo.findAll();

// Save (creates if new, updates if existing)
repo.save(player);

// Update
player.setBalance(1000);
repo.update(player);

// Delete
repo.delete(uuid);
repo.deleteEntity(player);

// Refresh (discard in-memory changes, re-read from DB)
repo.refresh(player);

// Existence + Count
boolean exists = repo.exists(uuid);
long count = repo.count();
```

### Batch Operations

```java
repo.createAll(List.of(player1, player2, player3));
repo.saveAll(playerList);      // batch create + update in one call
repo.updateAll(playerList);
repo.deleteAll(List.of(uuid1, uuid2, uuid3));
repo.findAllById(List.of(uuid1, uuid2));
```

### Async Operations

Every method has an async variant returning `CompletableFuture`. On Java 21+ these run on virtual threads; on Java 17 they use a cached thread pool.

```java
repo.createAsync(player).thenAccept(p -> log("Created: " + p.getId()));
repo.findByIdAsync(uuid).thenAccept(opt -> opt.ifPresent(this::greet));
repo.saveAllAsync(players).join();
```

---

## Session-Scoped Operations

Without session scoping, each repository call opens and closes its own `EntityManager`. Entities are immediately detached, and lazy-loaded collections throw `LazyInitializationException`.

`withSession` keeps the EntityManager open for the entire callback:

```java
// Transactional -- lazy loading works, changes are committed
repo.withSession(session -> {
    PlayerData player = session.find(PlayerData.class, uuid).orElseThrow();
    player.getInventory().size();  // lazy collection -- works!

    player.setBalance(500);
    session.merge(player);
    return player;
});

// Read-only -- no transaction overhead
repo.withReadOnly(session -> {
    PlayerData player = session.find(PlayerData.class, uuid).orElseThrow();
    return new ArrayList<>(player.getFriends());
});

// Repository-agnostic (via JEHibernate entry point)
jeHibernate.withSession(session -> {
    var player = session.find(PlayerData.class, uuid).orElseThrow();
    var warps = session.query(Warp.class).and("owner", uuid).list();
    return warps;
});
```

---

## Query Builder

Type-safe, fluent queries. No SQL, no JPQL.

### Filtering

```java
var results = repo.query()
    .and("active", true)                        // equality
    .like("username", "%alice%")                 // LIKE
    .greaterThan("balance", 100)                 // comparisons
    .lessThanOrEqual("level", 50)
    .between("createdAt", startDate, endDate)    // range
    .in("rank", List.of("VIP", "ADMIN"))         // IN clause
    .isNotNull("lastLogin")                      // null checks
    .notEqual("status", "BANNED")
    .list();
```

### OR Conditions

```java
var results = repo.query()
    .and("active", true)
    .or("rank", "ADMIN")
    .or("rank", "MODERATOR")
    .list();
// WHERE active = true AND (rank = 'ADMIN' OR rank = 'MODERATOR')
```

### Sorting

Multiple sort fields are supported. Calls accumulate.

```java
var results = repo.query()
    .orderByDesc("balance")
    .orderBy("username")
    .list();
```

### Pagination

```java
// Simple
List<PlayerData> page1 = repo.findAll(0, 20);

// Rich metadata (count + data in one session for consistency)
PageResult<PlayerData> page = repo.query()
    .and("active", true)
    .orderByDesc("balance")
    .getPage(0, 20);

page.content();        // List<PlayerData>
page.totalElements();  // total matching count
page.totalPages();     // total pages
page.hasNext();        // more pages?
page.hasPrevious();
page.isFirst();
page.isLast();
```

### Fetch Joins

Prevent N+1 queries by loading associations in the same SQL query:

```java
var results = repo.query()
    .fetch("inventory")       // INNER JOIN FETCH
    .fetchLeft("guild")       // LEFT JOIN FETCH (nullable)
    .and("active", true)
    .list();
```

### Streaming

Process large datasets without loading everything into memory:

```java
// MUST use try-with-resources
try (var stream = repo.query().and("active", true).stream()) {
    stream.filter(p -> p.getBalance() > 10_000)
          .forEach(this::processRichPlayer);
}
```

### Async Queries

```java
repo.query().and("active", true).listAsync()
    .thenAccept(players -> log("Found " + players.size()));

repo.query().getPageAsync(0, 20)
    .thenAccept(page -> log("Total: " + page.totalElements()));
```

---

## Specifications

Reusable, composable query predicates:

```java
Specification<PlayerData> richActive = Specifications.<PlayerData>equal("active", true)
    .and(Specifications.greaterThan("balance", 10_000));

List<PlayerData> players = repo.findAll(richActive);
long count = repo.count(richActive);
boolean any = repo.existsBy(richActive);
Optional<PlayerData> one = repo.findOne(richActive);
```

Supports: `equal`, `notEqual`, `like`, `in`, `isNull`, `isNotNull`, `greaterThan`, `lessThan`, `greaterThanOrEqual`, `lessThanOrEqual`, `between`. Nested properties work with dot notation: `"user.address.city"`.

---

## Caching

Extend `AbstractCachedRepository` for dual-layer Caffeine caching (by ID and by custom key):

```java
public class PlayerRepository extends AbstractCachedRepository<PlayerData, UUID, String> {
    public PlayerRepository(ExecutorService ex, EntityManagerFactory emf, Class<PlayerData> cls) {
        super(ex, emf, cls,
            PlayerData::getUsername,   // cache key extractor
            CacheConfig.builder()
                .expiration(Duration.ofMinutes(30))
                .maxSize(5000)
                .expireAfterAccess(true)
                .build());
    }
}
```

```java
// Cache lookups
repo.findByKey("alice");                                            // memory only
repo.findByKey("username", "alice");                                // DB fallback
repo.getOrCreate("username", "alice", k -> new PlayerData(uuid, k));// get or create

// Eviction
repo.evict(player);
repo.evictById(uuid);
repo.evictByKey("alice");
repo.evictAll();

// Preloading (warm cache on startup)
repo.preloadAsync();

// Stats
CacheStats stats = repo.getKeyCacheStats();
long size = repo.getCacheSize();
```

All mutations (create, update, save, delete) automatically maintain cache consistency.

---

## Transaction Management

### TransactionTemplate

```java
TransactionTemplate tx = jeHibernate.transactionTemplate();

// Transactional
PlayerData player = tx.execute(em -> {
    var p = em.find(PlayerData.class, uuid);
    p.setBalance(p.getBalance() + 100);
    return em.merge(p);
});

// Read-only (no transaction overhead)
List<PlayerData> top = tx.executeReadOnly(em ->
    em.createQuery("SELECT p FROM PlayerData p ORDER BY p.balance DESC", PlayerData.class)
      .setMaxResults(10)
      .getResultList());
```

### Optimistic Lock Retry

Safely handle concurrent modifications with automatic retry and exponential backoff:

```java
// Default: 3 retries, 100ms backoff
OptimisticLockRetry.execute(() -> {
    var p = repo.findByIdOrThrow(uuid);
    p.setBalance(p.getBalance() + amount);
    return repo.save(p);
});

// Custom: 5 retries, 200ms backoff, also retry deadlocks
OptimisticLockRetry.execute(
    () -> transferBalance(from, to, amount),
    5, Duration.ofMillis(200), true
);

// Void
OptimisticLockRetry.executeVoid(() -> {
    var p = repo.findByIdOrThrow(uuid);
    p.setBalance(p.getBalance() + amount);
    repo.save(p);
});
```

Catches `OptimisticLockException`, `StaleObjectStateException`, `StaleStateException`, and optionally `LockAcquisitionException` (deadlocks) anywhere in the cause chain.

---

## Dependency Injection

```java
public class EconomyService {
    @Inject private PlayerRepository playerRepo;
    @Inject private WarpRepository warpRepo;

    public void transfer(UUID from, UUID to, long amount) { ... }
}

// Create with auto-injection
var service = jeHibernate.repositories().createWithInjection(EconomyService.class);

// Or inject into existing instance
var service = new EconomyService();
jeHibernate.repositories().injectInto(service);
```

---

## Bukkit / Paper Integration

### Recommended Setup (Properties File)

```
your-plugin/
  src/main/resources/
    plugin.yml
    database/
      hibernate.properties
    simplelogger.properties
```

```java
public class MyPlugin extends JavaPlugin {
    private JEHibernate jeHibernate;

    @Override
    public void onEnable() {
        saveResource("database/hibernate.properties", false);

        jeHibernate = JEHibernate.builder()
            .configuration(config -> config.fromProperties(
                PropertyLoader.load(getDataFolder(), "database", "hibernate.properties")))
            .scanPackages("com.example.myplugin")
            .build();

        var playerRepo = jeHibernate.repositories().get(PlayerRepository.class);
        playerRepo.preloadAsync();
    }

    @Override
    public void onDisable() {
        jeHibernate.close();
    }
}
```

### Main Thread Safety

Never block the Bukkit main thread with database calls. Use async methods:

```java
playerRepo.findByIdAsync(uuid).thenAccept(opt ->
    opt.ifPresent(player ->
        Bukkit.getScheduler().runTask(plugin, () ->
            player.sendMessage("Balance: " + player.getBalance())
        )
    )
);
```

### Builder API (Alternative)

```java
jeHibernate = JEHibernate.builder()
    .configuration(config -> config
        .database(DatabaseType.MYSQL)
        .url("jdbc:mysql://localhost:3306/minecraft")
        .credentials("mc", "secret")
        .ddlAuto("update")
        .connectionPool(2, 10))
    .scanPackages("com.example.myplugin")
    .build();
```

---

## Spring Boot Integration

```java
@Configuration
public class JEHibernateConfig {

    @Bean
    public JEHibernate jeHibernate() {
        return JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.POSTGRESQL)
                .url("jdbc:postgresql://localhost:5432/mydb")
                .credentials("user", "pass")
                .ddlAuto("validate")
                .connectionPool(5, 20))
            .scanPackages("com.example")
            .build();
    }

    @Bean
    public PlayerRepository playerRepository(JEHibernate jeh) {
        return jeh.repositories().get(PlayerRepository.class);
    }

    @PreDestroy
    public void shutdown(JEHibernate jeh) {
        jeh.close();
    }
}
```

Or load from a properties file:

```java
@Bean
public JEHibernate jeHibernate() {
    return JEHibernate.fromProperties("config/hibernate.properties");
}
```

---

## Logging

JEHibernate uses SLF4J. You need an implementation on your classpath.

### Bukkit / Paper (slf4j-simple)

```kotlin
implementation("org.slf4j:slf4j-simple:2.0.16")
```

Create `simplelogger.properties` in `src/main/resources/`:

```properties
org.slf4j.simpleLogger.defaultLogLevel=info
org.slf4j.simpleLogger.log.de.jexcellence.jehibernate=info
org.slf4j.simpleLogger.log.org.hibernate.SQL=warn
org.slf4j.simpleLogger.log.org.hibernate.orm.jdbc.bind=warn
org.slf4j.simpleLogger.showDateTime=true
org.slf4j.simpleLogger.dateTimeFormat=yyyy-MM-dd HH:mm:ss
org.slf4j.simpleLogger.showThreadName=true
org.slf4j.simpleLogger.showShortLogName=true
```

### Spring Boot (Logback, already included)

```xml
<logger name="de.jexcellence.jehibernate" level="INFO"/>
<logger name="org.hibernate.SQL" level="WARN"/>
```

### Slow Query Detection

Queries exceeding 500ms are automatically logged at WARN level.

```java
QueryLogger.setSlowQueryThreshold(1000); // customize to 1 second
```

---

## Troubleshooting

### LazyInitializationException

Happens when accessing a lazy collection after the EntityManager is closed.

```java
// WRONG
var player = repo.findByIdOrThrow(uuid);
player.getInventory().size();  // LazyInitializationException

// FIX 1: session scope
repo.withSession(session -> {
    var p = session.find(PlayerData.class, uuid).orElseThrow();
    return p.getInventory().size();  // works
});

// FIX 2: fetch join
repo.query().fetch("inventory").and("id", uuid).first();
```

### OptimisticLockException

Two threads updated the same entity. Wrap in retry:

```java
OptimisticLockRetry.execute(() -> {
    var p = repo.findByIdOrThrow(uuid);
    p.setBalance(p.getBalance() + amount);
    return repo.save(p);
});
```

### Slow Queries

JEHibernate logs queries over 500ms at WARN level. Enable Hibernate SQL logging to see the generated SQL:

```properties
hibernate.show_sql=true
hibernate.format_sql=true
```

### Database Not Found / Connection Refused

Check that your database driver is on the classpath and the URL, credentials, and port are correct. For H2 file mode, ensure the plugin data directory exists.

---

## Architecture

```
Repository Hierarchy (Sealed Interfaces)

Repository<T, ID>                   findById, findAll, save, delete, count
    |
CrudRepository<T, ID>              create, update, batch ops, pagination
    |
AsyncRepository<T, ID>             CompletableFuture variants of everything
    |
QueryableRepository<T, ID>         query(), findOne/findAll/count with Spec
    |
AbstractCrudRepository<T, ID>      Full implementation + session scoping
    |
AbstractCachedRepository<T, ID, K> Dual-layer Caffeine caching
```

```
Package Structure

de.jexcellence.jehibernate
  config/         ConfigurationBuilder, DatabaseConfig, DatabaseType, PropertyLoader
  core/           JEHibernate (main entry point)
  entity/base/    BaseEntity, LongIdEntity, UuidEntity, StringIdEntity, Identifiable
  converter/      UuidConverter, InstantConverter
  exception/      TransactionException, EntityNotFoundException, ValidationException, ...
  logging/        QueryLogger
  naming/         SnakeCaseStrategy (camelCase -> snake_case)
  repository/
    base/         Repository hierarchy + AbstractCrudRepository + AbstractCachedRepository
    query/        QueryBuilder, PageResult, Specification, Specifications
    injection/    @Inject, InjectionProcessor
    manager/      RepositoryFactory, RepositoryRegistry
  scanner/        EntityScanner, RepositoryScanner
  session/        SessionContext
  transaction/    TransactionTemplate, OptimisticLockRetry
```

---

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

Copyright 2024 JExcellence
