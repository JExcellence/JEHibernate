package de.jexcellence.jehibernate.repository.base;

import de.jexcellence.jehibernate.repository.query.QueryBuilder;
import de.jexcellence.jehibernate.repository.query.Specification;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface with advanced querying capabilities.
 * <p>
 * This non-sealed interface extends {@link AsyncRepository} and adds type-safe
 * query building and specification pattern support. It's the top of the repository
 * hierarchy and can be freely implemented by custom repositories.
 * <p>
 * <b>Query Capabilities:</b>
 * <ul>
 *   <li>{@link #query()} - Fluent, type-safe query builder</li>
 *   <li>{@link #findOne(Specification)} - Find single entity with specification</li>
 *   <li>{@link #findAll(Specification)} - Find multiple entities with specification</li>
 *   <li>{@link #count(Specification)} - Count entities matching specification</li>
 * </ul>
 * <p>
 * <b>Query Builder Example:</b>
 * <pre>{@code
 * var users = userRepo.query()
 *     .and("active", true)
 *     .like("email", "%@gmail.com")
 *     .greaterThan("age", 18)
 *     .orderByDesc("createdAt")
 *     .page(0, 20)
 *     .list();
 * }</pre>
 * <p>
 * <b>Specification Pattern Example:</b>
 * <pre>{@code
 * var spec = Specifications.<User>equal("active", true)
 *     .and(Specifications.like("email", "%@gmail.com"))
 *     .and(Specifications.greaterThan("age", 18));
 * 
 * List<User> users = userRepo.findAll(spec);
 * long count = userRepo.count(spec);
 * }</pre>
 * <p>
 * <b>Java 24 Features:</b>
 * <ul>
 *   <li>PageResult record for rich pagination metadata</li>
 *   <li>Async query execution with CompletableFuture</li>
 *   <li>Functional filtering with predicates</li>
 * </ul>
 *
 * @param <T> the entity type
 * @param <ID> the ID type
 * @author JEHibernate
 * @version 2.0
 * @since 1.0
 * @see QueryBuilder
 * @see Specification
 * @see AsyncRepository
 */

public non-sealed interface QueryableRepository<T, ID> extends AsyncRepository<T, ID> {
    
    /**
     * Creates a new query builder for type-safe, fluent query construction.
     * <p>
     * The query builder provides a fluent API for constructing complex queries
     * without writing JPQL or SQL.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * var users = userRepo.query()
     *     .and("active", true)
     *     .like("email", "%@gmail.com")
     *     .greaterThan("age", 18)
     *     .between("createdAt", startDate, endDate)
     *     .orderByDesc("createdAt")
     *     .page(0, 20)
     *     .list();
     * }</pre>
     *
     * @return a new QueryBuilder instance
     * @see QueryBuilder
     */
    QueryBuilder<T> query();
    
    /**
     * Finds a single entity matching the given specification.
     * <p>
     * If multiple entities match, only the first one is returned.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * var spec = Specifications.<User>equal("username", "alice")
     *     .and(Specifications.equal("active", true));
     * Optional<User> user = userRepo.findOne(spec);
     * }</pre>
     *
     * @param spec the specification to match (must not be null)
     * @return Optional containing the entity if found, empty otherwise
     * @see Specification
     */
    Optional<T> findOne(Specification<T> spec);
    
    /**
     * Finds all entities matching the given specification.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * var spec = Specifications.<User>equal("active", true)
     *     .and(Specifications.like("email", "%@gmail.com"));
     * List<User> users = userRepo.findAll(spec);
     * }</pre>
     *
     * @param spec the specification to match (must not be null)
     * @return list of matching entities (never null, may be empty)
     * @see Specification
     */
    List<T> findAll(Specification<T> spec);
    
    /**
     * Counts entities matching the given specification.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * var spec = Specifications.<User>equal("active", true);
     * long activeUsers = userRepo.count(spec);
     * }</pre>
     *
     * @param spec the specification to match (must not be null)
     * @return the count of matching entities
     * @see Specification
     */
    long count(Specification<T> spec);

    /**
     * Checks if any entity matches the given specification.
     *
     * @param spec the specification to match
     * @return true if at least one entity matches
     */
    boolean existsBy(Specification<T> spec);
}
