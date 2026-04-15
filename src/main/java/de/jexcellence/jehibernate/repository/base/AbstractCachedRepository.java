package de.jexcellence.jehibernate.repository.base;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import de.jexcellence.jehibernate.entity.base.Identifiable;
import de.jexcellence.jehibernate.exception.RepositoryException;
import de.jexcellence.jehibernate.repository.query.Specification;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * Abstract cached repository providing dual-layer caching (by ID and by custom key)
 * on top of {@link AbstractCrudRepository}.
 * <p>
 * Uses Caffeine cache with configurable expiration, max size, and expiry strategy.
 * All mutation operations (create, update, delete, save) automatically maintain
 * cache consistency.
 * <p>
 * <b>Configuration Example:</b>
 * <pre>{@code
 * public class UserRepository extends AbstractCachedRepository<User, Long, String> {
 *     public UserRepository(ExecutorService executor, EntityManagerFactory emf, Class<User> entityClass) {
 *         super(executor, emf, entityClass,
 *             User::getUsername,  // key extractor
 *             CacheConfig.builder()
 *                 .expiration(Duration.ofMinutes(15))
 *                 .maxSize(5000)
 *                 .expireAfterAccess(true)  // reset timer on each access
 *                 .build()
 *         );
 *     }
 * }
 * }</pre>
 *
 * @param <T>  the entity type (must implement {@link Identifiable})
 * @param <ID> the ID type
 * @param <K>  the cache key type
 * @since 1.0
 */
public abstract class AbstractCachedRepository<T, ID, K> extends AbstractCrudRepository<T, ID> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCachedRepository.class);

    private final Cache<K, T> keyCache;
    private final Cache<ID, T> idCache;
    private final Function<T, K> keyExtractor;
    private final Function<T, ID> idExtractor;

    /**
     * Cache configuration record with builder support.
     */
    public record CacheConfig(
        Duration expiration,
        int maxSize,
        boolean expireAfterAccess
    ) {
        public CacheConfig {
            if (expiration == null) expiration = Duration.ofMinutes(30);
            if (maxSize <= 0) maxSize = 10_000;
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private Duration expiration = Duration.ofMinutes(30);
            private int maxSize = 10_000;
            private boolean expireAfterAccess = false;

            public Builder expiration(Duration expiration) { this.expiration = expiration; return this; }
            public Builder maxSize(int maxSize) { this.maxSize = maxSize; return this; }
            public Builder expireAfterAccess(boolean expireAfterAccess) { this.expireAfterAccess = expireAfterAccess; return this; }
            public CacheConfig build() { return new CacheConfig(expiration, maxSize, expireAfterAccess); }
        }
    }

    protected AbstractCachedRepository(
        ExecutorService executorService,
        EntityManagerFactory entityManagerFactory,
        Class<T> entityClass,
        Function<T, K> keyExtractor
    ) {
        this(executorService, entityManagerFactory, entityClass, keyExtractor,
            CacheConfig.builder().build());
    }

    protected AbstractCachedRepository(
        ExecutorService executorService,
        EntityManagerFactory entityManagerFactory,
        Class<T> entityClass,
        Function<T, K> keyExtractor,
        Duration expiration,
        int maxSize
    ) {
        this(executorService, entityManagerFactory, entityClass, keyExtractor,
            CacheConfig.builder().expiration(expiration).maxSize(maxSize).build());
    }

    protected AbstractCachedRepository(
        ExecutorService executorService,
        EntityManagerFactory entityManagerFactory,
        Class<T> entityClass,
        Function<T, K> keyExtractor,
        CacheConfig config
    ) {
        super(executorService, entityManagerFactory, entityClass);
        this.keyExtractor = keyExtractor;
        this.idExtractor = createIdExtractor(entityClass);

        Caffeine<Object, Object> builder = Caffeine.newBuilder()
            .maximumSize(config.maxSize())
            .recordStats();

        if (config.expireAfterAccess()) {
            builder.expireAfterAccess(config.expiration());
        } else {
            builder.expireAfterWrite(config.expiration());
        }

        this.keyCache = builder.build();
        // Build a separate cache instance with same config
        Caffeine<Object, Object> idBuilder = Caffeine.newBuilder()
            .maximumSize(config.maxSize())
            .recordStats();
        if (config.expireAfterAccess()) {
            idBuilder.expireAfterAccess(config.expiration());
        } else {
            idBuilder.expireAfterWrite(config.expiration());
        }
        this.idCache = idBuilder.build();

        LOGGER.debug("Cache initialized for {} — maxSize={}, expiration={}, strategy={}",
            entityClass.getSimpleName(), config.maxSize(), config.expiration(),
            config.expireAfterAccess() ? "expireAfterAccess" : "expireAfterWrite");
    }

    // --- Overridden CRUD methods with cache maintenance ---

    @Override
    public Optional<T> findById(ID id) {
        T cached = idCache.getIfPresent(id);
        if (cached != null) {
            return Optional.of(cached);
        }

        Optional<T> found = super.findById(id);
        found.ifPresent(this::cacheEntity);
        return found;
    }

    @Override
    public T create(T entity) {
        T created = super.create(entity);
        cacheEntity(created);
        return created;
    }

    @Override
    public List<T> createAll(Collection<T> entities) {
        List<T> created = super.createAll(entities);
        created.forEach(this::cacheEntity);
        return created;
    }

    @Override
    public T update(T entity) {
        T updated = super.update(entity);
        cacheEntity(updated);
        return updated;
    }

    @Override
    public List<T> updateAll(Collection<T> entities) {
        List<T> updated = super.updateAll(entities);
        updated.forEach(this::cacheEntity);
        return updated;
    }

    @Override
    public T save(T entity) {
        T saved = super.save(entity);
        cacheEntity(saved);
        return saved;
    }

    @Override
    public List<T> saveAll(Collection<T> entities) {
        List<T> saved = super.saveAll(entities);
        saved.forEach(this::cacheEntity);
        return saved;
    }

    @Override
    public void delete(ID id) {
        evictById(id);
        super.delete(id);
    }

    @Override
    public void deleteEntity(T entity) {
        evict(entity);
        super.deleteEntity(entity);
    }

    @Override
    public void deleteAll(Collection<ID> ids) {
        ids.forEach(this::evictById);
        super.deleteAll(ids);
    }

    // --- Cache-specific query methods ---

    /**
     * Find an entity by its cache key (memory-only lookup).
     *
     * @param key the cache key
     * @return the cached entity, or empty if not in cache
     */
    public Optional<T> findByKey(K key) {
        return Optional.ofNullable(keyCache.getIfPresent(key));
    }

    /**
     * Find an entity by its cache key, falling back to a database query if not cached.
     *
     * @param queryAttribute the entity attribute to query
     * @param key            the key value
     * @return the entity, or empty
     */
    public Optional<T> findByKey(String queryAttribute, K key) {
        T cached = keyCache.getIfPresent(key);
        if (cached != null) {
            return Optional.of(cached);
        }

        Optional<T> found = query().and(queryAttribute, key).first();
        found.ifPresent(this::cacheEntity);
        return found;
    }

    public CompletableFuture<Optional<T>> findByKeyAsync(String queryAttribute, K key) {
        return CompletableFuture.supplyAsync(() -> findByKey(queryAttribute, key), executorService);
    }

    /**
     * Get an entity by key, creating it if not found.
     *
     * @param queryAttribute the entity attribute to query
     * @param key            the key value
     * @param creator        function to create a new entity from the key
     * @return existing or newly created entity
     */
    public T getOrCreate(String queryAttribute, K key, Function<K, T> creator) {
        return findByKey(queryAttribute, key).orElseGet(() -> create(creator.apply(key)));
    }

    public CompletableFuture<T> getOrCreateAsync(String queryAttribute, K key, Function<K, T> creator) {
        return CompletableFuture.supplyAsync(() -> getOrCreate(queryAttribute, key, creator), executorService);
    }

    // --- Eviction ---

    public void evict(T entity) {
        keyCache.invalidate(keyExtractor.apply(entity));
        ID id = idExtractor.apply(entity);
        if (id != null) {
            idCache.invalidate(id);
        }
    }

    public void evictById(ID id) {
        T cached = idCache.getIfPresent(id);
        if (cached != null) {
            keyCache.invalidate(keyExtractor.apply(cached));
        }
        idCache.invalidate(id);
    }

    public void evictByKey(K key) {
        T cached = keyCache.getIfPresent(key);
        if (cached != null) {
            ID id = idExtractor.apply(cached);
            if (id != null) {
                idCache.invalidate(id);
            }
        }
        keyCache.invalidate(key);
    }

    public void evictAll() {
        keyCache.invalidateAll();
        idCache.invalidateAll();
        LOGGER.debug("All caches cleared for {}", getEntityClass().getSimpleName());
    }

    // --- Preloading ---

    /**
     * Preloads all entities into the cache.
     * <b>Warning:</b> Use with caution for large datasets.
     */
    public void preload() {
        List<T> all = findAll();
        all.forEach(this::cacheEntity);
        LOGGER.info("Preloaded {} entities into cache for {}", all.size(), getEntityClass().getSimpleName());
    }

    public CompletableFuture<Void> preloadAsync() {
        return CompletableFuture.runAsync(this::preload, executorService);
    }

    // --- Cache inspection ---

    public Map<K, T> getCachedByKey() {
        return Map.copyOf(keyCache.asMap());
    }

    public Map<ID, T> getCachedById() {
        return Map.copyOf(idCache.asMap());
    }

    public long getCacheSize() {
        return keyCache.estimatedSize();
    }

    public long getIdCacheSize() {
        return idCache.estimatedSize();
    }

    public CacheStats getKeyCacheStats() {
        return keyCache.stats();
    }

    public CacheStats getIdCacheStats() {
        return idCache.stats();
    }

    // --- Internal ---

    private void cacheEntity(T entity) {
        K key = keyExtractor.apply(entity);
        if (key != null) {
            keyCache.put(key, entity);
        }
        ID id = idExtractor.apply(entity);
        if (id != null) {
            idCache.put(id, entity);
        }
    }

    /**
     * Creates an ID extractor using the {@link Identifiable} interface.
     * No reflection — direct interface method call.
     */
    @SuppressWarnings("unchecked")
    private Function<T, ID> createIdExtractor(Class<T> entityClass) {
        if (Identifiable.class.isAssignableFrom(entityClass)) {
            return entity -> ((Identifiable<ID>) entity).getId();
        }
        // Fallback for entities not implementing Identifiable — use reflection once to find getId
        LOGGER.warn("Entity {} does not implement Identifiable — using reflection for ID extraction. " +
            "Consider implementing Identifiable<ID> for better performance.", entityClass.getName());
        return entity -> {
            try {
                var method = entity.getClass().getMethod("getId");
                return (ID) method.invoke(entity);
            } catch (Exception e) {
                return null;
            }
        };
    }
}
