package de.jexcellence.jehibernate.repository.base;

import java.util.Collection;
import java.util.List;

/**
 * Extended repository interface providing full CRUD operations including batch operations.
 * <p>
 * This sealed interface extends {@link Repository} and adds batch processing capabilities
 * for improved performance when working with multiple entities.
 * <p>
 * <b>Batch Operations:</b>
 * <ul>
 *   <li>{@link #createAll(Collection)} - Batch create with automatic flushing</li>
 *   <li>{@link #updateAll(Collection)} - Batch update with automatic flushing</li>
 *   <li>{@link #deleteAll(Collection)} - Batch delete by IDs</li>
 *   <li>{@link #findAllById(Collection)} - Batch find by IDs</li>
 * </ul>
 * <p>
 * <b>Pagination:</b>
 * <ul>
 *   <li>{@link #findAll(int, int)} - Paginated retrieval</li>
 * </ul>
 * <p>
 * <b>Performance Note:</b> Batch operations automatically flush and clear the
 * persistence context every 50 entities to prevent memory issues.
 * <p>
 * <b>Example Usage:</b>
 * <pre>{@code
 * // Batch create
 * List<User> users = List.of(
 *     new User("alice", "alice@example.com"),
 *     new User("bob", "bob@example.com")
 * );
 * List<User> created = userRepo.createAll(users);
 * 
 * // Pagination
 * List<User> page1 = userRepo.findAll(0, 20);  // First 20 users
 * List<User> page2 = userRepo.findAll(1, 20);  // Next 20 users
 * }</pre>
 *
 * @param <T> the entity type
 * @param <I> the ID type
 * @author JEHibernate
 * @version 2.0
 * @since 1.0
 * @see Repository
 * @see AsyncRepository
 */

public sealed interface CrudRepository<T, I> extends Repository<T, I>
    permits AsyncRepository {
    
    /**
     * Creates a new entity.
     *
     * @param entity the entity to create (must not be null)
     * @return the created entity with generated ID
     */
    T create(T entity);
    
    /**
     * Creates multiple entities in a batch operation.
     * <p>
     * This method automatically flushes and clears the persistence context
     * every 50 entities to prevent memory issues with large batches.
     * <p>
     * <b>Performance:</b> ~50x faster than individual creates for large batches.
     *
     * @param entities the entities to create (must not be null or empty)
     * @return list of created entities with generated IDs
     */
    List<T> createAll(Collection<T> entities);
    
    /**
     * Updates an existing entity.
     *
     * @param entity the entity to update (must not be null, must have ID)
     * @return the updated entity
     */
    T update(T entity);
    
    /**
     * Updates multiple entities in a batch operation.
     * <p>
     * This method automatically flushes and clears the persistence context
     * every 50 entities to prevent memory issues with large batches.
     *
     * @param entities the entities to update (must not be null or empty)
     * @return list of updated entities
     */
    List<T> updateAll(Collection<T> entities);
    
    /**
     * Deletes multiple entities by their IDs.
     * <p>
     * This is a bulk delete operation that executes a single DELETE query.
     *
     * @param ids the entity IDs to delete (must not be null or empty)
     */
    void deleteAll(Collection<I> ids);

    /**
     * Finds multiple entities by their IDs.
     *
     * @param ids the entity IDs to find (must not be null or empty)
     * @return list of found entities (may be smaller than input if some IDs don't exist)
     */
    List<T> findAllById(Collection<I> ids);
    
    /**
     * Saves multiple entities in a batch operation.
     * Each entity is either created (if new) or updated (if existing).
     *
     * @param entities the entities to save
     * @return list of saved entities
     */
    List<T> saveAll(Collection<T> entities);

    /**
     * Deletes an entity instance directly.
     *
     * @param entity the entity to delete (must not be null)
     */
    void deleteEntity(T entity);

    /**
     * Retrieves a page of entities.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * List<User> page1 = userRepo.findAll(0, 20);  // First 20 users
     * List<User> page2 = userRepo.findAll(1, 20);  // Next 20 users
     * }</pre>
     *
     * @param pageNumber the page number (0-based)
     * @param pageSize the number of entities per page
     * @return list of entities for the requested page
     */
    List<T> findAll(int pageNumber, int pageSize);
}
