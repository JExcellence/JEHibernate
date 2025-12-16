package de.jexcellence.hibernate.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import jakarta.persistence.EntityManagerFactory;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * Repository with integrated Caffeine caching for high-performance lookups.
 *
 * @param <T>  managed entity type
 * @param <ID> identifier type of the entity
 * @param <K>  cache key type
 * @author JExcellence
 * @version 1.1
 * @since 1.0
 */
public class CachedRepository<T, ID, K> extends BaseRepository<T, ID> {

    private final ExecutorService executor;
    private final Cache<K, T> keyCache;
    private final Cache<ID, T> idCache;
    private final Function<T, K> keyExtractor;
    private final Function<T, ID> idExtractor;

    public CachedRepository(
        @NotNull final ExecutorService executor,
        @NotNull final EntityManagerFactory entityManagerFactory,
        @NotNull final Class<T> entityClass,
        @NotNull final Function<T, K> keyExtractor
    ) {
        this(executor, entityManagerFactory, entityClass, keyExtractor, Duration.ofMinutes(30), 10000);
    }

    public CachedRepository(
        @NotNull final ExecutorService executor,
        @NotNull final EntityManagerFactory entityManagerFactory,
        @NotNull final Class<T> entityClass,
        @NotNull final Function<T, K> keyExtractor,
        @NotNull final Duration expiration,
        final int maxSize
    ) {
        super(executor, entityManagerFactory, entityClass);
        this.executor = executor;
        this.keyExtractor = keyExtractor;
        this.idExtractor = this.createIdExtractor();

        this.keyCache = Caffeine.newBuilder()
            .expireAfterWrite(expiration)
            .maximumSize(maxSize)
            .recordStats()
            .build();

        this.idCache = Caffeine.newBuilder()
            .expireAfterWrite(expiration)
            .maximumSize(maxSize)
            .recordStats()
            .build();
    }

    @Override
    @NotNull
    public Optional<T> findById(@NotNull final ID id) {
        var cached = this.idCache.getIfPresent(id);
        if (cached != null) {
            return Optional.of(cached);
        }

        var found = super.findById(id);
        found.ifPresent(entity -> {
            this.idCache.put(id, entity);
            this.keyCache.put(this.keyExtractor.apply(entity), entity);
        });
        return found;
    }

    @NotNull
    public Optional<T> findByKey(@NotNull final K key) {
        var cached = this.keyCache.getIfPresent(key);
        return Optional.ofNullable(cached);
    }

    @NotNull
    public Optional<T> findByKey(@NotNull final String queryAttribute, @NotNull final K key) {
        var cached = this.keyCache.getIfPresent(key);
        if (cached != null) {
            return Optional.of(cached);
        }

        var found = super.findByAttribute(queryAttribute, key);
        found.ifPresent(entity -> {
            this.keyCache.put(key, entity);
            var id = this.idExtractor.apply(entity);
            if (id != null) {
                this.idCache.put(id, entity);
            }
        });
        return found;
    }

    @NotNull
    public CompletableFuture<Optional<T>> findByKeyAsync(@NotNull final String queryAttribute, @NotNull final K key) {
        return CompletableFuture.supplyAsync(() -> this.findByKey(queryAttribute, key), this.executor);
    }

    @NotNull
    public T getOrCreate(@NotNull final String queryAttribute, @NotNull final K key, @NotNull final Function<K, T> creator) {
        return this.findByKey(queryAttribute, key).orElseGet(() -> this.create(creator.apply(key)));
    }

    @NotNull
    public CompletableFuture<T> getOrCreateAsync(
        @NotNull final String queryAttribute,
        @NotNull final K key,
        @NotNull final Function<K, T> creator
    ) {
        return CompletableFuture.supplyAsync(() -> this.getOrCreate(queryAttribute, key, creator), this.executor);
    }

    @Override
    @NotNull
    public List<T> findAll(final int pageNumber, final int pageSize) {
        var cachedEntities = this.keyCache.asMap();
        if (!cachedEntities.isEmpty() && cachedEntities.size() >= pageSize) {
            var size = Math.max(pageSize, 1);
            var offset = Math.max(pageNumber, 0) * size;
            return cachedEntities.values().stream()
                .skip(offset)
                .limit(size)
                .toList();
        }

        var entities = super.findAll(pageNumber, pageSize);
        entities.forEach(this::cacheEntity);
        return entities;
    }

    @Override
    @NotNull
    public T create(@NotNull final T entity) {
        var created = super.create(entity);
        this.cacheEntity(created);
        return created;
    }

    @Override
    @NotNull
    public List<T> createAll(@NotNull final Collection<T> entities) {
        var created = super.createAll(entities);
        created.forEach(this::cacheEntity);
        return created;
    }

    @Override
    @NotNull
    public T update(@NotNull final T entity) {
        var updated = super.update(entity);
        this.cacheEntity(updated);
        return updated;
    }

    @Override
    @NotNull
    public T save(@NotNull final T entity) {
        var saved = super.save(entity);
        this.cacheEntity(saved);
        return saved;
    }

    @Override
    public boolean delete(@NotNull final ID id) {
        var deleted = super.delete(id);
        if (deleted) {
            this.evictById(id);
        }
        return deleted;
    }

    @Override
    public void deleteEntity(@NotNull final T entity) {
        super.deleteEntity(entity);
        this.evict(entity);
    }

    public void evict(@NotNull final T entity) {
        this.keyCache.invalidate(this.keyExtractor.apply(entity));
        var id = this.idExtractor.apply(entity);
        if (id != null) {
            this.idCache.invalidate(id);
        }
    }

    public void evictById(@NotNull final ID id) {
        var cached = this.idCache.getIfPresent(id);
        if (cached != null) {
            this.keyCache.invalidate(this.keyExtractor.apply(cached));
        }
        this.idCache.invalidate(id);
    }

    public void evictByKey(@NotNull final K key) {
        var cached = this.keyCache.getIfPresent(key);
        if (cached != null) {
            var id = this.idExtractor.apply(cached);
            if (id != null) {
                this.idCache.invalidate(id);
            }
        }
        this.keyCache.invalidate(key);
    }

    public void evictAll() {
        this.keyCache.invalidateAll();
        this.idCache.invalidateAll();
    }

    public void preload() {
        super.findAll().forEach(this::cacheEntity);
    }

    @NotNull
    public CompletableFuture<Void> preloadAsync() {
        return CompletableFuture.runAsync(this::preload, this.executor);
    }

    @NotNull
    public Map<K, T> getCachedByKey() {
        return Map.copyOf(this.keyCache.asMap());
    }

    @NotNull
    public Map<ID, T> getCachedById() {
        return Map.copyOf(this.idCache.asMap());
    }

    public long getCacheSize() {
        return this.keyCache.estimatedSize();
    }

    @NotNull
    public CacheStats getKeyCacheStats() {
        return this.keyCache.stats();
    }

    @NotNull
    public CacheStats getIdCacheStats() {
        return this.idCache.stats();
    }

    private void cacheEntity(@NotNull final T entity) {
        this.keyCache.put(this.keyExtractor.apply(entity), entity);
        var id = this.idExtractor.apply(entity);
        if (id != null) {
            this.idCache.put(id, entity);
        }
    }

    @SuppressWarnings("unchecked")
    private Function<T, ID> createIdExtractor() {
        return entity -> {
            try {
                var method = entity.getClass().getMethod("getId");
                return (ID) method.invoke(entity);
            } catch (final ReflectiveOperationException e) {
                return null;
            }
        };
    }
}
