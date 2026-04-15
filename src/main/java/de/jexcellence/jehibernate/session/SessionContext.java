package de.jexcellence.jehibernate.session;

import de.jexcellence.jehibernate.repository.query.QueryBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * A session context that keeps an {@link EntityManager} open for the duration of a callback,
 * enabling lazy loading, multiple operations within the same persistence context, and
 * consistent reads.
 * <p>
 * SessionContext is created by {@code withSession()} or {@code withReadOnly()} methods
 * on repositories or the main {@code JEHibernate} entry point.
 * <p>
 * <b>Why this exists:</b> Without session scoping, each repository method creates and closes
 * its own EntityManager. Entities returned from those methods are immediately detached,
 * making lazy-loaded collections inaccessible ({@code LazyInitializationException}).
 * SessionContext solves this by keeping the EM open.
 * <p>
 * <b>Usage Example:</b>
 * <pre>{@code
 * // Lazy loading works within the session
 * userRepo.withSession(session -> {
 *     User user = session.find(User.class, 1L);
 *     List<Order> orders = user.getOrders(); // lazy-loaded, works!
 *     orders.forEach(order -> System.out.println(order.getTotal()));
 *     return user;
 * });
 *
 * // Multiple operations in one transaction
 * userRepo.withSession(session -> {
 *     User user = session.find(User.class, 1L);
 *     user.setEmail("new@example.com");
 *     session.merge(user);
 *     session.flush();
 *     return user;
 * });
 * }</pre>
 *
 * @since 2.0
 */
public final class SessionContext {

    private final EntityManager entityManager;
    private final ExecutorService executorService;

    public SessionContext(EntityManager entityManager, ExecutorService executorService) {
        this.entityManager = entityManager;
        this.executorService = executorService;
    }

    /**
     * Find an entity by its primary key.
     *
     * @param entityClass the entity class
     * @param id          the primary key
     * @param <T>         the entity type
     * @return an Optional containing the entity, or empty if not found
     */
    public <T> Optional<T> find(Class<T> entityClass, Object id) {
        return Optional.ofNullable(entityManager.find(entityClass, id));
    }

    /**
     * Persist a new entity.
     *
     * @param entity the entity to persist
     * @param <T>    the entity type
     * @return the persisted entity
     */
    public <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    /**
     * Merge a detached entity back into the persistence context.
     *
     * @param entity the entity to merge
     * @param <T>    the entity type
     * @return the managed entity instance
     */
    public <T> T merge(T entity) {
        return entityManager.merge(entity);
    }

    /**
     * Remove an entity from the database.
     *
     * @param entity the entity to remove (must be managed)
     */
    public void remove(Object entity) {
        entityManager.remove(entity);
    }

    /**
     * Refresh an entity from the database, discarding any in-memory changes.
     *
     * @param entity the entity to refresh (must be managed)
     */
    public void refresh(Object entity) {
        entityManager.refresh(entity);
    }

    /**
     * Flush pending changes to the database without committing the transaction.
     */
    public void flush() {
        entityManager.flush();
    }

    /**
     * Clear the persistence context, detaching all managed entities.
     */
    public void clear() {
        entityManager.clear();
    }

    /**
     * Create a typed JPQL query.
     *
     * @param jpql        the JPQL query string
     * @param resultClass the expected result type
     * @param <T>         the result type
     * @return a typed query
     */
    public <T> TypedQuery<T> createQuery(String jpql, Class<T> resultClass) {
        return entityManager.createQuery(jpql, resultClass);
    }

    /**
     * Create a fluent query builder bound to this session's EntityManager.
     * The query executes within the current session — no separate EM is created.
     *
     * @param entityClass the entity class to query
     * @param <T>         the entity type
     * @return a new QueryBuilder
     */
    public <T> QueryBuilder<T> query(Class<T> entityClass) {
        return new QueryBuilder<>(entityClass, () -> entityManager, executorService);
    }

    /**
     * Get the criteria builder for constructing criteria queries.
     *
     * @return the CriteriaBuilder
     */
    public CriteriaBuilder getCriteriaBuilder() {
        return entityManager.getCriteriaBuilder();
    }

    /**
     * Get the underlying EntityManager. Use with caution — prefer the typed methods above.
     *
     * @return the EntityManager
     */
    public EntityManager getEntityManager() {
        return entityManager;
    }

    /**
     * Execute a native SQL query.
     *
     * @param sql         the native SQL query
     * @param resultClass the expected result type
     * @param <T>         the result type
     * @return a list of results
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> nativeQuery(String sql, Class<T> resultClass) {
        return entityManager.createNativeQuery(sql, resultClass).getResultList();
    }

    /**
     * Check if the given entity is managed in this persistence context.
     *
     * @param entity the entity to check
     * @return true if the entity is managed
     */
    public boolean contains(Object entity) {
        return entityManager.contains(entity);
    }

    /**
     * Detach an entity from the persistence context.
     *
     * @param entity the entity to detach
     */
    public void detach(Object entity) {
        entityManager.detach(entity);
    }
}
