# JEHibernate 2.0

Modern Java 17+ Hibernate/JPA utility library that eliminates boilerplate while providing enterprise-grade database operations. Built for **Minecraft Bukkit/Spigot plugins** and works seamlessly in **Spring** or standalone applications. Automatically uses virtual threads on Java 21+.

## Features

- **Zero-configuration** entity and repository auto-scanning
- **Session-scoped operations** — lazy loading that actually works
- **Sealed interface hierarchy** — compile-time type safety for repositories
- **Fluent query builder** — type-safe filtering, multi-sort, OR conditions, fetch joins, streaming
- **Dual-layer caching** — Caffeine-backed with configurable strategies
- **Virtual threads** — automatic virtual thread support on Java 21+ (cached thread pool fallback on 17)
- **Optimistic lock retry** — exponential backoff with deadlock detection
- **Pattern-matching exceptions** — clean, classified error handling
- **Connection pooling** — Agroal support for production deployments
- **Slow query logging** — automatic detection and warnings

## Quick Start

### Gradle

```kotlin
dependencies {
    implementation("de.jexcellence.hibernate:JEHibernate:2.0.0")
    
    // Pick your database driver
    runtimeOnly("com.h2database:h2:2.4.240")         // H2 (dev/testing)
    runtimeOnly("com.mysql:mysql-connector-j:9.3.0")  // MySQL
    runtimeOnly("org.postgresql:postgresql:42.7.7")    // PostgreSQL
}
```

### Define an Entity

```java
@Entity
public class User extends LongIdEntity {
    private String username;
    private String email;
    private boolean active;

    protected User() {}

    public User(String username, String email) {
        this.username = username;
        this.email = email;
        this.active = true;
    }

    // Getters and setters...
}
```

Entity base classes: `LongIdEntity` (auto-increment), `UuidEntity` (UUID v4), `StringIdEntity` (natural keys). All provide automatic timestamps (`createdAt`, `updatedAt`), optimistic locking (`version`), and correct `equals`/`hashCode`.

### Define a Repository

```java
public class UserRepository extends AbstractCrudRepository<User, Long> {
    public UserRepository(ExecutorService executor, EntityManagerFactory emf, Class<User> entityClass) {
        super(executor, emf, entityClass);
    }
}
```

### Initialize and Use

```java
var jeHibernate = JEHibernate.builder()
    .configuration(config -> config
        .database(DatabaseType.H2)
        .url("jdbc:h2:mem:mydb")
        .credentials("sa", "")
        .ddlAuto("update"))
    .scanPackages("com.example")
    .build();

var userRepo = jeHibernate.repositories().get(UserRepository.class);

// CRUD
var user = userRepo.create(new User("alice", "alice@example.com"));
var found = userRepo.findById(user.getId());
user.setEmail("new@example.com");
userRepo.save(user);  // works for both new and existing entities
userRepo.delete(user.getId());

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
        .connectionPool(5, 20))          // Agroal connection pool
    .scanPackages("com.example")
    .build();
```

### Properties File

```java
// From filesystem or classpath
var jeHibernate = JEHibernate.fromProperties("hibernate.properties");

// From Bukkit plugin data folder
var jeHibernate = JEHibernate.fromProperties(getDataFolder(), "database", "hibernate.properties");
// Loads: plugins/MyPlugin/database/hibernate.properties
```

Place a `hibernate.properties` file in your plugin's `resources/database/` folder:

```properties
# ============================================
# Database Type Selection
# ============================================
# Supported: H2, MYSQL, MARIADB, POSTGRESQL, ORACLE, MSSQL_SERVER, SQLITE, HSQLDB
database.type=H2

# ============================================
# H2 (default — embedded, no external database required)
# ============================================
h2.url=jdbc:h2:file:./plugins/MyPlugin/database/mydb;MODE=MySQL;AUTO_SERVER=TRUE
h2.username=sa
h2.password=
# h2.driver=org.h2.Driver            # optional — auto-detected from type
# h2.dialect=org.hibernate.dialect.H2Dialect  # optional — auto-detected

# ============================================
# MySQL
# ============================================
# mysql.url=jdbc:mysql://localhost:3306/mydb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
# mysql.username=root
# mysql.password=change_me

# ============================================
# PostgreSQL
# ============================================
# postgresql.url=jdbc:postgresql://localhost:5432/mydb
# postgresql.username=postgres
# postgresql.password=change_me

# ============================================
# Microsoft SQL Server (prefix: mssql)
# ============================================
# mssql.url=jdbc:sqlserver://localhost:1433;databaseName=mydb;encrypt=false
# mssql.username=sa
# mssql.password=change_me

# ============================================
# Hibernate Settings
# ============================================
hibernate.hbm2ddl.auto=update
hibernate.show_sql=false
hibernate.format_sql=false
# hibernate.jdbc.batch_size=25       # default: 25
# hibernate.order_inserts=true       # default: true
# hibernate.order_updates=true       # default: true
```

**Property format:** `{prefix}.url`, `{prefix}.username`, `{prefix}.password`, and optionally `{prefix}.driver` / `{prefix}.dialect` to override auto-detected values. All `hibernate.*` properties are passed through directly to Hibernate.

### Supported Databases

| Type | Prefix | Default Driver |
|------|--------|---------------|
| `H2` | `h2` | `org.h2.Driver` |
| `MYSQL` | `mysql` | `com.mysql.cj.jdbc.Driver` |
| `MARIADB` | `mariadb` | `org.mariadb.jdbc.Driver` |
| `POSTGRESQL` | `postgresql` | `org.postgresql.Driver` |
| `ORACLE` | `oracle` | `oracle.jdbc.OracleDriver` |
| `MSSQL_SERVER` | `mssql` | `com.microsoft.sqlserver.jdbc.SQLServerDriver` |
| `SQLITE` | `sqlite` | `org.sqlite.JDBC` |
| `HSQLDB` | `hsqldb` | `org.hsqldb.jdbc.JDBCDriver` |

### Connection Pooling

Requires `hibernate-agroal` and `agroal-pool` on the classpath:

```kotlin
implementation("org.hibernate.orm:hibernate-agroal")
implementation("io.agroal:agroal-pool:2.5")
```

```java
.connectionPool(5, 20)  // min 5, max 20 connections
```

### Second-Level Cache

Requires `hibernate-jcache` on the classpath:

```java
.enableSecondLevelCache()
```

---

## Repository Operations

### Basic CRUD

```java
// Create
var user = userRepo.create(new User("alice", "alice@example.com"));

// Read
Optional<User> found = userRepo.findById(1L);
User user = userRepo.findByIdOrThrow(1L);  // throws EntityNotFoundException
User user = userRepo.findByIdOrCreate(999L, () -> new User("default", "d@example.com"));

// Save (auto-detects new vs existing)
userRepo.save(user);

// Update
user.setEmail("new@example.com");
userRepo.update(user);

// Delete
userRepo.delete(1L);
userRepo.deleteEntity(user);

// Refresh from database
userRepo.refresh(user);
```

### Batch Operations

```java
var users = List.of(new User("alice", "a@ex.com"), new User("bob", "b@ex.com"));

userRepo.createAll(users);
userRepo.saveAll(users);     // batch save (new or existing)
userRepo.updateAll(users);
userRepo.deleteAll(List.of(1L, 2L, 3L));
userRepo.findAllById(List.of(1L, 2L));
```

### Async Operations

All CRUD methods have async variants returning `CompletableFuture`, executed on virtual threads:

```java
userRepo.createAsync(user).thenAccept(u -> System.out.println("Created: " + u.getId()));
userRepo.findByIdAsync(1L).thenAccept(opt -> opt.ifPresent(this::process));
userRepo.saveAllAsync(users).join();
```

---

## Session-Scoped Operations (Lazy Loading)

The biggest improvement in 2.0. Without session scoping, each repository method creates/closes its own `EntityManager`, causing `LazyInitializationException` when accessing lazy collections.

### withSession -- Transactional

```java
userRepo.withSession(session -> {
    User user = session.find(User.class, 1L).orElseThrow();
    user.getOrders().size();  // lazy loading works!
    
    user.setEmail("new@example.com");
    session.merge(user);
    session.flush();
    return user;
});
```

### withReadOnly -- No Transaction Overhead

```java
userRepo.withReadOnly(session -> {
    User user = session.find(User.class, 1L).orElseThrow();
    return new ArrayList<>(user.getOrders());  // copy before session closes
});
```

### Global Session (via JEHibernate)

```java
jeHibernate.withSession(session -> {
    var user = session.find(User.class, 1L).orElseThrow();
    var orders = session.query(Order.class)
        .and("userId", user.getId())
        .list();
    return orders;
});
```

---

## Query Builder

Type-safe, fluent query construction without JPQL or SQL.

### Filtering

```java
var users = userRepo.query()
    .and("active", true)                           // equality
    .like("email", "%@gmail.com")                  // LIKE
    .greaterThan("age", 18)                        // comparisons
    .lessThanOrEqual("loginCount", 100)
    .between("createdAt", startDate, endDate)      // range
    .in("role", List.of("ADMIN", "MOD"))           // IN clause
    .isNotNull("lastLogin")                        // null checks
    .notEqual("status", "BANNED")
    .list();
```

### OR Conditions

```java
var users = userRepo.query()
    .and("active", true)
    .or("role", "ADMIN")
    .or("role", "MODERATOR")
    .list();
// WHERE active = true AND (role = 'ADMIN' OR role = 'MODERATOR')
```

### Multi-Field Sorting

```java
var users = userRepo.query()
    .orderBy("lastName")
    .orderByDesc("createdAt")
    .orderBy("firstName")
    .list();
```

### Pagination

```java
// Simple pagination
var page1 = userRepo.findAll(0, 20);

// Rich pagination with metadata
PageResult<User> page = userRepo.query()
    .and("active", true)
    .orderByDesc("createdAt")
    .getPage(0, 20);

page.content();        // List<User>
page.totalElements();  // total matching entities
page.totalPages();     // total page count
page.hasNext();        // has next page?
page.hasPrevious();    // has previous page?
page.isFirst();        // is first page?
page.isLast();         // is last page?
```

### Fetch Joins (N+1 Prevention)

```java
var users = userRepo.query()
    .fetch("orders")          // INNER JOIN FETCH
    .fetchLeft("profile")     // LEFT JOIN FETCH
    .and("active", true)
    .list();
```

### Streaming (Large Datasets)

```java
// Must use try-with-resources!
try (var stream = userRepo.query().and("active", true).stream()) {
    stream.filter(u -> u.getAge() > 18)
          .forEach(this::process);
}
```

### Async Queries

```java
userRepo.query().and("active", true).listAsync()
    .thenAccept(users -> users.forEach(this::process));

userRepo.query().and("active", true).getPageAsync(0, 20)
    .thenAccept(page -> System.out.println("Total: " + page.totalElements()));
```

---

## Specifications (Composable Query Predicates)

```java
Specification<User> activeGmail = Specifications.<User>equal("active", true)
    .and(Specifications.like("email", "%@gmail.com"))
    .and(Specifications.greaterThan("age", 18));

List<User> users = userRepo.findAll(activeGmail);
long count = userRepo.count(activeGmail);
boolean exists = userRepo.existsBy(activeGmail);
Optional<User> one = userRepo.findOne(activeGmail);
```

---

## Caching

### Basic Cached Repository

```java
public class PlayerRepository extends AbstractCachedRepository<Player, UUID, String> {
    public PlayerRepository(ExecutorService executor, EntityManagerFactory emf, Class<Player> entityClass) {
        super(executor, emf, entityClass, Player::getUsername);
    }
}
```

### With Custom Configuration

```java
public class PlayerRepository extends AbstractCachedRepository<Player, UUID, String> {
    public PlayerRepository(ExecutorService executor, EntityManagerFactory emf, Class<Player> entityClass) {
        super(executor, emf, entityClass, Player::getUsername,
            CacheConfig.builder()
                .expiration(Duration.ofMinutes(15))
                .maxSize(5000)
                .expireAfterAccess(true)  // reset expiry on each access
                .build());
    }
}
```

### Cache Operations

```java
// Lookup by cache key
Optional<Player> player = playerRepo.findByKey("alice");
Optional<Player> player = playerRepo.findByKey("username", "alice"); // with DB fallback

// Get or create
Player p = playerRepo.getOrCreate("username", "alice", key -> new Player(key));

// Eviction
playerRepo.evict(player);
playerRepo.evictById(playerId);
playerRepo.evictByKey("alice");
playerRepo.evictAll();

// Preloading
playerRepo.preload();       // sync
playerRepo.preloadAsync();  // async

// Stats
CacheStats stats = playerRepo.getKeyCacheStats();
long size = playerRepo.getCacheSize();
```

---

## Transaction Management

### TransactionTemplate

```java
TransactionTemplate tx = jeHibernate.transactionTemplate();

// Transactional operation
User user = tx.execute(em -> {
    var u = new User("alice", "alice@example.com");
    em.persist(u);
    return u;
});

// Read-only (no transaction overhead)
List<User> users = tx.executeReadOnly(em ->
    em.createQuery("SELECT u FROM User u WHERE u.active = true", User.class).getResultList()
);
```

### Optimistic Lock Retry

```java
// Default: 3 retries, 100ms exponential backoff
User updated = OptimisticLockRetry.execute(() -> {
    User user = userRepo.findByIdOrThrow(userId);
    user.incrementLoginCount();
    return userRepo.save(user);
});

// Custom settings with deadlock retry
Order order = OptimisticLockRetry.execute(
    () -> updateOrderStatus(orderId, newStatus),
    5,                        // max retries
    Duration.ofMillis(200),   // initial backoff
    true                      // also retry deadlocks
);

// Void operation
OptimisticLockRetry.executeVoid(() -> {
    var user = userRepo.findByIdOrThrow(userId);
    user.setEmail("new@example.com");
    userRepo.save(user);
});
```

---

## Dependency Injection

```java
public class UserService {
    @Inject
    private UserRepository userRepo;

    @Inject
    private OrderRepository orderRepo;
}

// Inject into existing instance
var service = new UserService();
jeHibernate.repositories().injectInto(service);

// Create with injection
var service = jeHibernate.repositories().createWithInjection(UserService.class);
```

---

## Bukkit/Spigot Plugin Integration

### Using Properties File (recommended)

Place `hibernate.properties` in `src/main/resources/database/`:

```java
public class MyPlugin extends JavaPlugin {
    private JEHibernate jeHibernate;

    @Override
    public void onEnable() {
        // Save default config from resources/database/ to plugins/MyPlugin/database/
        saveResource("database/hibernate.properties", false);

        // Load from plugin data folder
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

### Using Builder API

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

### Async Operations for Non-Blocking Main Thread

```java
// Don't block the main thread - use async
playerRepo.findByIdAsync(playerId)
    .thenAccept(opt -> opt.ifPresent(player -> {
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Back on main thread with the result
            player.sendMessage("Welcome back!");
        });
    }));
```

---

## Spring Integration

JEHibernate works alongside Spring's own EntityManagerFactory:

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
                .ddlAuto("validate"))
            .scanPackages("com.example")
            .build();
    }

    @PreDestroy
    public void shutdown() {
        jeHibernate().close();
    }
}
```

---

## Logging

JEHibernate uses **SLF4J** for logging. You need an SLF4J implementation on your classpath.

### For Bukkit/Spigot Plugins (slf4j-simple)

Add to your dependencies:

```kotlin
implementation("org.slf4j:slf4j-simple:2.0.16")
```

Create `simplelogger.properties` in `src/main/resources/`:

```properties
# Default logging level
org.slf4j.simpleLogger.defaultLogLevel=info

# JEHibernate logging
org.slf4j.simpleLogger.log.de.jexcellence.jehibernate=info

# Hibernate SQL logging (set to debug to see SQL)
org.slf4j.simpleLogger.log.org.hibernate.SQL=warn
org.slf4j.simpleLogger.log.org.hibernate.orm.jdbc.bind=warn

# Show timestamps and thread names
org.slf4j.simpleLogger.showDateTime=true
org.slf4j.simpleLogger.dateTimeFormat=yyyy-MM-dd HH:mm:ss
org.slf4j.simpleLogger.showThreadName=true
```

### For Spring (Logback — already included)

Spring Boot includes Logback by default. Add to `logback-spring.xml`:

```xml
<logger name="de.jexcellence.jehibernate" level="INFO"/>
<logger name="org.hibernate.SQL" level="WARN"/>
```

### Slow Query Detection

JEHibernate automatically warns about queries exceeding 500ms:

```java
// Customize threshold
QueryLogger.setSlowQueryThreshold(1000); // 1 second
```

---

## Troubleshooting

### LazyInitializationException

Use session-scoped operations:

```java
// WRONG - EM closes immediately, lazy loading fails
var user = userRepo.findByIdOrThrow(1L);
user.getOrders().size();  // LazyInitializationException!

// RIGHT - EM stays open for the callback
userRepo.withSession(session -> {
    var user = session.find(User.class, 1L).orElseThrow();
    return user.getOrders().size();  // works!
});

// RIGHT - fetch join loads eagerly
var users = userRepo.query().fetch("orders").list();
```

### OptimisticLockException / StaleObjectStateException

Wrap concurrent updates in `OptimisticLockRetry`:

```java
OptimisticLockRetry.execute(() -> {
    var user = userRepo.findByIdOrThrow(userId);
    user.setBalance(user.getBalance() + amount);
    return userRepo.save(user);
});
```

### Slow Queries

JEHibernate automatically logs queries exceeding 500ms at WARN level. Configure the threshold:

```java
QueryLogger.setSlowQueryThreshold(1000); // 1 second
```

---

## License

Apache License 2.0
