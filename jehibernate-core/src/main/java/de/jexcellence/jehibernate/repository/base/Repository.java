package de.jexcellence.jehibernate.repository.base;

import java.util.List;
import java.util.Optional;

/**
 * Base repository interface providing fundamental CRUD operations.
 * <p>
 * This is the root of the sealed repository hierarchy, ensuring type safety
 * and compile-time guarantees. Only permitted subtypes can implement this interface.
 * <p>
 * <b>Sealed Hierarchy:</b>
 * <pre>
 * Repository (sealed)
 *   ↓ permits
 * CrudRepository (sealed)
 *   ↓ permits
 * AsyncRepository (sealed)
 *   ↓ permits
 * QueryableRepository (non-sealed)
 *   ↓ implemented by
 * AbstractCrudRepository
 * </pre>
 * <p>
 * <b>Core Operations:</b>
 * <ul>
 *   <li>{@link #findById(Object)} - Find entity by ID</li>
 *   <li>{@link #findAll()} - Retrieve all entities</li>
 *   <li>{@link #save(Object)} - Save or update entity</li>
 *   <li>{@link #delete(Object)} - Delete entity by ID</li>
 *   <li>{@link #exists(Object)} - Check if entity exists</li>
 *   <li>{@link #count()} - Count total entities</li>
 * </ul>
 * <p>
 * <b>Example Usage:</b>
 * <pre>{@code
 * public class UserRepository extends AbstractCrudRepository<User, Long> {
 *     // Implementation provided by AbstractCrudRepository
 * }
 * 
 * // Usage
 * Optional<User> user = userRepo.findById(1L);
 * long totalUsers = userRepo.count();
 * }</pre>
 *
 * @param <T> the entity type
 * @param <I> the ID type
 * @author JEHibernate
 * @version 2.0
 * @since 1.0
 * @see CrudRepository
 * @see AsyncRepository
 * @see QueryableRepository
 */

public sealed interface Repository<T, I>
    permits CrudRepository {

    /**
     * Finds an entity by its ID.
     *
     * @param id the entity ID (must not be null)
     * @return an Optional containing the entity if found, empty otherwise
     */
    Optional<T> findById(I id);

    /**
     * Retrieves all entities of this type.
     * <p>
     * <b>Warning:</b> Use with caution for large datasets. Consider using
     * pagination methods instead.
     *
     * @return list of all entities (never null, may be empty)
     * @see CrudRepository#findAll(int, int)
     */
    List<T> findAll();

    /**
     * Saves or updates an entity.
     * <p>
     * If the entity is new (not yet persisted), it will be created.
     * If the entity already exists, it will be updated.
     *
     * @param entity the entity to save (must not be null)
     * @return the saved entity
     */
    T save(T entity);

    /**
     * Deletes an entity by its ID.
     * <p>
     * If the entity doesn't exist, this operation is a no-op.
     *
     * @param id the entity ID (must not be null)
     */
    void delete(I id);

    /**
     * Checks if an entity with the given ID exists.
     *
     * @param id the entity ID (must not be null)
     * @return true if the entity exists, false otherwise
     */
    boolean exists(I id);
    
    /**
     * Counts the total number of entities.
     *
     * @return the total count of entities
     */
    long count();
}
