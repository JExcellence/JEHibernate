# JEHibernate 2.0

A modern, lightweight Hibernate/JPA utility library for Java 24+ providing:
- Fluent configuration with auto-discovery
- Generic base entities with flexible ID strategies
- Full-featured CRUD repositories with async support
- Caffeine-backed caching repositories
- Type-safe query builder with Specification pattern
- Zero-configuration entity and repository scanning
- Universal compatibility (Spigot/Bukkit, Spring, standalone)
- **Java 24 features**: Sealed interfaces, pattern matching, records, enhanced APIs

## Requirements

- Java 24+ (with preview features enabled)
- Hibernate 7.1+
- Jakarta Persistence 3.1+

## Installation

### Gradle (Kotlin DSL)
```kotlin
dependencies {
    implementation("de.jexcellence.hibernate:JEHibernate:2.0.0")
}

// Enable Java 24 preview features
tasks.withType<JavaCompile> {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test> {
    jvmArgs("--enable-preview")
}
```

### Maven
```xml
<dependency>
    <groupId>de.jexcellence.hibernate</groupId>
    <artifactId>JEHibernate</artifactId>
    <version>2.0.0</version>
</dependency>
```

## Quick Start

### 1. Create Entities

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
}

@Entity
public class Player extends UuidEntity {
    private String username;
    private String server;
    
    protected Player() {}
    
    public Player(String username) {
        this.username = username;
    }
}
```

### 2. Create Repositories

```java
public class UserRepository extends AbstractCrudRepository<User, Long> {
    public UserRepository(ExecutorService executor, EntityManagerFactory emf, Class<User> entityClass) {
        super(executor, emf, entityClass);
    }
}

public class PlayerRepository extends AbstractCachedRepository<Player, UUID, UUID> {
    public PlayerRepository(ExecutorService executor, EntityManagerFactory emf, Class<Player> entityClass) {
        super(executor, emf, entityClass, Player::getId);
    }
}
```

### 3. Bootstrap with Auto-Discovery

```java
var jeHibernate = JEHibernate.builder()
    .configuration(config -> config
        .database(DatabaseType.H2)
        .url("jdbc:h2:mem:testdb")
        .credentials("sa", "")
        .ddlAuto("update")
        .showSql(true))
    .scanPackages("com.example.entities", "com.example.repositories")
    .build();

var userRepo = jeHibernate.repositories().get(UserRepository.class);
var playerRepo = jeHibernate.repositories().get(PlayerRepository.class);
```

### 4. Use Repositories

```java
var user = userRepo.create(new User("alice", "alice@example.com"));
var found = userRepo.findById(user.getId());
var all = userRepo.findAll(0, 10);

userRepo.query()
    .and("active", true)
    .like("email", "%@gmail.com")
    .orderByDesc("createdAt")
    .listAsync()
    .thenAccept(users -> System.out.println("Found: " + users.size()));

jeHibernate.close();
```

## Configuration Options

### From Properties File

```java
var jeHibernate = JEHibernate.fromProperties("hibernate.properties");
```

**hibernate.properties:**
```properties
database.type=MYSQL

mysql.url=jdbc:mysql://localhost:3306/mydb
mysql.username=root
mysql.password=secret

hibernate.hbm2ddl.auto=update
hibernate.show_sql=false
hibernate.jdbc.batch_size=50
```

### Fluent Builder

```java
var jeHibernate = JEHibernate.builder()
    .configuration(config -> config
        .database(DatabaseType.POSTGRESQL)
        .url("jdbc:postgresql://localhost:5432/mydb")
        .credentials("user", "pass")
        .ddlAuto("validate")
        .showSql(false)
        .formatSql(true)
        .batchSize(50)
        .namingStrategy(new SnakeCaseStrategy()))
    .scanPackages("com.example")
    .build();
```

### Custom Executor

```java
var executor = Executors.newFixedThreadPool(10);
var jeHibernate = JEHibernate.builder()
    .executor(executor)
    .configuration(config -> config.fromProperties(props))
    .scanPackages("com.example")
    .build();
```

### Disable Auto-Scan

```java
var jeHibernate = JEHibernate.builder()
    .configuration(config -> config
        .database(DatabaseType.H2)
        .url("jdbc:h2:mem:testdb")
        .registerEntity(User.class)
        .registerEntity(Player.class))
    .disableAutoScan()
    .build();

jeHibernate.repositories().register(UserRepository.class, User.class);
```

## Entity Base Classes

### LongIdEntity (Auto-increment)

```java
@Entity
public class User extends LongIdEntity {
    private String name;
}
```

### UuidEntity (Auto-generated UUID)

```java
@Entity
public class Player extends UuidEntity {
    private String username;
}
```

### StringIdEntity (Custom String ID)

```java
@Entity
public class Session extends StringIdEntity {
    private String token;
    
    public Session(String token) {
        setId(token);
        this.token = token;
    }
}
```

## Repository Features

### Enhanced Optional Methods (Java 24)

```java
// Find or throw exception
User user = repo.findByIdOrThrow(1L);  // Throws EntityNotFoundException if not found

// Find or create new
User user = repo.findByIdOrCreate(999L, () -> new User("default", "default@example.com"));

// Smart create or update
Product product = new Product("Widget", 29.99);
product.setId(1L);
Product saved = repo.createOrUpdate(product, Product::getId);

// Functional filtering (in-memory)
List<User> premiumUsers = repo.findAllMatching(u -> u.isPremium() && u.isActive());
```

### Pagination with Records

```java
// Get paginated results with metadata
PageResult<User> page = repo.query()
    .and("active", true)
    .orderBy("username")
    .getPage(0, 20);

System.out.println("Page " + (page.pageNumber() + 1) + " of " + page.totalPages());
System.out.println("Total: " + page.totalElements());
System.out.println("Has next: " + page.hasNext());
System.out.println("Has previous: " + page.hasPrevious());

page.content().forEach(user -> System.out.println(user.getUsername()));

// Async pagination
repo.query()
    .like("email", "%@gmail.com")
    .getPageAsync(0, 50)
    .thenAccept(asyncPage -> {
        System.out.println("Loaded " + asyncPage.numberOfElements() + " items");
    });
```

### Basic CRUD

```java
var user = repo.create(new User("alice", "alice@example.com"));
var users = repo.createAll(List.of(user1, user2, user3));

Optional<User> found = repo.findById(1L);
List<User> all = repo.findAll();
List<User> page = repo.findAll(0, 20);
List<User> byIds = repo.findAllById(List.of(1L, 2L, 3L));

user.setEmail("newemail@example.com");
repo.update(user);
repo.updateAll(List.of(user1, user2));

repo.save(user);

repo.delete(1L);
repo.deleteAll(List.of(1L, 2L, 3L));

boolean exists = repo.exists(1L);
long count = repo.count();
```

### Async Operations

```java
repo.createAsync(user)
    .thenAccept(created -> System.out.println("Created: " + created.getId()));

repo.findByIdAsync(1L)
    .thenAccept(opt -> opt.ifPresent(System.out::println));

repo.findAllAsync(0, 10)
    .thenAccept(users -> System.out.println("Found: " + users.size()));

repo.updateAsync(user)
    .thenAccept(updated -> System.out.println("Updated"));

repo.deleteAsync(1L)
    .thenRun(() -> System.out.println("Deleted"));
```

### Query Builder

```java
var users = repo.query()
    .and("active", true)
    .like("email", "%@gmail.com")
    .greaterThan("age", 18)
    .isNotNull("lastLogin")
    .orderByDesc("createdAt")
    .page(0, 20)
    .list();

var count = repo.query()
    .and("role", "ADMIN")
    .count();

var first = repo.query()
    .and("username", "alice")
    .first();

var exists = repo.query()
    .and("email", "test@example.com")
    .exists();

repo.query()
    .and("active", true)
    .between("createdAt", startDate, endDate)
    .orderBy("username")
    .listAsync()
    .thenAccept(list -> System.out.println("Found: " + list.size()));
```

### Specification Pattern

```java
var spec = Specifications.equal("active", true)
    .and(Specifications.like("email", "%@gmail.com"))
    .and(Specifications.greaterThan("age", 18));

var users = repo.findAll(spec);
var user = repo.findOne(spec);
var count = repo.count(spec);

var customSpec = (Specification<User>) (root, query, cb) -> 
    cb.and(
        cb.equal(root.get("active"), true),
        cb.like(root.get("email"), "%@gmail.com")
    );

var users = repo.findAll(customSpec);
```

## Cached Repository

For high-read scenarios with caching:

```java
public class PlayerRepository extends AbstractCachedRepository<Player, UUID, UUID> {
    public PlayerRepository(ExecutorService executor, EntityManagerFactory emf, Class<Player> entityClass) {
        super(executor, emf, entityClass, Player::getId);
    }
}

var playerRepo = jeHibernate.repositories().get(PlayerRepository.class);

Optional<Player> player = playerRepo.findByKey("uuid", someUuid);

Player player = playerRepo.getOrCreate("uuid", uuid, key -> new Player(key));

playerRepo.preload();
playerRepo.evictById(playerId);
playerRepo.evictByKey(uuid);
playerRepo.evictAll();

var stats = playerRepo.getKeyCacheStats();
System.out.println("Hit rate: " + stats.hitRate());
```

## Dependency Injection

```java
public class UserService {
    @Inject
    private UserRepository userRepo;
    
    @Inject
    private PlayerRepository playerRepo;
    
    public void doSomething() {
        var users = userRepo.findAll();
    }
}

var service = new UserService();
jeHibernate.repositories().injectInto(service);

var service = jeHibernate.repositories().createWithInjection(UserService.class);
```

## Transaction Management

### Implicit (Repository Methods)

```java
var user = repo.create(new User("alice", "alice@example.com"));
```

### Explicit (TransactionTemplate)

```java
var template = new TransactionTemplate(jeHibernate.getEntityManagerFactory());

var result = template.execute(em -> {
    var user = em.find(User.class, 1L);
    user.setName("Updated");
    return em.merge(user);
});

template.executeVoid(em -> {
    var user = em.find(User.class, 1L);
    em.remove(user);
});

template.executeAsync(em -> {
    return em.find(User.class, 1L);
}, executor).thenAccept(user -> System.out.println(user));
```

## Optimistic Locking

```java
var result = OptimisticLockRetry.execute(
    () -> repo.update(entity),
    3,
    Duration.ofMillis(100)
);

var result = OptimisticLockRetry.execute(() -> repo.update(entity));
```

## Supported Databases

- H2
- MySQL
- MariaDB
- PostgreSQL
- Oracle
- SQL Server
- SQLite
- HSQLDB

## Java 24 Features

### Sealed Interfaces
The repository hierarchy uses sealed interfaces for type safety:

```java
public sealed interface Repository<T, ID> permits CrudRepository { }
public sealed interface CrudRepository<T, ID> extends Repository<T, ID> permits AsyncRepository { }
public sealed interface AsyncRepository<T, ID> extends CrudRepository<T, ID> permits QueryableRepository { }
public non-sealed interface QueryableRepository<T, ID> extends AsyncRepository<T, ID> { }
```

This prevents invalid implementations and provides compile-time guarantees.

### Pattern Matching Exception Handling
Automatic exception classification using switch pattern matching:

```java
try {
    repo.create(entity);
} catch (Exception e) {
    // Internally uses pattern matching:
    // PersistenceException -> TransactionException
    // IllegalArgumentException -> ValidationException
    // etc.
}
```

### Records for DTOs
Immutable data transfer objects with built-in validation:

```java
PageResult<User> page = repo.query().getPage(0, 20);
// PageResult is a record with:
// - content(), totalElements(), pageNumber(), pageSize()
// - hasNext(), hasPrevious(), totalPages(), isEmpty()
```

### Enhanced APIs
Modern Java features throughout:
- Virtual threads by default
- Sequenced collections (`getFirst()` instead of `get(0)`)
- Enhanced switch expressions
- Pattern matching for instanceof

See [examples/Java24FeaturesExample.java](examples/Java24FeaturesExample.java) for complete examples.

## Naming Strategy

Automatic snake_case conversion:

```java
@Entity
public class UserAccount extends LongIdEntity {
    private String firstName;
    private String lastName;
}
```

Creates table: `user_account` with columns: `first_name`, `last_name`

## Converters

Built-in converters:
- `UuidConverter` - UUID ↔ byte[16]
- `InstantConverter` - Instant ↔ Timestamp

## Bukkit/Spigot Example

```java
public class MyPlugin extends JavaPlugin {
    private JEHibernate jeHibernate;
    
    @Override
    public void onEnable() {
        jeHibernate = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.MYSQL)
                .url("jdbc:mysql://localhost:3306/minecraft")
                .credentials("user", "pass")
                .ddlAuto("update"))
            .scanPackages("com.example.plugin.entities", "com.example.plugin.repositories")
            .build();
        
        var playerRepo = jeHibernate.repositories().get(PlayerRepository.class);
        
        getServer().getPluginManager().registerEvents(new PlayerListener(playerRepo), this);
    }
    
    @Override
    public void onDisable() {
        if (jeHibernate != null) {
            jeHibernate.close();
        }
    }
}
```

## Spring Integration Example

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
            .scanPackages("com.example.entities", "com.example.repositories")
            .build();
    }
    
    @Bean
    public UserRepository userRepository(JEHibernate jeHibernate) {
        return jeHibernate.repositories().get(UserRepository.class);
    }
}
```

## Migration from 1.x

See [MIGRATION.md](MIGRATION.md) for complete migration guide.

## License

Apache License 2.0
