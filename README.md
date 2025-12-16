# JEHibernate

A lightweight Hibernate/JPA utility library providing:
- Easy EntityManagerFactory bootstrapping
- Full-featured CRUD repositories with async support
- Caffeine-backed caching repository
- Fluent query builder
- Consistent naming strategy and converters

## Installation

### Gradle (Kotlin DSL)
```kotlin
dependencies {
    implementation("de.jexcellence.hibernate:JEHibernate:1.1.0")
}
```

### Maven
```xml
<dependency>
    <groupId>de.jexcellence.hibernate</groupId>
    <artifactId>JEHibernate</artifactId>
    <version>1.1.0</version>
</dependency>
```

## Quick Start

### 1. Create hibernate.properties
```properties
# ============================================
# Database Type Selection
# ============================================
# Supported types: H2, MYSQL, MARIADB, POSTGRESQL, ORACLE, MSSQL_SERVER, SQLITE, HSQLDB
# Default: H2 (embedded, no external database required)
database.type=H2

# ============================================
# H2 Database Configuration (Default - Embedded)
# ============================================
# H2 is used by default for easy setup without external database
# Mode options: mem (in-memory), file (persistent)
h2.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
h2.driver=org.h2.Driver
h2.dialect=org.hibernate.dialect.H2Dialect
h2.username=sa
h2.password=

# ============================================
# MySQL Database Configuration
# ============================================
mysql.url=jdbc:mysql://localhost:3306/mydb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
mysql.driver=com.mysql.cj.jdbc.Driver
mysql.dialect=org.hibernate.dialect.MySQLDialect
mysql.username=root
mysql.password=change_me

# ============================================
# MariaDB Database Configuration
# ============================================
mariadb.url=jdbc:mariadb://localhost:3306/mydb?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8
mariadb.driver=org.mariadb.jdbc.Driver
mariadb.dialect=org.hibernate.dialect.MariaDBDialect
mariadb.username=root
mariadb.password=change_me

# ============================================
# PostgreSQL Database Configuration
# ============================================
postgresql.url=jdbc:postgresql://localhost:5432/mydb
postgresql.driver=org.postgresql.Driver
postgresql.dialect=org.hibernate.dialect.PostgreSQLDialect
postgresql.username=postgres
postgresql.password=change_me

# ============================================
# Oracle Database Configuration
# ============================================
oracle.url=jdbc:oracle:thin:@localhost:1521:orcl
oracle.driver=oracle.jdbc.OracleDriver
oracle.dialect=org.hibernate.dialect.OracleDialect
oracle.username=system
oracle.password=change_me

# ============================================
# Microsoft SQL Server Configuration
# ============================================
mssql.url=jdbc:sqlserver://localhost:1433;databaseName=mydb;encrypt=false
mssql.driver=com.microsoft.sqlserver.jdbc.SQLServerDriver
mssql.dialect=org.hibernate.dialect.SQLServerDialect
mssql.username=sa
mssql.password=change_me

# ============================================
# Hibernate Configuration
# ============================================
# Schema management: validate, update, create, create-drop, none
# Recommended: validate for production, update for development
hibernate.hbm2ddl.auto=update

# Show SQL statements in console (disable in production)
hibernate.show_sql=false

# Format SQL statements for readability
hibernate.format_sql=false

# Highlight SQL syntax in console output
hibernate.highlight_sql=false

# Use SQL comments for debugging
hibernate.use_sql_comments=false

# Batch size for bulk operations
hibernate.jdbc.batch_size=25

# Order inserts for better batching
hibernate.order_inserts=true

# Order updates for better batching
hibernate.order_updates=true

# Generate statistics (disable in production)
hibernate.generate_statistics=false
```

### 2. Bootstrap
```java
var executor = Executors.newFixedThreadPool(4);
var jeHibernate = new JEHibernate("hibernate.properties");

RepositoryManager.initialize(executor, jeHibernate.getEntityManagerFactory());
```

### 3. Create and Register Repository
```java
public class UserRepository extends BaseRepository<User, Long> {
    public UserRepository(ExecutorService exec, EntityManagerFactory emf, Class<User> entityClass) {
        super(exec, emf, entityClass);
    }
}

// Register
RepositoryManager.getInstance().register(UserRepository.class, User.class);

// Get anywhere
var userRepo = RepositoryManager.get(UserRepository.class);
```

## Repository Features

### Basic CRUD
```java
// Create
var user = repo.create(new User("Alice"));
var users = repo.createAll(List.of(user1, user2, user3));

// Read
Optional<User> user = repo.findById(1L);
List<User> all = repo.findAll(0, 10);
boolean exists = repo.existsById(1L);
long count = repo.count();

// Update
repo.update(user);
repo.save(user); // Smart persist/merge

// Delete
repo.delete(1L);
repo.deleteAll(List.of(1L, 2L, 3L));
repo.deleteByAttribute("status", "INACTIVE");
```

### Fluent Query Builder
```java
var activeUsers = repo.query()
    .where("active", true)
    .whereLike("email", "%@gmail.com")
    .whereGreaterThan("age", 18)
    .whereNotNull("lastLogin")
    .orderByDesc("createdAt")
    .page(0, 20)
    .list();

var count = repo.query()
    .where("role", "ADMIN")
    .count();

var first = repo.query()
    .where("username", "alice")
    .first();
```

### Async Operations
```java
repo.createAsync(user).thenAccept(created -> ...);
repo.findByIdAsync(1L).thenAccept(opt -> ...);
repo.query().where("active", true).listAsync().thenAccept(list -> ...);
```

## Cached Repository

For high-read scenarios, use `CachedRepository`:

```java
public class PlayerRepository extends CachedRepository<Player, Long, UUID> {
    public PlayerRepository(ExecutorService exec, EntityManagerFactory emf) {
        super(exec, emf, Player.class, Player::getUuid);
    }
}

// Register with key extractor
RepositoryManager.getInstance().register(PlayerRepository.class, Player.class, Player::getUuid);

// Usage
var playerRepo = RepositoryManager.get(PlayerRepository.class);

// Cache-first lookup
Optional<Player> player = playerRepo.findByKey("uuid", someUuid);

// Get or create pattern
Player player = playerRepo.getOrCreate("uuid", uuid, key -> new Player(key));

// Cache management
playerRepo.preload();           // Warm up cache
playerRepo.evictById(1L);       // Invalidate specific entry
playerRepo.evictAll();          // Clear cache
var stats = playerRepo.getKeyCacheStats(); // Monitor performance
```

## Configuration

### Database Types
Supported: `H2`, `MYSQL`, `MARIADB`, `POSTGRESQL`, `ORACLE`, `MSSQL_SERVER`, `SQLITE`, `HSQLDB`

```properties
database.type=MYSQL

mysql.url=jdbc:mysql://localhost:3306/mydb
mysql.username=root
mysql.password=secret

hibernate.hbm2ddl.auto=validate
hibernate.show_sql=false
hibernate.jdbc.batch_size=50
```

### Naming Strategy
```properties
hibernate.physical_naming_strategy=de.jexcellence.hibernate.naming.SnakeCaseNamingStrategy
```
Converts `UserAccount` → `user_account`, `firstName` → `first_name`

## Dependency Injection

### Manual Injection
```java
public class UserService {
    @InjectRepository
    private UserRepository userRepo;
}

var service = new UserService();
RepositoryManager.getInstance().injectInto(service);
```

### Auto-Creation with Injection
```java
var service = RepositoryManager.getInstance().createWithInjection(UserService.class);
```

## Entity Base Class

```java
@Entity
public class User extends BaseEntity {
    private String name;
    // Inherits: id, version, createdAt, updatedAt, deleted
}
```

## Optimistic Locking

```java
var result = OptimisticLockHandler.executeWithRetry(
    () -> repo.update(entity),
    "User"
);
```

## License

Apache License 2.0
