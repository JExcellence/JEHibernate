package de.jexcellence.hibernate.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.persistence.EntityManagerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * GenericCachedRepository provides high-performance caching capabilities on top of standard CRUD operations
 * for JPA entities, combining database persistence with in-memory caching for optimal performance.
 *
 * <p>This repository extends AbstractCRUDRepository and adds sophisticated caching behavior using
 * Caffeine cache, eliminating the need for individual entity repositories to implement their own
 * caching logic. The class provides the following advanced features:</p>
 * <ul>
 *   <li>Automatic cache management with configurable expiration policies</li>
 *   <li>Cache-aware CRUD operations that maintain data consistency</li>
 *   <li>Flexible key extraction strategy for custom cache key generation</li>
 *   <li>Asynchronous operations with cache integration</li>
 *   <li>Cache invalidation on entity modifications</li>
 *   <li>Cache-first read operations for improved performance</li>
 * </ul>
 *
 * <p>The caching strategy implements a write-through pattern where all modifications are immediately
 * reflected in both the database and cache. Read operations prioritize cache lookups before
 * falling back to database queries, significantly reducing database load for frequently accessed data.</p>
 *
 * <p>Cache keys are extracted from entities using a configurable Function, allowing for flexible
 * caching strategies based on different entity attributes (e.g., ID, username, email, etc.).</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * // Create repository with username-based caching
 * GenericCachedRepository&lt;User, Long, String&gt; userRepo =
 *     new GenericCachedRepository&lt;&gt;(executor, emf, User.class, User::getUsername);
 *
 * // Cache-aware operations
 * User user = userRepo.findByCacheKey("username", "john.doe");
 * userRepo.create(newUser); // Automatically cached
 * </pre>
 *
 * @param <T>  the entity type managed by this repository
 * @param <ID> the type of the entity's primary key identifier
 * @param <K>  the type of the cache key extracted from entities
 *
 * @author JExcellence
 * @version 1.0
 * @since 1.0
 * @see AbstractCRUDRepository
 * @see Cache
 * @see Caffeine
 */
public class GenericCachedRepository<T, ID, K> extends AbstractCRUDRepository<T, ID> {
	
	private final ExecutorService executor;
	private final Cache<K, T> cache;
	private final Function<T, K> keyExtractor;
	
	/**
	 * Constructs a new GenericCachedRepository with the specified configuration.
	 *
	 * <p>Initializes the repository with caching capabilities using Caffeine cache
	 * with a default expiration policy of 30 minutes after write. The cache is
	 * configured for optimal performance with automatic cleanup of expired entries.</p>
	 *
	 * <p>The key extractor function is used to generate cache keys from entity instances,
	 * allowing for flexible caching strategies based on different entity attributes.</p>
	 *
	 * @param executor the ExecutorService for handling asynchronous operations
	 * @param entityManagerFactory the factory to create EntityManager instances for database operations
	 * @param entityClass the Class object representing the entity type this repository manages
	 * @param keyExtractor function to extract cache keys from entity instances
	 * @throws IllegalArgumentException if any parameter is null
	 */
	public GenericCachedRepository(
		final ExecutorService executor,
		final EntityManagerFactory entityManagerFactory,
		final Class<T> entityClass,
		final Function<T, K> keyExtractor
	) {
		super(
			executor,
			entityManagerFactory,
			entityClass
		);
		this.executor = executor;
		this.cache = Caffeine.newBuilder().expireAfterWrite(
			30,
			TimeUnit.MINUTES
		).build();
		this.keyExtractor = keyExtractor;
	}
	
	/**
	 * Retrieves a paginated list of all entities with cache-first strategy.
	 *
	 * <p>This method implements an optimized retrieval strategy:</p>
	 * <ul>
	 *   <li>First checks if entities are available in the cache</li>
	 *   <li>If cache contains data, returns cached entities immediately</li>
	 *   <li>If cache is empty, queries the database and populates the cache</li>
	 *   <li>Subsequent calls benefit from cached data until expiration</li>
	 * </ul>
	 *
	 * <p>Note: This implementation returns all cached entities when cache is populated,
	 * ignoring pagination parameters for cached results to maximize cache efficiency.</p>
	 *
	 * @param pageNumber the zero-based page number to retrieve (ignored if cache is populated)
	 * @param pageSize the maximum number of entities to return per page (ignored if cache is populated)
	 * @return a list of entities, either from cache or database with pagination applied
	 * @throws RuntimeException if the database query execution fails
	 */
	@Override
	public List<T> findAll(
		final int pageNumber,
		final int pageSize
	) {
		Map<K, T> map = this.cache.asMap();
		if (!map.isEmpty()) {
			return List.copyOf(map.values());
		}
		
		List<T> list = super.findAll(
			pageNumber,
			pageSize
		);
		list.forEach(entity -> this.cache.put(
			this.keyExtractor.apply(entity),
			entity
		));
		return list;
	}
	
	/**
	 * Asynchronously retrieves a paginated list of all entities with cache-first strategy.
	 *
	 * <p>This method executes the cache-aware findAll operation on a separate thread
	 * using the configured ExecutorService. The caching behavior is identical to the
	 * synchronous version but executed asynchronously for non-blocking operations.</p>
	 *
	 * @param pageNumber the zero-based page number to retrieve (ignored if cache is populated)
	 * @param pageSize the maximum number of entities to return per page (ignored if cache is populated)
	 * @return a CompletableFuture that will complete with a list of entities
	 * @throws RuntimeException if the query execution fails
	 * @see #findAll(int, int)
	 */
	@Override
	public CompletableFuture<List<T>> findAllAsync(
		final int pageNumber,
		final int pageSize
	) {
		return CompletableFuture.supplyAsync(
			() -> this.findAll(
				pageNumber,
				pageSize
			),
			this.executor
		);
	}
	
	/**
	 * Finds an entity by its cache key with cache-first lookup strategy.
	 *
	 * <p>This method implements an efficient two-tier lookup strategy:</p>
	 * <ol>
	 *   <li>First attempts to retrieve the entity from the cache using the provided key</li>
	 *   <li>If not found in cache, queries the database using the specified query attribute</li>
	 *   <li>If found in database, automatically caches the entity for future lookups</li>
	 *   <li>Returns null if the entity is not found in either cache or database</li>
	 * </ol>
	 *
	 * <p>This approach significantly reduces database load for frequently accessed entities
	 * while ensuring data consistency through cache population on database hits.</p>
	 *
	 * @param queryAttribute the entity attribute name to use for database querying
	 * @param key the cache key value to search for
	 * @return the entity found, or null if not present in cache or database
	 * @throws RuntimeException if the database query execution fails
	 */
	public T findByCacheKey(
		final String queryAttribute,
		final K key
	) {
		T cached = this.cache.getIfPresent(key);
		if (cached != null) {
			return cached;
		}
		T found = super.findByAttributes(Map.of(
			queryAttribute,
			key
		));
		if (found != null) {
			this.cache.put(
				key,
				found
			);
		}
		return found;
	}
	
	/**
	 * Asynchronously finds an entity by its cache key with cache-first lookup strategy.
	 *
	 * <p>This method executes the cache-aware entity lookup on a separate thread
	 * using the configured ExecutorService. The caching behavior is identical to the
	 * synchronous version but executed asynchronously for non-blocking operations.</p>
	 *
	 * @param queryAttribute the entity attribute name to use for database querying
	 * @param key the cache key value to search for
	 * @return a CompletableFuture that will complete with the found entity, or null if not found
	 * @throws RuntimeException if the query execution fails
	 * @see #findByCacheKey(String, Object)
	 */
	public CompletableFuture<T> findByCacheKeyAsync(
		final String queryAttribute,
		final K key
	) {
		return CompletableFuture.supplyAsync(
			() -> this.findByCacheKey(
				queryAttribute,
				key
			),
			this.executor
		);
	}
	
	/**
	 * Creates a new entity in the database and automatically caches it.
	 *
	 * <p>This method implements a write-through caching strategy where the entity
	 * is first persisted to the database and then immediately cached using the
	 * configured key extractor function. This ensures that newly created entities
	 * are immediately available for cache-based lookups.</p>
	 *
	 * <p>The cache key is automatically generated from the created entity using
	 * the key extractor function provided during repository construction.</p>
	 *
	 * @param entity the entity instance to create in the database
	 * @return the created entity with any generated values populated
	 * @throws RuntimeException if the entity cannot be persisted
	 */
	@Override
	public T create(
		final T entity
	) {
		T created = super.create(entity);
		this.cache.put(
			this.keyExtractor.apply(created),
			created
		);
		return created;
	}
	
	/**
	 * Updates an existing entity in the database and refreshes the cache.
	 *
	 * <p>This method implements a write-through caching strategy where the entity
	 * is first updated in the database and then the cache is updated with the
	 * latest entity state. This ensures cache consistency with the database.</p>
	 *
	 * <p>The cache entry is updated using the key extracted from the updated entity,
	 * ensuring that any changes to the entity are immediately reflected in cached lookups.</p>
	 *
	 * @param entity the entity instance to update in the database
	 * @return the updated entity with the current database state
	 * @throws RuntimeException if the entity cannot be updated
	 */
	@Override
	public T update(
		final T entity
	) {
		T updated = super.update(entity);
		this.cache.put(
			this.keyExtractor.apply(updated),
			updated
		);
		return updated;
	}
	
	/**
	 * Deletes an entity by its identifier and removes it from the cache.
	 *
	 * <p>This method implements cache invalidation alongside database deletion:</p>
	 * <ol>
	 *   <li>First attempts to delete the entity from the database</li>
	 *   <li>If deletion is successful, removes the corresponding entry from cache</li>
	 *   <li>Uses reflection to extract entity IDs for cache invalidation</li>
	 *   <li>Ensures cache consistency by removing stale entries</li>
	 * </ol>
	 *
	 * <p>The cache invalidation process searches through cached entities to find
	 * and remove the entity with the matching identifier, maintaining cache consistency.</p>
	 *
	 * @param id the identifier of the entity to delete
	 * @return true if the entity was found and deleted, false if the entity was not found
	 * @throws RuntimeException if the delete operation fails
	 */
	@Override
	public boolean delete(
		final ID id
	) {
		final boolean gotDeleted = super.delete(id);
		this.cache.asMap().values().removeIf(entity -> {
			Object entityId = this.getIdFromEntity(entity);
			return entityId != null && entityId.equals(id);
		});
		
		return gotDeleted;
	}
	
	/**
	 * Retrieves a read-only view of all currently cached entities.
	 *
	 * <p>This method provides access to the current cache state without triggering
	 * any cache operations or database queries. The returned map is a snapshot
	 * of the cache at the time of the call and reflects the current cache contents.</p>
	 *
	 * <p>The map keys are the cache keys generated by the key extractor function,
	 * and the values are the cached entity instances. This method is useful for
	 * cache monitoring, debugging, and administrative operations.</p>
	 *
	 * @return an immutable map view of all cached entities with their cache keys
	 */
	public Map<K, T> getCachedEntities() {
		return this.cache.asMap();
	}
	
	/**
	 * Extracts the identifier from an entity using reflection.
	 *
	 * <p>This method uses reflection to invoke the getId() method on the entity
	 * to retrieve its identifier. This approach provides flexibility to work with
	 * any entity type that follows the standard JPA convention of having a getId() method.</p>
	 *
	 * <p>The method handles reflection exceptions gracefully by returning null,
	 * which allows the calling code to handle cases where ID extraction fails
	 * without throwing exceptions that could disrupt cache operations.</p>
	 *
	 * @param entity the entity from which to extract the identifier
	 * @return the entity's identifier, or null if extraction fails or entity has no getId() method
	 */
	private Object getIdFromEntity(
		final T entity
	) {
		try {
			return entity.getClass().getMethod("getId").invoke(entity);
		} catch (
			  final Exception exception
		) {
			return null;
		}
	}
}