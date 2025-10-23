```
# JEHibernate --- Agent Guide

## Purpose
Provide AI tools and agents with a concise, structured map of the JEHibernate project, including:
- Project architecture and entry points
- Key classes and their responsibilities
- Common usage patterns and conventions
- Environment-specific initialization (Spring, Spigot/Paper, Plain Java)
- Caching strategies and pitfalls
- Extension points and customization

---

## Project Overview

**JEHibernate** is a lightweight abstraction layer over JPA/Hibernate that provides:
1. Simplified EntityManagerFactory bootstrapping from properties files
2. Generic CRUD repositories with sync/async APIs
3. Optional Caffeine-backed caching repository for high-hit queries
4. Consistent naming strategies and type converters
5. Utilities for optimistic locking and entity creation

**Core Philosophy**: Minimize boilerplate, enforce patterns, provide sensible defaults.

---

## Primary Entry Points

### 1. JEHibernate (Main Bootstrap Class)
**Package**: `de.jexcellence.hibernate`

**Constructor**:
```java
JEHibernate(String filePath)

```

**Key Methods**:

-   `EntityManagerFactory getEntityManagerFactory()` --- Returns the initialized EMF
-   `void close()` --- Closes the EMF and releases resources

**Internals**:

-   Delegates to `DatabaseConnectionManager` to build the EMF
-   Uses `HibernateConfigManager` to load and validate properties
-   Supports multiple database types via `DatabaseType` enum

**Usage Pattern**:

```
JEHibernate je = new JEHibernate("path/to/hibernate.properties");
EntityManagerFactory emf = je.getEntityManagerFactory();
// ... use repositories ...
je.close();

```

* * * * *

Key Packages and Classes
------------------------

### de.jexcellence.hibernate

-   **JEHibernate**: Main bootstrap class

### de.jexcellence.hibernate.repository

-   **AbstractCRUDRepository<T, ID>**

    -   Generic base repository with sync and async CRUD operations
    -   Supports attribute-based queries and pagination
    -   Requires `ExecutorService` and `EntityManagerFactory` in constructor
    -   Methods: create, update, delete, findById, findAll, findByAttributes, findListByAttributes
    -   Async counterparts: createAsync, updateAsync, deleteAsync, findByIdAsync, findAllAsync, findByAttributesAsync, findListByAttributesAsync
-   **GenericCachedRepository<T, ID, K>**

    -   Extends AbstractCRUDRepository with Caffeine-based caching
    -   Type parameter K: cache key type (e.g., String for username)
    -   Requires `Function<T, K> keyExtractor` to derive cache keys from entities
    -   Methods: findByCacheKey, findByCacheKeyAsync, getCacheStats, clearCache, getCacheSize
    -   Cache policy: expireAfterWrite(30 minutes), maxSize(10000)
    -   Write-through on create/update; evict on delete

### de.jexcellence.hibernate.entity

-   **AbstractEntity**

    -   Base class for all entities
    -   Provides: id (Long), createdAt, updatedAt, version (for optimistic locking)
    -   Implements equals/hashCode based on id
-   **UUIDConverter**

    -   JPA attribute converter for UUID types
    -   Use with `@Convert(converter = UUIDConverter.class)` on UUID fields

### de.jexcellence.hibernate.config

-   **HibernateConfigManager**
    -   Loads properties from file path
    -   Validates required properties (JDBC URL, user, password)
    -   Returns a Properties object for EntityManagerFactory construction

### de.jexcellence.hibernate.util

-   **DatabaseConnectionManager**

    -   Builds EntityManagerFactory from properties
    -   Integrates with HibernateConfigManager
    -   Handles dialect selection and persistence provider setup
-   **DatabaseType**

    -   Enum for supported databases (H2, MySQL, PostgreSQL, etc.)
    -   Provides dialect hints and connection validation

### de.jexcellence.hibernate.naming

-   **JENamingStrategy**
    -   Physical naming strategy for Hibernate
    -   Maps ClassName → class_name, fieldName → field_name
    -   Configure via: `hibernate.physical_naming_strategy=de.jexcellence.hibernate.naming.JENamingStrategy`

### de.jexcellence.hibernate.locking

-   **OptimisticLockHandler**
    -   Utilities for handling OptimisticLockException
    -   Strategies: retry with backoff, or surface domain error

### de.jexcellence.hibernate.creation

-   **GenericEntityCreator**
    -   Helper for programmatic entity instantiation
    -   Useful for factory patterns and dynamic entity creation

* * * * *

Common Workflows
----------------

### Workflow 1: Bootstrap and Basic CRUD

```
1\. Create hibernate.properties with JDBC config
2. new JEHibernate(filePath) → EntityManagerFactory
3. Extend AbstractCRUDRepository<Entity, ID>
4. Inject ExecutorService + EMF into repository
5. Use sync/async methods for CRUD
6. Close EMF and shutdown executor on shutdown

```

### Workflow 2: Caching with GenericCachedRepository

```
1\. Define entity with unique identifier (e.g., username)
2. Create Function<Entity, K> keyExtractor (e.g., Entity::getUsername)
3. Extend GenericCachedRepository<Entity, ID, K>
4. Pass keyExtractor to super() constructor
5. Use findByCacheKey(queryAttribute, key) for cache-first reads
6. Cache auto-populates on create/update; auto-evicts on delete

```

### Workflow 3: Environment-Specific Initialization

#### Plain Java

```
ExecutorService executor = Executors.newFixedThreadPool(8);
JEHibernate je = new JEHibernate("config/hibernate.properties");
EntityManagerFactory emf = je.getEntityManagerFactory();
Repository repo = new Repository(executor, emf, Entity.class);
// ... use repo ...
je.close();
executor.shutdown();

```

#### Spring

```
@Configuration
public class HibernateConfig {
  @Bean @Primary
  public EntityManagerFactory entityManagerFactory() {
    return new JEHibernate(new ClassPathResource("hibernate.properties").getPath())
      .getEntityManagerFactory();
  }
  @Bean(destroyMethod = "shutdown")
  public ExecutorService dbExecutor() {
    return Executors.newFixedThreadPool(8);
  }
}

@Component
public class MyRepository extends GenericCachedRepository<Entity, Long, String> {
  public MyRepository(ExecutorService exec, EntityManagerFactory emf) {
    super(exec, emf, Entity.class, Entity::getUniqueField);
  }
}

```

#### Spigot/Paper

```
private EntityManagerFactory entityManagerFactory;
private ExecutorService dbExec;

@Override
public void onEnable() {
  dbExec = Executors.newFixedThreadPool(8);
  File databaseFolder = new File(getDataFolder(), "database");
  if (databaseFolder.exists() || databaseFolder.mkdirs()) {
    saveResource("database/hibernate.properties", false);
    File hibernateFile = new File(databaseFolder, "hibernate.properties");
    entityManagerFactory = new JEHibernate(hibernateFile.getPath()).getEntityManagerFactory();
  }
}

@Override
public void onDisable() {
  if (dbExec != null) dbExec.shutdown();
  if (entityManagerFactory != null) entityManagerFactory.close();
}

```

* * * * *

GenericCachedRepository Deep Dive
---------------------------------

### Constructor Signature

```
public GenericCachedRepository(ExecutorService executor,
                               EntityManagerFactory emf,
                               Class<T> entityClass,
                               Function<T, K> keyExtractor)

```

### Key Methods

`findByCacheKey(attr, key)`Cache-first; queries DB on missPopulates on miss`findByCacheKeyAsync(attr, key)`Async version of abovePopulates on miss`findAll(page, size)`Loads all entitiesPopulates all`findAllAsync(page, size)`Async versionPopulates all`create(entity)`Persists entityWrite-through`createAsync(entity)`Async versionWrite-through`update(entity)`Merges entityRefresh entry`updateAsync(entity)`Async versionRefresh entry`delete(id)`Deletes entityEvict by key`deleteAsync(id)`Async versionEvict by key`getCacheStats()`Returns cache statisticsN/A`clearCache()`Invalidates all entriesN/A`getCacheSize()`Returns entry countN/A

### Cache Consistency Rules

1.  **Write-Through on Create**: New entity is cached immediately after persist
2.  **Refresh on Update**: Updated entity replaces cache entry
3.  **Evict on Delete**: Cache entry is removed by extracted key
4.  **Populate on Query Miss**: findByCacheKey populates cache on DB hit
5.  **Batch Populate**: findAll populates cache with all fetched entities

### Choosing K and queryAttribute

**K (Cache Key Type)**:

-   Should uniquely identify an entity within your domain
-   Examples: String (username), UUID (externalId), Long (customId)
-   Must be hashable and immutable

**queryAttribute (Query Attribute Name)**:

-   The entity field name used to query on cache miss
-   Must match the attribute in the entity class
-   Example: "username" for `@Column(name = "username")`

**Example**:

```
// Entity
@Entity
public class Player extends AbstractEntity {
  private String username;
  public String getUsername() { return username; }
}

// Repository
public class PlayerRepository extends GenericCachedRepository<Player, Long, String> {
  public PlayerRepository(ExecutorService exec, EntityManagerFactory emf) {
    super(exec, emf, Player.class, Player::getUsername);
  }
}

// Usage
Player p = repo.findByCacheKey("username", "Alice"); // K=String, queryAttribute="username"

```

* * * * *

Conventions and Best Practices
------------------------------

### EntityManager Lifecycle

-   **Never share** EntityManager across threads
-   Each repository method creates its own EntityManager for that operation
-   Transactions are per-operation (implicit in repository methods)

### Async Execution

-   All async methods use the injected ExecutorService
-   Results are wrapped in CompletableFuture
-   Exceptions propagate via CompletableFuture.exceptionally()
-   Never block the calling thread; use thenAccept/thenApply for chaining

### Naming Strategy

-   Configure via: `hibernate.physical_naming_strategy=de.jexcellence.hibernate.naming.JENamingStrategy`
-   Enforces snake_case for table and column names
-   Ensure database schema matches physical naming

### Resource Management

-   Always close EntityManagerFactory on shutdown
-   Always shutdown ExecutorService (use `shutdown()` or `shutdownNow()`)
-   Use try-finally or try-with-resources where applicable

### Query Patterns

-   Prefer Criteria API for dynamic queries (used internally by AbstractCRUDRepository)
-   Attribute-based queries use exact-match by default
-   Extend repository with domain-specific query methods as needed

* * * * *

Pitfalls and Troubleshooting
----------------------------

### Cache Consistency Issues

**Problem**: Cache key doesn't match queryAttribute semantics **Solution**: Ensure keyExtractor(entity) produces the same value as the queryAttribute field

```
// WRONG: keyExtractor returns username, but queryAttribute is "email"
repo.findByCacheKey("email", "alice@example.com"); // Cache miss every time

// CORRECT: keyExtractor and queryAttribute align
repo.findByCacheKey("username", "alice");

```

### Stale Cache After Update

**Problem**: Entity's cache key changes after update (e.g., username changed) **Solution**: Repository handles typical cases; if key changes, manually evict old key

```
// Before update, evict old key if necessary
repo.clearCache(); // Nuclear option
// Or implement custom update logic

```

### EntityManager Thread Safety

**Problem**: Sharing EntityManager across async tasks **Solution**: Repository creates per-operation managers; never pass EntityManager between threads

### Configuration File Not Found

**Problem**: JEHibernate throws exception on missing properties file **Solution**: Verify file path is absolute or relative to working directory

```
// Use absolute path
new JEHibernate("/absolute/path/to/hibernate.properties");

// Or relative to classpath (Spring)
new JEHibernate(new ClassPathResource("hibernate.properties").getPath());

```

### No Entities Discovered

**Problem**: Hibernate doesn't find entity classes **Solution**: Ensure entities are annotated with @Entity and @Table; verify persistence.xml or property configuration includes entity packages

### Async Deadlocks

**Problem**: ExecutorService thread pool exhausted or blocked **Solution**: Use appropriately sized thread pool; avoid blocking operations in async chains

```
// Good: bounded pool for DB operations
ExecutorService dbExec = Executors.newFixedThreadPool(8);

// Bad: unbounded pool or blocking operations
ExecutorService badExec = Executors.newCachedThreadPool();

```

* * * * *

Extension Points
----------------

### Custom Repository Methods

Extend AbstractCRUDRepository or GenericCachedRepository with domain-specific queries:

```
public class PlayerRepository extends GenericCachedRepository<Player, Long, String> {
  public List<Player> findByLevel(int level) {
    return executeQuery(em -> {
      CriteriaBuilder cb = em.getCriteriaBuilder();
      CriteriaQuery<Player> cq = cb.createQuery(Player.class);
      Root<Player> root = cq.from(Player.class);
      cq.where(cb.equal(root.get("level"), level));
      return em.createQuery(cq).getResultList();
    });
  }
}

```

### Custom Naming Strategy

Implement `PhysicalNamingStrategy` and configure:

```
hibernate.physical_naming_strategy=com.example.CustomNamingStrategy

```

### Custom Type Converters

Implement `AttributeConverter<X, Y>` and annotate entity fields:

```
@Convert(converter = CustomConverter.class)
private CustomType field;

```

### Caching Decorator

Wrap GenericCachedRepository with additional caching layers (e.g., Redis):

```
public class RedisCachedRepository<T, ID, K> extends GenericCachedRepository<T, ID, K> {
  // Override methods to add Redis layer
}

```

* * * * *

Testing Guidance
----------------

### Test Setup

-   Use H2 in-memory database for fast tests
-   Place test properties under `src/test/resources/hibernate.properties`
-   Create fresh EntityManagerFactory per test or test class

### Example Test

```
@BeforeEach
public void setup() {
  JEHibernate je = new JEHibernate("src/test/resources/hibernate.properties");
  emf = je.getEntityManagerFactory();
  executor = Executors.newFixedThreadPool(2);
  repo = new TestRepository(executor, emf);
}

@AfterEach
public void teardown() {
  emf.close();
  executor.shutdown();
}

@Test
public void testCachedFind() {
  Player p = repo.findByCacheKey("username", "TestPlayer");
  assertNotNull(p);
}

```

* * * * *

Configuration Properties Reference
----------------------------------

### Required

-   `jakarta.persistence.jdbc.url` --- JDBC connection URL
-   `jakarta.persistence.jdbc.user` --- Database user
-   `jakarta.persistence.jdbc.password` --- Database password

### Common Optional

-   `hibernate.hbm2ddl.auto` --- Schema generation (create, update, validate, none)
-   `hibernate.show_sql` --- Log SQL statements
-   `hibernate.format_sql` --- Pretty-print SQL
-   `hibernate.highlight_sql` --- Colorize SQL in logs
-   `hibernate.physical_naming_strategy` --- Naming strategy class
-   `database.type` --- Database type hint (h2, mysql, postgres, etc.)
-   `database.host` --- Database host (for connection validation)
-   `database.port` --- Database port
-   `database.name` --- Database name

* * * * *

Integration Patterns
--------------------

### With Spring Data JPA

JEHibernate can coexist with Spring Data JPA; use JEHibernate for custom repositories and Spring Data for standard CRUD.

### With Dependency Injection

-   Spring: Use @Bean for EntityManagerFactory and ExecutorService
-   Spigot/Paper: Inject via constructor or service locator pattern
-   Plain Java: Manual instantiation and passing

### With Logging

-   Use `java.util.logging.Logger` or project's chosen logger
-   Repository methods log via inherited logger
-   Enable Hibernate SQL logging via properties

* * * * *

Versioning and Lifecycle
------------------------

**Current Version**: 1.0.0 (local build)

**Roadmap**:

-   Publish to Maven Central
-   Spring integration module
-   Second-level cache integration
-   Kotlin coroutines support
-   More database presets

**Maintenance**:

-   Keep README.md and AGENT.md in sync with code changes
-   Document new classes and methods in AGENT.md
-   Update examples when APIs change

* * * * *

Quick Reference: Common Tasks
-----------------------------

Bootstrap`new JEHibernate(path).getEntityManagerFactory()`Create repo`extends AbstractCRUDRepository<T, ID>`Create cached repo`extends GenericCachedRepository<T, ID, K>`Sync CRUD`repo.create(e)`, `repo.update(e)`, `repo.delete(id)`Async CRUD`repo.createAsync(e).thenAccept(...)`Cache-first read`repo.findByCacheKey("attr", key)`Clear cache`repo.clearCache()`Custom queryOverride `executeQuery(Function<EM, R>)`Optimistic lockCatch `OptimisticLockException`, retry or fail

* * * * *

Contact and Contribution
------------------------

For enhancements, bug reports, or questions:

1.  Update README.md with user-facing changes
2.  Update AGENT.md with architectural or API changes
3.  Include tests for new functionality
4.  Follow existing code style and conventions

* * * * *

**Last Updated**: 2024 **Maintained By**: JEHibernate Team

```

Done! I've created a comprehensive **AGENT.md** file that includes:

✅ **Project Overview** --- Clear purpose and philosophy
✅ **Primary Entry Points** --- JEHibernate bootstrap class
✅ **Key Packages & Classes** --- All major components with responsibilities
✅ **Common Workflows** --- Bootstrap, CRUD, Caching, Environment-specific init
✅ **GenericCachedRepository Deep Dive** --- Methods, cache consistency, K/queryAttribute selection
✅ **Conventions & Best Practices** --- EntityManager lifecycle, async patterns, naming strategy
✅ **Pitfalls & Troubleshooting** --- Common issues and solutions
✅ **Extension Points** --- Custom repositories, naming strategies, converters
✅ **Testing Guidance** --- H2 setup, example tests
✅ **Configuration Reference** --- All properties explained
✅ **Integration Patterns** --- Spring, Spigot/Paper, logging
✅ **Quick Reference Table** --- Common tasks at a glance

This AGENT.md is designed for AI tools to quickly understand the project structure, conventions, and how to navigate/extend it.
```