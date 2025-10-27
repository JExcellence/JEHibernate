package de.jexcellence.hibernate.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.persistence.EntityManagerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Repository variant adding a local Caffeine cache for cache-key lookups.
 */
public class GenericCachedRepository<T, ID, K> extends AbstractCRUDRepository<T, ID> {

    private final ExecutorService executor;
    private final Cache<K, T> cache;
    private final Function<T, K> keyExtractor;

    public GenericCachedRepository(
        @NotNull final ExecutorService executor,
        @NotNull final EntityManagerFactory entityManagerFactory,
        @NotNull final Class<T> entityClass,
        @NotNull final Function<T, K> keyExtractor
    ) {
        super(executor, entityManagerFactory, entityClass);
        this.executor = Objects.requireNonNull(executor, "executor");
        this.keyExtractor = Objects.requireNonNull(keyExtractor, "keyExtractor");
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();
    }

    @Override
    @NotNull
    public List<T> findAll(final int pageNumber, final int pageSize) {
        final Map<K, T> cachedEntities = this.cache.asMap();
        if (!cachedEntities.isEmpty()) {
            final int size = Math.max(pageSize, 1);
            final int offset = Math.max(pageNumber, 0) * size;
            return cachedEntities.values().stream()
                .skip(offset)
                .limit(size)
                .toList();
        }

        final List<T> entities = super.findAll(pageNumber, pageSize);
        entities.forEach(entity -> this.cache.put(this.keyExtractor.apply(entity), entity));
        return entities;
    }

    @Override
    @NotNull
    public CompletableFuture<List<T>> findAllAsync(final int pageNumber, final int pageSize) {
        return CompletableFuture.supplyAsync(() -> this.findAll(pageNumber, pageSize), this.executor);
    }

    @Nullable
    public T findByCacheKey(@NotNull final String queryAttribute, @NotNull final K key) {
        final T cached = this.cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        final T found = super.findByAttributes(Map.of(queryAttribute, key));
        if (found != null) {
            this.cache.put(key, found);
        }
        return found;
    }

    @NotNull
    public CompletableFuture<@Nullable T> findByCacheKeyAsync(
        @NotNull final String queryAttribute,
        @NotNull final K key
    ) {
        return CompletableFuture.supplyAsync(() -> this.findByCacheKey(queryAttribute, key), this.executor);
    }

    @Override
    @NotNull
    public T create(@NotNull final T entity) {
        final T created = super.create(entity);
        this.cache.put(this.keyExtractor.apply(created), created);
        return created;
    }

    @Override
    @NotNull
    public T update(@NotNull final T entity) {
        final T updated = super.update(entity);
        this.cache.put(this.keyExtractor.apply(updated), updated);
        return updated;
    }

    @Override
    public boolean delete(@NotNull final ID id) {
        final boolean deleted = super.delete(id);
        if (deleted) {
            this.cache.asMap().entrySet().removeIf(entry -> Objects.equals(this.extractId(entry.getValue()), id));
        }
        return deleted;
    }

    @NotNull
    public Map<K, T> getCachedEntities() {
        return Map.copyOf(this.cache.asMap());
    }

    @Nullable
    private Object extractId(@NotNull final T entity) {
        try {
            final Method method = entity.getClass().getMethod("getId");
            return method.invoke(entity);
        } catch (final ReflectiveOperationException ignored) {
            return null;
        }
    }
}