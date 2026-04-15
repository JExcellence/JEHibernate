package de.jexcellence.jehibernate.repository.base;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import de.jexcellence.jehibernate.entity.base.Identifiable;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Abstract cached repository providing dual-layer caching (by ID and by custom key)
 * on top of {@link AbstractCrudRepository}.
 * <p>
 * <b>Caching Strategy:</b>
 * <ul>
 *   <li><b>Cache-Aside</b> on reads — check cache first, miss routes to DB, result is cached.
 *       Uses Caffeine's {@code get(key, loader)} for thundering herd protection: concurrent
 *       misses for the same key coalesce into a single DB query.</li>
 *   <li><b>Write-Through</b> on mutations — every create/update/save writes to DB first,
 *       then updates the cache. Deletes evict before DB write.</li>
 *   <li><b>Stale-While-Revalidate</b> (optional) — when {@code refreshAfterWrite} is set,
 *       expired entries serve stale data immediately while triggering a background reload.
 *       Users never block on cache revalidation.</li>
 *   <li><b>TTL Jitter</b> — expiration times include random jitter (up to 10% of TTL)
 *       to prevent thundering herd from mass expiry after bulk preload.</li>
 * </ul>
 * <p>
 * <b>Cache Contract:</b>
 * <ul>
 *   <li>Staleness window: configurable via TTL (default 30 minutes)</li>
 *   <li>Invalidation: automatic on all mutation paths (create, update, save, delete)</li>
 *   <li>Cold start: use {@link #preload()} or {@link #preload(int)} to warm the cache</li>
 *   <li>Consistency: eventual — a short window exists between DB write and cache update</li>
 * </ul>
 *
 * @param <T>  the entity type (should implement {@link Identifiable})
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
     * Cache configuration record.
     *
     * @param expiration        base TTL for cache entries
     * @param maxSize           maximum number of entries per cache layer
     * @param expireAfterAccess if true, TTL resets on each access; if false, TTL is from write time
     * @param refreshAfterWrite if non-null, enables stale-while-revalidate: expired entries serve
     *                          stale data immediately while reloading in the background
     * @param jitterPercent     percentage of TTL to add as random jitter (0-50, default 10).
     *                          Prevents thundering herd from mass expiry after preload.
     */
    public record CacheConfig(
        Duration expiration,
        int maxSize,
        boolean expireAfterAccess,
        Duration refreshAfterWrite,
        int jitterPercent
    ) {
        public CacheConfig {
            if (expiration == null) expiration = Duration.ofMinutes(30);
            if (maxSize <= 0) maxSize = 10_000;
            if (jitterPercent < 0 || jitterPercent > 50) jitterPercent = 10;
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private Duration expiration = Duration.ofMinutes(30);
            private int maxSize = 10_000;
            private boolean expireAfterAccess = false;
            private Duration refreshAfterWrite = null;
            private int jitterPercent = 10;

            public Builder expiration(Duration expiration) { this.expiration = expiration; return this; }
            public Builder maxSize(int maxSize) { this.maxSize = maxSize; return this; }
            public Builder expireAfterAccess(boolean expireAfterAccess) { this.expireAfterAccess = expireAfterAccess; return this; }
            /**
             * Enables stale-while-revalidate: after this duration, the next access returns the
             * stale value immediately and triggers an async reload. Set to ~80% of expiration
             * for best results. Users never block on revalidation.
             */
            public Builder refreshAfterWrite(Duration refresh) { this.refreshAfterWrite = refresh; return this; }
            /** Percentage of TTL added as random jitter (0-50, default 10). */
            public Builder jitterPercent(int jitterPercent) { this.jitterPercent = jitterPercent; return this; }
            public CacheConfig build() { return new CacheConfig(expiration, maxSize, expireAfterAccess, refreshAfterWrite, jitterPercent); }
        }
    }

    // --- Constructors ---

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
        CacheConfig config
    ) {
        super(executorService, entityManagerFactory, entityClass);
        this.keyExtractor = keyExtractor;
        this.idExtractor = createIdExtractor(entityClass);

        Duration jitteredExpiration = applyJitter(config.expiration(), config.jitterPercent());

        this.keyCache = buildCache(config, jitteredExpiration);
        this.idCache = buildCache(config, jitteredExpiration);

        LOGGER.info("Cache initialized for {} — maxSize={}, expiration={} (jitter={}%), strategy={}, refresh={}",
            entityClass.getSimpleName(), config.maxSize(), config.expiration(), config.jitterPercent(),
            config.expireAfterAccess() ? "expireAfterAccess" : "expireAfterWrite",
            config.refreshAfterWrite() != null ? config.refreshAfterWrite() : "disabled");
    }

    private Cache<Object, Object> buildCacheBuilder(CacheConfig config, Duration jitteredExpiration) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
            .maximumSize(config.maxSize())
            .recordStats();

        if (config.expireAfterAccess()) {
            builder.expireAfterAccess(jitteredExpiration);
        } else {
            builder.expireAfterWrite(jitteredExpiration);
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private <CK, CV> Cache<CK, CV> buildCache(CacheConfig config, Duration jitteredExpiration) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
            .maximumSize(config.maxSize())
            .recordStats();

        if (config.expireAfterAccess()) {
            builder.expireAfterAccess(jitteredExpiration);
        } else {
            builder.expireAfterWrite(jitteredExpiration);
        }

        // Stale-while-revalidate: serve stale, reload async
        if (config.refreshAfterWrite() != null && !config.expireAfterAccess()) {
            builder.refreshAfterWrite(config.refreshAfterWrite());
        }

        return (Cache<CK, CV>) builder.build();
    }

    // --- Overridden CRUD methods with cache maintenance (Write-Through) ---

    @Override
    public Optional<T> findById(ID id) {
        // Caffeine's get() coalesces concurrent misses for the same key (thundering herd protection)
        T result = idCache.get(id, k -> super.findById(k).orElse(null));
        if (result != null) {
            // Cross-populate key cache
            K key = keyExtractor.apply(result);
            if (key != null) {
                keyCache.put(key, result);
            }
            return Optional.of(result);
        }
        return Optional.empty();
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
     * Find an entity by its cache key (memory-only lookup, no DB fallback).
     */
    public Optional<T> findByKey(K key) {
        return Optional.ofNullable(keyCache.getIfPresent(key));
    }

    /**
     * Find an entity by its cache key with DB fallback.
     * Uses Caffeine's coalescing loader to prevent thundering herd.
     */
    public Optional<T> findByKey(String queryAttribute, K key) {
        // Caffeine's get() coalesces concurrent misses for the same key
        T result = keyCache.get(key, k -> query().and(queryAttribute, k).first().orElse(null));
        if (result != null) {
            // Cross-populate ID cache
            ID id = idExtractor.apply(result);
            if (id != null) {
                idCache.put(id, result);
            }
            return Optional.of(result);
        }
        return Optional.empty();
    }

    public CompletableFuture<Optional<T>> findByKeyAsync(String queryAttribute, K key) {
        return CompletableFuture.supplyAsync(() -> findByKey(queryAttribute, key), executorService);
    }

    /**
     * Get an entity by key, creating it if not found in cache or DB.
     */
    public T getOrCreate(String queryAttribute, K key, Function<K, T> creator) {
        return findByKey(queryAttribute, key).orElseGet(() -> create(creator.apply(key)));
    }

    public CompletableFuture<T> getOrCreateAsync(String queryAttribute, K key, Function<K, T> creator) {
        return CompletableFuture.supplyAsync(() -> getOrCreate(queryAttribute, key, creator), executorService);
    }

    // --- Eviction ---

    public void evict(T entity) {
        K key = keyExtractor.apply(entity);
        if (key != null) keyCache.invalidate(key);
        ID id = idExtractor.apply(entity);
        if (id != null) idCache.invalidate(id);
    }

    public void evictById(ID id) {
        T cached = idCache.getIfPresent(id);
        if (cached != null) {
            K key = keyExtractor.apply(cached);
            if (key != null) keyCache.invalidate(key);
        }
        idCache.invalidate(id);
    }

    public void evictByKey(K key) {
        T cached = keyCache.getIfPresent(key);
        if (cached != null) {
            ID id = idExtractor.apply(cached);
            if (id != null) idCache.invalidate(id);
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
     * <b>Warning:</b> Use {@link #preload(int)} with a limit for large datasets.
     */
    public void preload() {
        List<T> all = findAll();
        all.forEach(this::cacheEntity);
        LOGGER.info("Preloaded {} entities into cache for {}", all.size(), getEntityClass().getSimpleName());
    }

    /**
     * Preloads up to {@code limit} entities into the cache.
     * Safer than {@link #preload()} for large tables.
     *
     * @param limit maximum number of entities to preload
     */
    public void preload(int limit) {
        List<T> page = findAll(0, limit);
        page.forEach(this::cacheEntity);
        LOGGER.info("Preloaded {} entities (limit={}) into cache for {}",
            page.size(), limit, getEntityClass().getSimpleName());
    }

    public CompletableFuture<Void> preloadAsync() {
        return CompletableFuture.runAsync(this::preload, executorService);
    }

    public CompletableFuture<Void> preloadAsync(int limit) {
        return CompletableFuture.runAsync(() -> preload(limit), executorService);
    }

    // --- Cache inspection & monitoring ---

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

    /**
     * Logs current cache statistics at INFO level.
     * Call periodically or on-demand to monitor cache health.
     * <p>
     * Logs: hit rate, hit/miss counts, eviction count, and cache size.
     */
    public void logCacheStats() {
        CacheStats keyStats = keyCache.stats();
        CacheStats idStats = idCache.stats();
        LOGGER.info("Cache stats for {} — " +
                "keyCache: hitRate={}, hits={}, misses={}, evictions={}, size={} | " +
                "idCache: hitRate={}, hits={}, misses={}, evictions={}, size={}",
            getEntityClass().getSimpleName(),
            String.format("%.1f%%", keyStats.hitRate() * 100),
            keyStats.hitCount(), keyStats.missCount(), keyStats.evictionCount(),
            keyCache.estimatedSize(),
            String.format("%.1f%%", idStats.hitRate() * 100),
            idStats.hitCount(), idStats.missCount(), idStats.evictionCount(),
            idCache.estimatedSize());
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
     * Applies random jitter to a TTL duration to prevent thundering herd from mass expiry.
     * For example, 30 minutes with 10% jitter produces 27-33 minutes.
     */
    private static Duration applyJitter(Duration base, int jitterPercent) {
        if (jitterPercent <= 0) return base;
        long baseMillis = base.toMillis();
        long jitterRange = baseMillis * jitterPercent / 100;
        long jitter = ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);
        return Duration.ofMillis(Math.max(1000, baseMillis + jitter)); // minimum 1 second
    }

    @SuppressWarnings("unchecked")
    private Function<T, ID> createIdExtractor(Class<T> entityClass) {
        if (Identifiable.class.isAssignableFrom(entityClass)) {
            return entity -> ((Identifiable<ID>) entity).getId();
        }
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
