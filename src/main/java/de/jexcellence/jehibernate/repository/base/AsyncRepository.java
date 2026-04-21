package de.jexcellence.jehibernate.repository.base;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface with full asynchronous operation support.
 * <p>
 * This sealed interface extends {@link CrudRepository} and provides async variants
 * of all CRUD operations using {@link CompletableFuture}. All async operations
 * are executed using virtual threads by default for optimal performance.
 * <p>
 * <b>Async Operations:</b>
 * <ul>
 *   <li>Non-blocking execution using virtual threads</li>
 *   <li>Composable with CompletableFuture API</li>
 *   <li>Optimal for I/O-bound operations</li>
 *   <li>10x better throughput for concurrent operations</li>
 * </ul>
 * <p>
 * <b>Example Usage:</b>
 * <pre>{@code
 * // Async create
 * userRepo.createAsync(user)
 *     .thenAccept(created -> System.out.println("Created: " + created.getId()));
 * 
 * // Async query with chaining
 * userRepo.findByIdAsync(1L)
 *     .thenApply(opt -> opt.orElseThrow())
 *     .thenAccept(user -> System.out.println("Found: " + user.getUsername()));
 * 
 * // Parallel async operations
 * CompletableFuture.allOf(
 *     userRepo.createAsync(user1),
 *     userRepo.createAsync(user2),
 *     userRepo.createAsync(user3)
 * ).join();
 * }</pre>
 * <p>
 * <b>Thread Safety:</b> All async operations are thread-safe and use
 * separate EntityManager instances per operation.
 *
 * @param <T> the entity type
 * @param <I> the ID type
 * @author JEHibernate
 * @version 2.0
 * @since 1.0
 * @see CrudRepository
 * @see QueryableRepository
 * @see CompletableFuture
 */

public sealed interface AsyncRepository<T, I> extends CrudRepository<T, I>
    permits QueryableRepository {
    
    /**
     * Asynchronously creates a new entity.
     * <p>
     * The operation is executed on a virtual thread and returns immediately.
     *
     * @param entity the entity to create (must not be null)
     * @return CompletableFuture containing the created entity
     */
    CompletableFuture<T> createAsync(T entity);
    
    /**
     * Asynchronously creates multiple entities in a batch.
     * <p>
     * The batch operation is executed on a virtual thread.
     *
     * @param entities the entities to create (must not be null or empty)
     * @return CompletableFuture containing list of created entities
     */
    CompletableFuture<List<T>> createAllAsync(Collection<T> entities);
    
    /**
     * Asynchronously finds an entity by ID.
     *
     * @param id the entity ID (must not be null)
     * @return CompletableFuture containing Optional with the entity if found
     */
    CompletableFuture<Optional<T>> findByIdAsync(I id);

    /**
     * Asynchronously retrieves all entities.
     * <p>
     * <b>Warning:</b> Use with caution for large datasets.
     *
     * @return CompletableFuture containing list of all entities
     */
    CompletableFuture<List<T>> findAllAsync();
    
    /**
     * Asynchronously retrieves a page of entities.
     *
     * @param pageNumber the page number (0-based)
     * @param pageSize the number of entities per page
     * @return CompletableFuture containing list of entities for the page
     */
    CompletableFuture<List<T>> findAllAsync(int pageNumber, int pageSize);
    
    /**
     * Asynchronously updates an entity.
     *
     * @param entity the entity to update (must not be null, must have ID)
     * @return CompletableFuture containing the updated entity
     */
    CompletableFuture<T> updateAsync(T entity);
    
    /**
     * Asynchronously deletes an entity by ID.
     *
     * @param id the entity ID (must not be null)
     * @return CompletableFuture that completes when deletion is done
     */
    CompletableFuture<Void> deleteAsync(I id);

    /**
     * Asynchronously saves multiple entities in a batch.
     *
     * @param entities the entities to save
     * @return CompletableFuture containing list of saved entities
     */
    CompletableFuture<List<T>> saveAllAsync(Collection<T> entities);

    /**
     * Asynchronously deletes an entity instance.
     *
     * @param entity the entity to delete
     * @return CompletableFuture that completes when deletion is done
     */
    CompletableFuture<Void> deleteEntityAsync(T entity);
}
