# Repository Dependency Injection - Usage Examples

This document provides comprehensive examples for using the repository dependency injection system in JEHibernate.

## Table of Contents

1. [Basic Setup](#basic-setup)
2. [Repository Examples](#repository-examples)
3. [Registration Patterns](#registration-patterns)
4. [Service Class Examples](#service-class-examples)
5. [Advanced Patterns](#advanced-patterns)
6. [Error Handling](#error-handling)
7. [Best Practices](#best-practices)

---

## Basic Setup

### Step 1: Define Your Entity

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(nullable = false, unique = true)
    private String username;
    
    @Column(nullable = false)
    private String email;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    // Constructors, getters, setters...
    
    public UUID getId() {
        return id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getEmail() {
        return email;
    }
}
```

### Step 2: Create Your Repository

```java
package com.example.repository;

import de.jexcellence.hibernate.repository.GenericCachedRepository;
import com.example.entity.User;

import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

public class UserRepository extends GenericCachedRepository<User, UUID, UUID> {
    
    public UserRepository(
        ExecutorService executor,
        EntityManagerFactory emf,
        Class<User> entityClass,
        Function<User, UUID> keyExtractor
    ) {
        super(executor, emf, entityClass, keyExtractor);
    }
    
    // Add custom query methods
    public CompletableFuture<User> findByUsername(String username) {
        return this.findByField("username", username);
    }
    
    public CompletableFuture<List<User>> findByEmailDomain(String domain) {
        return CompletableFuture.supplyAsync(() -> {
            EntityManager em = entityManagerFactory.createEntityManager();
            try {
                return em.createQuery(
                    "SELECT u FROM User u WHERE u.email LIKE :pattern", 
                    User.class
                )
                .setParameter("pattern", "%@" + domain)
                .getResultList();
            } finally {
                em.close();
            }
        }, executor);
    }
}
```

### Step 3: Initialize in Plugin Main Class

```java
package com.example;

import de.jexcellence.hibernate.repository.RepositoryManager;
import com.example.repository.UserRepository;
import com.example.entity.User;
import com.example.service.UserService;

import org.bukkit.plugin.java.JavaPlugin;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyPlugin extends JavaPlugin {
    
    private ExecutorService executor;
    private EntityManagerFactory entityManagerFactory;
    private UserService userService;
    
    @Override
    public void onEnable() {
        // Step 1: Create shared dependencies
        this.executor = Executors.newFixedThreadPool(4);
        this.entityManagerFactory = createEntityManagerFactory();
        
        // Step 2: Initialize RepositoryManager
        RepositoryManager.initialize(executor, entityManagerFactory);
        
        // Step 3: Register repositories
        registerRepositories();
        
        // Step 4: Create and inject services
        this.userService = new UserService();
        RepositoryManager.getInstance().injectInto(userService);
        
        getLogger().info("Repository injection system initialized!");
    }
    
    private void registerRepositories() {
        RepositoryManager manager = RepositoryManager.getInstance();
        
        // Register UserRepository with ID as cache key
        manager.register(
            UserRepository.class,
            User.class,
            User::getId
        );
        
        // Register other repositories...
    }
    
    private EntityManagerFactory createEntityManagerFactory() {
        return Persistence.createEntityManagerFactory("myPersistenceUnit");
    }
    
    @Override
    public void onDisable() {
        if (executor != null) {
            executor.shutdown();
        }
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
    }
}
```

### Step 4: Use in Service Classes

```java
package com.example.service;

import de.jexcellence.hibernate.repository.InjectRepository;
import com.example.repository.UserRepository;
import com.example.entity.User;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class UserService {
    
    @InjectRepository
    private UserRepository userRepository;
    
    public CompletableFuture<User> getUser(UUID id) {
        return userRepository.findById(id);
    }
    
    public CompletableFuture<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    public CompletableFuture<Void> createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setCreatedAt(LocalDateTime.now());
        
        return userRepository.save(user).thenApply(savedUser -> null);
    }
    
    public CompletableFuture<List<User>> getAllUsers() {
        return userRepository.findAll();
    }
    
    public CompletableFuture<Void> deleteUser(UUID id) {
        return userRepository.deleteById(id);
    }
}
```

---

## Repository Examples

### Example 1: Simple Non-Cached Repository

For entities that don't need caching:

```java
public class AuditLogRepository extends AbstractCRUDRepository<AuditLog, Long> {
    
    public AuditLogRepository(
        ExecutorService executor,
        EntityManagerFactory emf,
        Class<AuditLog> entityClass
    ) {
        super(executor, emf, entityClass);
    }
    
    public CompletableFuture<List<AuditLog>> findByUserId(UUID userId) {
        return this.findByField("userId", userId);
    }
    
    public CompletableFuture<List<AuditLog>> findRecent(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            EntityManager em = entityManagerFactory.createEntityManager();
            try {
                return em.createQuery(
                    "SELECT a FROM AuditLog a ORDER BY a.timestamp DESC", 
                    AuditLog.class
                )
                .setMaxResults(limit)
                .getResultList();
            } finally {
                em.close();
            }
        }, executor);
    }
}
```

**Registration:**

```java
RepositoryManager.getInstance().register(
    AuditLogRepository.class,
    AuditLog.class
);
```

### Example 2: Cached Repository with ID Key

Most common pattern - cache by entity ID:

```java
public class PlayerRepository extends GenericCachedRepository<Player, UUID, UUID> {
    
    public PlayerRepository(
        ExecutorService executor,
        EntityManagerFactory emf,
        Class<Player> entityClass,
        Function<Player, UUID> keyExtractor
    ) {
        super(executor, emf, entityClass, keyExtractor);
    }
    
    public CompletableFuture<Player> findByMinecraftUUID(UUID minecraftUuid) {
        return this.findByField("minecraftUuid", minecraftUuid);
    }
}
```

**Registration:**

```java
RepositoryManager.getInstance().register(
    PlayerRepository.class,
    Player.class,
    Player::getId  // Cache by primary key
);
```

### Example 3: Cached Repository with Custom Key

Cache by a different field (e.g., username):

```java
public class PlayerRepository extends GenericCachedRepository<Player, UUID, String> {
    
    public PlayerRepository(
        ExecutorService executor,
        EntityManagerFactory emf,
        Class<Player> entityClass,
        Function<Player, String> keyExtractor
    ) {
        super(executor, emf, entityClass, keyExtractor);
    }
    
    public CompletableFuture<Player> findByUsername(String username) {
        // This will use the cache with username as key
        return this.findByField("username", username);
    }
}
```

**Registration:**

```java
RepositoryManager.getInstance().register(
    PlayerRepository.class,
    Player.class,
    Player::getUsername  // Cache by username instead of ID
);
```

---

## Registration Patterns

### Pattern 1: Register All Repositories at Startup

```java
private void registerRepositories() {
    RepositoryManager manager = RepositoryManager.getInstance();
    
    // Non-cached repositories
    manager.register(AuditLogRepository.class, AuditLog.class);
    manager.register(ConfigRepository.class, Config.class);
    
    // Cached repositories with ID keys
    manager.register(UserRepository.class, User.class, User::getId);
    manager.register(PlayerRepository.class, Player.class, Player::getId);
    
    // Cached repositories with custom keys
    manager.register(
        SessionRepository.class, 
        Session.class, 
        Session::getSessionToken
    );
}
```

### Pattern 2: Conditional Registration

```java
private void registerRepositories() {
    RepositoryManager manager = RepositoryManager.getInstance();
    
    // Always register core repositories
    manager.register(UserRepository.class, User.class, User::getId);
    
    // Conditionally register based on config
    if (getConfig().getBoolean("features.audit-logging")) {
        manager.register(AuditLogRepository.class, AuditLog.class);
    }
    
    if (getConfig().getBoolean("features.player-stats")) {
        manager.register(PlayerStatsRepository.class, PlayerStats.class, PlayerStats::getPlayerId);
    }
}
```

### Pattern 3: Replace Registration

```java
// Initial registration
manager.register(UserRepository.class, User.class, User::getId);

// Later, replace with different key extractor
manager.register(
    UserRepository.class, 
    User.class, 
    User::getUsername  // Now caches by username
);
// Note: Existing instances are not affected, only new injections
```

---

## Service Class Examples

### Example 1: Single Repository Service

```java
public class UserService {
    
    @InjectRepository
    private UserRepository userRepository;
    
    public User getUserSync(UUID id) {
        return userRepository.findById(id).join();
    }
    
    public void createUserAsync(String username, String email, Consumer<User> callback) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        
        userRepository.save(user).thenAccept(callback);
    }
}
```

### Example 2: Multiple Repository Service

```java
public class GameService {
    
    @InjectRepository
    private PlayerRepository playerRepository;
    
    @InjectRepository
    private GameSessionRepository sessionRepository;
    
    @InjectRepository
    private AuditLogRepository auditRepository;
    
    public CompletableFuture<Void> startGame(UUID playerId) {
        return playerRepository.findById(playerId)
            .thenCompose(player -> {
                GameSession session = new GameSession();
                session.setPlayer(player);
                session.setStartTime(LocalDateTime.now());
                return sessionRepository.save(session);
            })
            .thenCompose(session -> {
                AuditLog log = new AuditLog();
                log.setAction("GAME_START");
                log.setUserId(playerId);
                return auditRepository.save(log);
            })
            .thenApply(log -> null);
    }
}
```

### Example 3: Service with Private Helper Methods

```java
public class UserManagementService {
    
    @InjectRepository
    private UserRepository userRepository;
    
    @InjectRepository
    private AuditLogRepository auditRepository;
    
    public CompletableFuture<User> registerUser(String username, String email) {
        return validateUsername(username)
            .thenCompose(valid -> {
                if (!valid) {
                    throw new IllegalArgumentException("Username already exists");
                }
                return createUser(username, email);
            })
            .thenCompose(user -> logAction("USER_REGISTERED", user.getId())
                .thenApply(v -> user)
            );
    }
    
    private CompletableFuture<Boolean> validateUsername(String username) {
        return userRepository.findByUsername(username)
            .thenApply(user -> user == null);
    }
    
    private CompletableFuture<User> createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setCreatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }
    
    private CompletableFuture<Void> logAction(String action, UUID userId) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setUserId(userId);
        log.setTimestamp(LocalDateTime.now());
        return auditRepository.save(log).thenApply(v -> null);
    }
}
```

---

## Advanced Patterns

### Pattern 1: Service Factory with Injection

```java
public class ServiceFactory {
    
    private final RepositoryManager repositoryManager;
    
    public ServiceFactory() {
        this.repositoryManager = RepositoryManager.getInstance();
    }
    
    public <T> T createService(Class<T> serviceClass) {
        try {
            T service = serviceClass.getDeclaredConstructor().newInstance();
            repositoryManager.injectInto(service);
            return service;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to create service: " + serviceClass.getName(), e);
        }
    }
}

// Usage
ServiceFactory factory = new ServiceFactory();
UserService userService = factory.createService(UserService.class);
GameService gameService = factory.createService(GameService.class);
```

### Pattern 2: Lazy Service Initialization

```java
public class MyPlugin extends JavaPlugin {
    
    private UserService userService;
    private GameService gameService;
    
    @Override
    public void onEnable() {
        // Initialize repository system
        setupRepositoryManager();
    }
    
    public UserService getUserService() {
        if (userService == null) {
            userService = new UserService();
            RepositoryManager.getInstance().injectInto(userService);
        }
        return userService;
    }
    
    public GameService getGameService() {
        if (gameService == null) {
            gameService = new GameService();
            RepositoryManager.getInstance().injectInto(gameService);
        }
        return gameService;
    }
}
```

### Pattern 3: Inheritance with Injection

```java
public abstract class BaseService {
    
    @InjectRepository
    protected AuditLogRepository auditRepository;
    
    protected CompletableFuture<Void> logAction(String action, UUID userId) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setUserId(userId);
        log.setTimestamp(LocalDateTime.now());
        return auditRepository.save(log).thenApply(v -> null);
    }
}

public class UserService extends BaseService {
    
    @InjectRepository
    private UserRepository userRepository;
    
    public CompletableFuture<User> createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        
        return userRepository.save(user)
            .thenCompose(savedUser -> 
                logAction("USER_CREATED", savedUser.getId())
                    .thenApply(v -> savedUser)
            );
    }
}

// Injection works across inheritance hierarchy
UserService service = new UserService();
RepositoryManager.getInstance().injectInto(service);
// Both userRepository and auditRepository are injected
```

---

## Error Handling

### Handling Uninitialized Manager

```java
try {
    RepositoryManager manager = RepositoryManager.getInstance();
} catch (IllegalStateException e) {
    getLogger().severe("RepositoryManager not initialized: " + e.getMessage());
    // Handle gracefully - maybe disable plugin
    getServer().getPluginManager().disablePlugin(this);
}
```

### Handling Unregistered Repository

```java
try {
    UserService service = new UserService();
    RepositoryManager.getInstance().injectInto(service);
} catch (IllegalStateException e) {
    getLogger().severe("Failed to inject repositories: " + e.getMessage());
    // Log which repository is missing and register it
    if (e.getMessage().contains("UserRepository")) {
        getLogger().info("Registering missing UserRepository...");
        RepositoryManager.getInstance().register(
            UserRepository.class, 
            User.class, 
            User::getId
        );
        // Retry injection
        RepositoryManager.getInstance().injectInto(service);
    }
}
```

### Handling Repository Operation Failures

```java
public class UserService {
    
    @InjectRepository
    private UserRepository userRepository;
    
    public void createUserSafely(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        
        userRepository.save(user)
            .exceptionally(throwable -> {
                getLogger().severe("Failed to save user: " + throwable.getMessage());
                // Handle error - maybe notify admins
                return null;
            })
            .thenAccept(savedUser -> {
                if (savedUser != null) {
                    getLogger().info("User created: " + savedUser.getUsername());
                }
            });
    }
}
```

---

## Best Practices

### 1. Initialize Early

```java
@Override
public void onEnable() {
    // Initialize as first step
    setupRepositoryManager();
    
    // Then do everything else
    registerCommands();
    registerListeners();
    startTasks();
}
```

### 2. Use Private Fields

```java
// Good - encapsulated
public class UserService {
    @InjectRepository
    private UserRepository userRepository;
}

// Avoid - exposes internal dependency
public class UserService {
    @InjectRepository
    public UserRepository userRepository;
}
```

### 3. Keep Services Focused

```java
// Good - single responsibility
public class UserService {
    @InjectRepository
    private UserRepository userRepository;
    
    // Only user-related operations
}

public class PlayerService {
    @InjectRepository
    private PlayerRepository playerRepository;
    
    // Only player-related operations
}

// Avoid - too many responsibilities
public class MegaService {
    @InjectRepository
    private UserRepository userRepository;
    
    @InjectRepository
    private PlayerRepository playerRepository;
    
    @InjectRepository
    private GameRepository gameRepository;
    
    @InjectRepository
    private AuditRepository auditRepository;
    
    // Too many concerns in one class
}
```

### 4. Use Meaningful Cache Keys

```java
// Good - cache by the field you query most
manager.register(
    PlayerRepository.class,
    Player.class,
    Player::getMinecraftUUID  // If you query by UUID most often
);

// Or
manager.register(
    PlayerRepository.class,
    Player.class,
    Player::getUsername  // If you query by username most often
);
```

### 5. Handle Async Operations Properly

```java
// Good - proper async handling
public void loadUser(UUID id, Consumer<User> callback) {
    userRepository.findById(id)
        .thenAccept(callback)
        .exceptionally(throwable -> {
            getLogger().severe("Error loading user: " + throwable.getMessage());
            return null;
        });
}

// Avoid - blocking the thread
public User loadUserSync(UUID id) {
    return userRepository.findById(id).join();  // Blocks current thread!
}
```

### 6. Clean Up Resources

```java
@Override
public void onDisable() {
    // Shutdown executor
    if (executor != null) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
    
    // Close entity manager factory
    if (entityManagerFactory != null) {
        entityManagerFactory.close();
    }
}
```

---

## Complete Working Example

Here's a complete, working example that ties everything together:

```java
// Entity
@Entity
@Table(name = "players")
public class Player {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(nullable = false, unique = true)
    private UUID minecraftUUID;
    
    @Column(nullable = false)
    private String username;
    
    private int level;
    private int experience;
    
    // Getters and setters...
}

// Repository
public class PlayerRepository extends GenericCachedRepository<Player, UUID, UUID> {
    public PlayerRepository(
        ExecutorService executor,
        EntityManagerFactory emf,
        Class<Player> entityClass,
        Function<Player, UUID> keyExtractor
    ) {
        super(executor, emf, entityClass, keyExtractor);
    }
    
    public CompletableFuture<Player> findByMinecraftUUID(UUID uuid) {
        return this.findByField("minecraftUUID", uuid);
    }
}

// Service
public class PlayerService {
    @InjectRepository
    private PlayerRepository playerRepository;
    
    public CompletableFuture<Player> getOrCreatePlayer(UUID minecraftUUID, String username) {
        return playerRepository.findByMinecraftUUID(minecraftUUID)
            .thenCompose(existing -> {
                if (existing != null) {
                    return CompletableFuture.completedFuture(existing);
                }
                
                Player newPlayer = new Player();
                newPlayer.setMinecraftUUID(minecraftUUID);
                newPlayer.setUsername(username);
                newPlayer.setLevel(1);
                newPlayer.setExperience(0);
                
                return playerRepository.save(newPlayer);
            });
    }
    
    public CompletableFuture<Void> addExperience(UUID playerId, int amount) {
        return playerRepository.findById(playerId)
            .thenCompose(player -> {
                player.setExperience(player.getExperience() + amount);
                
                // Level up logic
                while (player.getExperience() >= player.getLevel() * 100) {
                    player.setExperience(player.getExperience() - player.getLevel() * 100);
                    player.setLevel(player.getLevel() + 1);
                }
                
                return playerRepository.update(player);
            })
            .thenApply(v -> null);
    }
}

// Plugin Main
public class MyPlugin extends JavaPlugin {
    private ExecutorService executor;
    private EntityManagerFactory emf;
    private PlayerService playerService;
    
    @Override
    public void onEnable() {
        // Setup
        this.executor = Executors.newFixedThreadPool(4);
        this.emf = Persistence.createEntityManagerFactory("myPersistenceUnit");
        
        // Initialize repository system
        RepositoryManager.initialize(executor, emf);
        RepositoryManager.getInstance().register(
            PlayerRepository.class,
            Player.class,
            Player::getId
        );
        
        // Create and inject service
        this.playerService = new PlayerService();
        RepositoryManager.getInstance().injectInto(playerService);
        
        // Register listener
        getServer().getPluginManager().registerEvents(new PlayerListener(playerService), this);
    }
    
    @Override
    public void onDisable() {
        if (executor != null) {
            executor.shutdown();
        }
        if (emf != null) {
            emf.close();
        }
    }
}

// Listener using the service
public class PlayerListener implements Listener {
    private final PlayerService playerService;
    
    public PlayerListener(PlayerService playerService) {
        this.playerService = playerService;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        org.bukkit.entity.Player bukkitPlayer = event.getPlayer();
        
        playerService.getOrCreatePlayer(
            bukkitPlayer.getUniqueId(),
            bukkitPlayer.getName()
        ).thenAccept(player -> {
            bukkitPlayer.sendMessage("Welcome! Level: " + player.getLevel());
        });
    }
}
```

---

This documentation covers all common use cases and patterns for the repository dependency injection system. For more information, refer to the JavaDoc comments in the source code.
