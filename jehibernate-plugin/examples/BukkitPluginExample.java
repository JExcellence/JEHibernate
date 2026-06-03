package examples;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.jehibernate.entity.base.UuidEntity;
import de.jexcellence.jehibernate.repository.base.AbstractCachedRepository;
import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import de.jexcellence.jehibernate.repository.injection.Inject;
import de.jexcellence.jehibernate.transaction.OptimisticLockRetry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

// ============================================================================
// STEP 1: Define your entities
// ============================================================================

@Entity
@Table(name = "players")
class MinecraftPlayer extends UuidEntity {
    @Column(nullable = false)
    private String username;
    private String lastServer;
    private long playtime;
    private long balance;

    protected MinecraftPlayer() {}

    public MinecraftPlayer(UUID uuid, String username) {
        setId(uuid);
        this.username = username;
        this.playtime = 0;
        this.balance = 0;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getLastServer() { return lastServer; }
    public void setLastServer(String lastServer) { this.lastServer = lastServer; }
    public long getPlaytime() { return playtime; }
    public void setPlaytime(long playtime) { this.playtime = playtime; }
    public long getBalance() { return balance; }
    public void setBalance(long balance) { this.balance = balance; }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}

@Entity
@Table(name = "warps")
class Warp extends LongIdEntity {
    @Column(nullable = false, unique = true)
    private String name;
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    protected Warp() {}

    public Warp(String name, String world, double x, double y, double z) {
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String getName() { return name; }
    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}

// ============================================================================
// STEP 2: Define your repositories
// ============================================================================

// Cached repository — keeps players in memory for fast lookups
class PlayerRepository extends AbstractCachedRepository<MinecraftPlayer, UUID, UUID> {
    public PlayerRepository(ExecutorService executor, EntityManagerFactory emf, Class<MinecraftPlayer> entityClass) {
        super(executor, emf, entityClass,
            MinecraftPlayer::getId,
            AbstractCachedRepository.CacheConfig.builder()
                .expiration(Duration.ofMinutes(30))
                .maxSize(5000)
                .expireAfterAccess(true)  // reset timer on each access
                .build());
    }
}

// Simple CRUD repository — no caching needed for warps
class WarpRepository extends AbstractCrudRepository<Warp, Long> {
    public WarpRepository(ExecutorService executor, EntityManagerFactory emf, Class<Warp> entityClass) {
        super(executor, emf, entityClass);
    }
}

// ============================================================================
// STEP 3: Service layer (optional — uses @Inject for DI)
// ============================================================================

class EconomyService {
    @Inject
    private PlayerRepository playerRepo;

    /**
     * Thread-safe balance transfer using optimistic lock retry.
     * If two threads modify the same player concurrently, the retry
     * catches the version conflict and re-reads before trying again.
     */
    public void transfer(UUID from, UUID to, long amount) {
        OptimisticLockRetry.executeVoid(() -> {
            var sender = playerRepo.findByIdOrThrow(from);
            var receiver = playerRepo.findByIdOrThrow(to);

            if (sender.getBalance() < amount) {
                throw new IllegalArgumentException("Insufficient balance");
            }

            sender.setBalance(sender.getBalance() - amount);
            receiver.setBalance(receiver.getBalance() + amount);
            playerRepo.saveAll(List.of(sender, receiver));
        });
    }

    public long getBalance(UUID playerId) {
        return playerRepo.findByIdOrThrow(playerId).getBalance();
    }
}

// ============================================================================
// STEP 4: Plugin main class
// ============================================================================

/**
 * Example Bukkit/Spigot/Paper plugin using JEHibernate.
 *
 * Directory structure in your plugin JAR:
 *   resources/
 *     plugin.yml
 *     database/
 *       hibernate.properties    <-- database config (copied to plugin data folder)
 *       simplelogger.properties <-- SLF4J logging config
 */
public class BukkitPluginExample /* extends JavaPlugin */ {

    private static final Logger LOGGER = LoggerFactory.getLogger(BukkitPluginExample.class);

    private JEHibernate jeHibernate;
    private PlayerRepository playerRepo;
    private WarpRepository warpRepo;

    // === OPTION A: Load from properties file (recommended) ===

    public void onEnableWithProperties() {
        // Intentionally empty: implement this method to load database configuration
        // from a properties file using PropertyLoader and JEHibernate.builder()
        // .configuration(cfg -> cfg.fromProperties(props)).build().
        // See onEnableWithBuilder() for the programmatic equivalent.
        throw new UnsupportedOperationException("onEnableWithProperties() is not yet implemented");
    }

    // === OPTION B: Programmatic config (for simple setups) ===

    public void onEnableWithBuilder() {
        jeHibernate = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:file:./plugins/MyPlugin/database/mydb;MODE=MySQL;AUTO_SERVER=TRUE")
                .credentials("sa", "")
                .ddlAuto("update")
                .batchSize(50))
            .scanPackages("examples")
            .build();

        playerRepo = jeHibernate.repositories().get(PlayerRepository.class);
        warpRepo = jeHibernate.repositories().get(WarpRepository.class);
        jeHibernate.repositories().createWithInjection(EconomyService.class);

        playerRepo.preloadAsync();
    }

    // === Event handlers (would be Bukkit @EventHandler methods) ===

    public void onPlayerJoin(UUID uuid, String username) {
        // Async — doesn't block the main thread
        playerRepo.getOrCreateAsync("id", uuid,
            id -> new MinecraftPlayer(id, username))
            .thenAccept(player -> {
                player.setLastServer("lobby");
                playerRepo.save(player);
            });
    }

    public void onPlayerQuit(UUID uuid) {
        playerRepo.findByIdAsync(uuid).thenAccept(opt ->
            opt.ifPresent(player -> {
                player.setPlaytime(player.getPlaytime() + 1);
                playerRepo.save(player);
            }));
    }

    // === Warp commands ===

    public void createWarp(String name, String world, double x, double y, double z) {
        warpRepo.save(new Warp(name, world, x, y, z));
    }

    public void listWarps(String world) {
        var warps = warpRepo.query()
            .and("world", world)
            .orderBy("name")
            .list();

        warps.forEach(w ->
            LOGGER.info("{} @ {}, {}, {}", w.getName(), w.getX(), w.getY(), w.getZ()));
    }

    public void searchWarps(String query) {
        var warps = warpRepo.query()
            .like("name", "%" + query + "%")
            .orderBy("name")
            .getPage(0, 10);

        LOGGER.info("Found {} warps (showing page 1):", warps.totalElements());
        warps.content().forEach(w -> LOGGER.info("  {}", w.getName()));
    }

    // === Shutdown ===

    public void onDisable() {
        if (jeHibernate != null) {
            jeHibernate.close();
        }
    }
}
