# JEHibernate

A lightweight utility library that standardizes JPA/Hibernate bootstrapping, configuration, and repository patterns with clean sync/async APIs. It includes:
- EntityManagerFactory lifecycle management
- Generic CRUD repositories with async support
- Optional Caffeine-backed caching repository (GenericCachedRepository)
- Consistent naming strategy and converters
- Utilities for optimistic locking and entity creation

## Contents
- Features
- Installation
- Quick Start
- Configuration
- Repository APIs
- GenericCachedRepository Source Code & Implementation
- Caching with GenericCachedRepository
- Examples by Environment
  - Plain Java
  - Spring (exact initialization)
  - Spigot/Paper (exact initialization)
- Naming Strategy
- Optimistic Locking
- Testing
- Troubleshooting
- Contributing
- License

## Features
- Bootstrapping
  - JEHibernate for easy EntityManagerFactory lifecycle
  - DatabaseConnectionManager + HibernateConfigManager for property-driven initialization
- Repositories
  - AbstractCRUDRepository<T, ID>: sync + async CRUD, attribute-based queries, pagination
  - GenericCachedRepository<T, ID, K>: Caffeine cache with keyExtractor for high-hit queries
- Entities
  - AbstractEntity base (id, timestamps, version)
  - UUIDConverter for UUIDs
- Conventions
  - JENamingStrategy for consistent table/column naming
- Utilities
  - OptimisticLockHandler for version conflict strategies
  - GenericEntityCreator for programmatic entity creation

## Installation
Use local build for now.

### Gradle (Kotlin DSL):
```gradle
repositories {
  mavenLocal()
  mavenCentral()
}
dependencies {
  implementation("de.jexcellence.hibernate:jehibernate:1.0.0")
}

```

Publish locally:

```gradle
./gradlew clean build publishToMavenLocal
```

### Maven:

```xml
<dependency>
  <groupId>de.jexcellence.hibernate</groupId>
  <artifactId>jehibernate</artifactId>
  <version>1.0.0</version>
</dependency>
```

Quick Start
-----------

### 1) Create a hibernate.properties (H2 example):

```properties
jakarta.persistence.jdbc.url=jdbc:h2:mem:testdb;MODE=LEGACY;DB_CLOSE_DELAY=-1
jakarta.persistence.jdbc.user=sa
jakarta.persistence.jdbc.password=
hibernate.hbm2ddl.auto=update
hibernate.show_sql=true
hibernate.format_sql=true
hibernate.highlight_sql=true
# hibernate.physical_naming_strategy=de.jexcellence.hibernate.naming.JENamingStrategy
```

### 2) Bootstrap:

```java
import de.jexcellence.hibernate.JEHibernate;
import jakarta.persistence.EntityManagerFactory;

JEHibernate je = new JEHibernate("config/hibernate.properties");
EntityManagerFactory emf = je.getEntityManagerFactory();
```

### 3) Create a repository:

-   Extend AbstractCRUDRepository for your entity or use GenericCachedRepository to add caching.

Configuration
-------------

-   HibernateConfigManager loads and validates properties.
-   DatabaseConnectionManager builds the EntityManagerFactory using the configured persistence provider.
-   DatabaseType can help with DB presets if configured.

Common properties:

-   jakarta.persistence.jdbc.url
-   jakarta.persistence.jdbc.user
-   jakarta.persistence.jdbc.password
-   hibernate.hbm2ddl.auto or schema-generation alternatives

Repository APIs
---------------

AbstractCRUDRepository<T, ID> exposes (typical):

### Sync:

-   T create(T entity)
-   T update(T entity)
-   boolean delete(ID id)
-   T findById(ID id)
-   List findAll(int pageNumber, int pageSize)
-   T findByAttributes(Map<String, Object> attributes)
-   List findListByAttributes(Map<String, Object> attributes)

### Async (CompletableFuture):

-   createAsync, updateAsync, deleteAsync, findByIdAsync, findAllAsync, findByAttributesAsync, findListByAttributesAsync

ExecutorService is injected into the repository and used for async methods.

GenericCachedRepository Source Code & Implementation
----------------------------------------------------

### Full GenericCachedRepository.java Source

```java
package de.jexcellence.hibernate.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * GenericCachedRepository extends AbstractCRUDRepository with Caffeine-based caching.
 *
 * Type parameters:
 * - T: Entity type
 * - ID: Primary key type
 * - K: Cache key type (derived from entity via keyExtractor)
 *
 * The cache is keyed by K (e.g., username, externalId) and stores entities for fast retrieval.
 * On cache miss, queries fall back to the database and populate the cache.
 */
public class GenericCachedRepository<T, ID, K> extends AbstractCRUDRepository<T, ID> {

    private final Cache<K, T> cache;
    private final Function<T, K> keyExtractor;

    /**
     * Constructor.
     *
     * @param executor ExecutorService for async operations
     * @param emf EntityManagerFactory
     * @param entityClass Entity class
     * @param keyExtractor Function to extract cache key K from entity T
     */
    public GenericCachedRepository(ExecutorService executor,
                                   EntityManagerFactory emf,
                                   Class<T> entityClass,
                                   Function<T, K> keyExtractor) {
        super(executor, emf, entityClass);
        this.keyExtractor = keyExtractor;

        // Initialize Caffeine cache with 30-minute expiration
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();
    }

    /**
     * Find entity by cache key. Cache-first strategy: checks cache, falls back to DB query.
     *
     * @param queryAttribute The entity attribute name to query (e.g., "username")
     * @param key The cache key value
     * @return Entity if found, null otherwise
     */
    public T findByCacheKey(String queryAttribute, K key) {
        // Check cache first
        T cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        // Cache miss: query database
        Map<String, Object> attributes = Map.of(queryAttribute, key);
        T entity = findByAttributes(attributes);

        // Populate cache if found
        if (entity != null) {
            cache.put(key, entity);
        }

        return entity;
    }

    /**
     * Async version of findByCacheKey.
     *
     * @param queryAttribute The entity attribute name to query
     * @param key The cache key value
     * @return CompletableFuture with entity or null
     */
    public CompletableFuture<T> findByCacheKeyAsync(String queryAttribute, K key) {
        return CompletableFuture.supplyAsync(() -> findByCacheKey(queryAttribute, key), executor);
    }

    /**
     * Override findAll to populate cache with all entities.
     *
     * @param pageNumber Page number (0-based)
     * @param pageSize Page size
     * @return List of entities (cached)
     */
    @Override
    public List<T> findAll(int pageNumber, int pageSize) {
        List<T> entities = super.findAll(pageNumber, pageSize);

        // Populate cache with all fetched entities
        for (T entity : entities) {
            K key = keyExtractor.apply(entity);
            cache.put(key, entity);
        }

        return entities;
    }

    /**
     * Async version of findAll with caching.
     *
     * @param pageNumber Page number
     * @param pageSize Page size
     * @return CompletableFuture with list of entities
     */
    public CompletableFuture<List<T>> findAllAsync(int pageNumber, int pageSize) {
        return CompletableFuture.supplyAsync(() -> findAll(pageNumber, pageSize), executor);
    }

    /**
     * Override create to cache the new entity.
     *
     * @param entity Entity to create
     * @return Created entity (cached)
     */
    @Override
    public T create(T entity) {
        T created = super.create(entity);
        K key = keyExtractor.apply(created);
        cache.put(key, created);
        return created;
    }

    /**
     * Async version of create with caching.
     *
     * @param entity Entity to create
     * @return CompletableFuture with created entity
     */
    @Override
    public CompletableFuture<T> createAsync(T entity) {
        return CompletableFuture.supplyAsync(() -> create(entity), executor);
    }

    /**
     * Override update to refresh cache entry.
     *
     * @param entity Entity to update
     * @return Updated entity (cache refreshed)
     */
    @Override
    public T update(T entity) {
        T updated = super.update(entity);
        K key = keyExtractor.apply(updated);
        cache.put(key, updated);
        return updated;
    }

    /**
     * Async version of update with cache refresh.
     *
     * @param entity Entity to update
     * @return CompletableFuture with updated entity
     */
    @Override
    public CompletableFuture<T> updateAsync(T entity) {
        return CompletableFuture.supplyAsync(() -> update(entity), executor);
    }

    /**
     * Override delete to evict from cache.
     *
     * @param id Entity ID to delete
     * @return true if deleted, false otherwise
     */
    @Override
    public boolean delete(ID id) {
        // Fetch entity to get cache key before deletion
        T entity = findById(id);
        boolean deleted = super.delete(id);

        if (deleted && entity != null) {
            K key = keyExtractor.apply(entity);
            cache.invalidate(key);
        }

        return deleted;
    }

    /**
     * Async version of delete with cache eviction.
     *
     * @param id Entity ID to delete
     * @return CompletableFuture with deletion result
     */
    @Override
    public CompletableFuture<Boolean> deleteAsync(ID id) {
        return CompletableFuture.supplyAsync(() -> delete(id), executor);
    }

    /**
     * Get cache statistics (optional, for monitoring).
     *
     * @return Cache stats string
     */
    public String getCacheStats() {
        return cache.stats().toString();
    }

    /**
     * Clear all cache entries.
     */
    public void clearCache() {
        cache.invalidateAll();
    }

    /**
     * Get current cache size.
     *
     * @return Number of cached entries
     */
    public long getCacheSize() {
        return cache.size();
    }
}
```

### Key Features of GenericCachedRepository:

1.  **Cache-First Strategy**: `findByCacheKey()` checks cache before querying the database
2.  **Automatic Population**: `findAll()` populates cache with all fetched entities
3.  **Write-Through**: `create()` and `update()` automatically update cache entries
4.  **Eviction on Delete**: `delete()` removes entries from cache
5.  **Async Support**: All methods have async counterparts using CompletableFuture
6.  **Caffeine Integration**: Uses Caffeine cache with 30-minute TTL and 10k max size
7.  **Key Extraction**: Uses a `Function<T, K>` to derive cache keys from entities

Caching with GenericCachedRepository
------------------------------------

GenericCachedRepository<T, ID, K> extends AbstractCRUDRepository and introduces a Caffeine cache keyed by a domain-specific key K derived via a keyExtractor.

### Constructor:

```java
new GenericCachedRepository<>(
    ExecutorService executor, 
    EntityManagerFactory emf, 
    Class<T> entityClass, 
    Function<T, K> keyExtractor
)
```

### What it does:

-   **findAll**: returns cached values if present; otherwise loads from DB and populates cache
-   **findByCacheKey(queryAttribute, key)**: cache-first; on miss, queries by attribute and caches the result
-   **create**: persists entity and caches by keyExtractor(entity)
-   **update**: merges entity and refreshes cache entry
-   **delete**: removes entity by id and evicts from cache when key is inferable
-   **Async counterparts**: findAllAsync, findByCacheKeyAsync, createAsync, updateAsync, deleteAsync

### Choosing K and queryAttribute:

-   K should uniquely identify an entity (e.g., username, externalId, UUID)
-   queryAttribute is the attribute name used to load from DB on cache miss (e.g., "username")

### Cache policy:

-   Default expireAfterWrite(30 minutes); tune inside GenericCachedRepository builder if needed

### Example entity (PlayerProfile):

```java
import de.jexcellence.hibernate.entity.AbstractEntity;
import jakarta.persistence.Entity;

@Entity
public class PlayerProfile extends AbstractEntity {
  private String username;

  public String getUsername() {
    return username;
  }

  public void setUsername(String u) {
    this.username = u;
  }
}

```

### Key extractor:

```java
java.util.function.Function<
        PlayerProfile, 
        String
> byUsername = PlayerProfile::getUsername;
```

### Repository with cache by username:

```java
import de.jexcellence.hibernate.repository.GenericCachedRepository;

public class PlayerProfileRepository extends GenericCachedRepository<PlayerProfile, Long, String> {
  public PlayerProfileRepository(java.util.concurrent.ExecutorService exec,
                                 jakarta.persistence.EntityManagerFactory emf) {
    super(exec, emf, PlayerProfile.class, PlayerProfile::getUsername);
  }
}
```

### Usage:

```java
PlayerProfileRepository repo = new PlayerProfileRepository(exec, emf);
PlayerProfile p1 = repo.findByCacheKey("username", "Alice"); // cache-first
PlayerProfile created = repo.create(new PlayerProfile());     // put into cache
repo.findAllAsync(0, 100).thenAccept(list -> { /* cached after first load */ });
```

### Notes and pitfalls:

-   Ensure keyExtractor matches queryAttribute's semantics; otherwise cache misses/hits may be inconsistent
-   If the key attribute changes on update, the repository should update cache accordingly (evict old key if necessary)
-   EntityManager is not thread-safe; the repository creates per-operation managers internally

Examples by Environment
-----------------------

### Plain Java

```java
import de.jexcellence.hibernate.JEHibernate;
import de.jexcellence.hibernate.repository.GenericCachedRepository;
import jakarta.persistence.EntityManagerFactory;

import java.util.concurrent.*;

ExecutorService executor = Executors.newFixedThreadPool(8);
JEHibernate je = new JEHibernate("config/hibernate.properties");
EntityManagerFactory emf = je.getEntityManagerFactory();

GenericCachedRepository<PlayerProfile, Long, String> repo =
    new GenericCachedRepository<>(executor, emf, PlayerProfile.class, PlayerProfile::getUsername);

// Cache-first fetch by username:
PlayerProfile p = repo.findByCacheKey("username", "Alice");

// Async load all (cached on first call):
repo.findAllAsync(0, 100).thenAccept(list -> { /* ... */ });

// On create/update/delete, cache is synchronized accordingly:
PlayerProfile created = repo.create(new PlayerProfile());

je.close();
executor.shutdown();
```

### Spring (exact initialization)

```java
package com.raindropcentral.api.config;

import de.jexcellence.hibernate.JEHibernate;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class HibernateConfig {

  @Bean
  @Primary
  public EntityManagerFactory entityManagerFactory() {
    return new JEHibernate(
      new ClassPathResource("hibernate.properties").getPath()
    ).getEntityManagerFactory();
  }

  @Bean(destroyMethod = "shutdown")
  public java.util.concurrent.ExecutorService dbExecutor() {
    return java.util.concurrent.Executors.newFixedThreadPool(8);
  }
}
```

Define the cached repository as a Spring bean:

```java
import de.jexcellence.hibernate.repository.GenericCachedRepository;
import org.springframework.stereotype.Component;

@Component
public class PlayerProfileRepository extends GenericCachedRepository<PlayerProfile, Long, String> {
  public PlayerProfileRepository(java.util.concurrent.ExecutorService exec, EntityManagerFactory emf) {
    super(exec, emf, PlayerProfile.class, PlayerProfile::getUsername);
  }
}
```

Usage in a service:

```java
import org.springframework.stereotype.Service;

@Service
public class PlayerService {
  private final PlayerProfileRepository repo;

  public PlayerService(PlayerProfileRepository repo) {
    this.repo = repo;
  }

  public PlayerProfile getByUsername(String username) {
    return repo.findByCacheKey("username", username);
  }
}
```

**Notes:**

-   EntityManagerFactory is provided by JEHibernate; Spring Data is optional
-   Ensure ExecutorService is properly shutdown when the context closes

### Spigot/Paper (exact initialization)

Add a default hibernate.properties to your plugin resources at `database/hibernate.properties`.

In your plugin class:

```java
private jakarta.persistence.EntityManagerFactory entityManagerFactory;

private void initializeDatabaseResources() {
  java.io.File databaseFolder = new java.io.File(this.getDataFolder(), "database");
  if (
          databaseFolder.exists() || 
          databaseFolder.mkdirs()
  ) {
    this.saveResource("database/hibernate.properties", false);
    java.io.File hibernateFile = new java.io.File(databaseFolder + "/hibernate.properties");
    this.entityManagerFactory = new de.jexcellence.hibernate.JEHibernate(hibernateFile.getPath()).getEntityManagerFactory();
    com.raidcentral.logging.CentralLogger.getLogger(getClass().getName())
      .log(java.util.logging.Level.INFO, "Database resources initialized successfully.");
  }
}
```

Create an executor in onEnable and shut down in onDisable:

```java
private java.util.concurrent.ExecutorService dbExec;

@Override
public void onEnable() {
  dbExec = java.util.concurrent.Executors.newFixedThreadPool(8);
  initializeDatabaseResources();
}

@Override
public void onDisable() {
  if (dbExec != null) dbExec.shutdown();
  if (entityManagerFactory != null) entityManagerFactory.close();
}
```

Cached repository:

```java
public class ProfileRepository extends de.jexcellence.hibernate.repository.GenericCachedRepository<PlayerProfile, Long, String> {
  public ProfileRepository(java.util.concurrent.ExecutorService exec, jakarta.persistence.EntityManagerFactory emf) {
    super(exec, emf, PlayerProfile.class, PlayerProfile::getUsername);
  }
}
```

**Guidelines:**

-   Never block the main server thread with DB calls
-   Prefer async methods for heavy operations; switch results back to main thread for game state changes

Naming Strategy
---------------

Configure:

```properties
hibernate.physical_naming_strategy=de.jexcellence.hibernate.naming.JENamingStrategy
```

Typically maps ClassName -> class_name and fieldName -> field_name.

Optimistic Locking
------------------

Use OptimisticLockHandler with a versioned entity (AbstractEntity provides version). On OptimisticLockException:

-   Retry with backoff, or
-   Surface a domain error

Testing
-------

-   Use H2 in-memory DB with test properties under src/test/resources
-   Each test creates and closes its own EntityManagerFactory
-   Use a lightweight ExecutorService for async tests

Troubleshooting
---------------

-   **EntityManagerFactory fails to build**: verify JDBC URL, driver, dialect, and file paths
-   **Queries return empty**: verify entity annotations and naming strategy alignment
-   **Async issues**: never share EntityManager across threads; create per operation in repository
-   **Cache consistency**:
    -   keyExtractor must match the attribute used in queryAttribute
    -   After update/delete, cache entries must be updated/invalidated

Contributing
------------

PRs welcome. Keep README and AGENT.md updated with API changes or behavior adjustments.

License
-------

TBD. Add a LICENSE file (e.g., Apache-2.0 or MIT).