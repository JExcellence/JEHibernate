package de.jexcellence.hibernate.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Minimal JPA repository exposing synchronous and asynchronous CRUD helpers.
 *
 * @param <T>  managed entity type
 * @param <ID> identifier type of the entity
 */
public class AbstractCRUDRepository<T, ID> {

    private final Logger logger;
    private final Class<T> entityClass;
    private final EntityManagerFactory entityManagerFactory;
    private final ExecutorService executorService;

    /**
     * Creates a repository backed by the supplied dependencies.
     *
     * @param executor             executor used for asynchronous variants
     * @param entityManagerFactory factory creating {@link EntityManager}s
     * @param entityClass          entity metadata class
     */
    public AbstractCRUDRepository(
        @NotNull final ExecutorService executor,
        @NotNull final EntityManagerFactory entityManagerFactory,
        @NotNull final Class<T> entityClass
    ) {
        this.entityManagerFactory = Objects.requireNonNull(entityManagerFactory, "entityManagerFactory");
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass");
        this.executorService = Objects.requireNonNull(executor, "executor");
        this.logger = Logger.getLogger(entityClass.getName());
    }

    /**
     * Persists {@code entity} asynchronously.
     */
    @NotNull
    public CompletableFuture<T> createAsync(@NotNull final T entity) {
        return CompletableFuture.supplyAsync(() -> this.create(entity), this.executorService);
    }

    /**
     * Updates {@code entity} asynchronously.
     */
    @NotNull
    public CompletableFuture<T> updateAsync(@NotNull final T entity) {
        return CompletableFuture.supplyAsync(() -> this.update(entity), this.executorService);
    }

    /**
     * Deletes an entity by identifier asynchronously.
     */
    @NotNull
    public CompletableFuture<Boolean> deleteAsync(@NotNull final ID id) {
        return CompletableFuture.supplyAsync(() -> this.delete(id), this.executorService);
    }

    /**
     * Resolves an entity by identifier asynchronously.
     */
    @NotNull
    public CompletableFuture<@Nullable T> findByIdAsync(@NotNull final ID id) {
        return CompletableFuture.supplyAsync(() -> this.findById(id), this.executorService);
    }

    /**
     * Fetches a page of entities asynchronously.
     */
    @NotNull
    public CompletableFuture<List<T>> findAllAsync(final int pageNumber, final int pageSize) {
        return CompletableFuture.supplyAsync(() -> this.findAll(pageNumber, pageSize), this.executorService);
    }

    /**
     * Finds a single entity via attribute map asynchronously.
     */
    @NotNull
    public CompletableFuture<@Nullable T> findByAttributesAsync(@NotNull final Map<String, Object> attributes) {
        return CompletableFuture.supplyAsync(() -> this.findByAttributes(attributes), this.executorService);
    }
    
    /**
     * Creates a new entity in the database.
     *
     * <p>Persists the provided entity within a managed transaction. The entity will be
     * assigned a primary key if using generated values.</p>
     *
     * @param entity the entity instance to create in the database
     * @return the created entity with any generated values populated
     * @throws RuntimeException if the entity cannot be persisted
     */
    @NotNull
    public T create(@NotNull final T entity) {
        return this.executeQuery(entityManager -> {
            entityManager.persist(entity);
            return entity;
        });
    }
    
    /**
     * Updates an existing entity in the database.
     *
     * <p>Merges the provided entity with the existing database state within a managed transaction.
     * If the entity doesn't exist, it will be created.</p>
     *
     * @param entity the entity instance to update in the database
     * @return the updated entity with the current database state
     * @throws RuntimeException if the entity cannot be updated
     */
    @NotNull
    public T update(@NotNull final T entity) {
        return this.executeQuery(entityManager -> entityManager.merge(entity));
    }
    
    /**
     * Deletes an entity by its identifier.
     *
     * <p>Finds the entity by its identifier and removes it from the database within
     * a managed transaction. If the entity doesn't exist, no operation is performed.</p>
     *
     * @param id the identifier of the entity to delete
     * @return true if the entity was found and deleted, false if the entity was not found
     * @throws RuntimeException if the delete operation fails
     */
    public boolean delete(@NotNull final ID id) {
        return this.executeQuery(em -> {
            final T entity = em.find(this.entityClass, id);
            if (entity != null) {
                em.remove(entity);
                return true;
            }
            return false;
        });
    }
    
    /**
     * Finds an entity by its identifier using JPA Criteria API.
     *
     * <p>Constructs a type-safe query using the Criteria API to find an entity
     * by its primary key identifier.</p>
     *
     * @param id the identifier of the entity to find
     * @return the found entity, or null if no entity exists with the given identifier
     * @throws RuntimeException if the query execution fails
     */
    @Nullable
    public T findById(@NotNull final ID id) {
        return this.executeQuery(entityManager -> entityManager.find(this.entityClass, id));
    }
    
    /**
     * Retrieves a paginated list of all entities of the specified type.
     *
     * <p>Uses JPA Criteria API to construct a query that returns all entities
     * with pagination support for efficient handling of large datasets.</p>
     *
     * @param pageNumber the zero-based page number to retrieve
     * @param pageSize the maximum number of entities to return per page
     * @return a list of entities for the specified page, may be empty if no entities exist
     * @throws RuntimeException if the query execution fails
     */
    @NotNull
    public List<T> findAll(final int pageNumber, final int pageSize) {
        return this.executeQuery(entityManager -> {
            final CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            final CriteriaQuery<T> cq = cb.createQuery(this.entityClass);
            final Root<T> root = cq.from(this.entityClass);
            cq.select(root);

            return entityManager.createQuery(cq)
                .setFirstResult(Math.max(pageNumber, 0) * Math.max(pageSize, 1))
                .setMaxResults(Math.max(pageSize, 1))
                .getResultList();
        });
    }
    
    /**
     * Finds a single entity by multiple attributes using dynamic criteria.
     *
     * <p>Constructs a dynamic query based on the provided attributes map. Supports
     * nested property access using dot notation (e.g., "address.city"). Returns
     * the first matching entity or null if none found.</p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Map&lt;String, Object&gt; attributes = new HashMap&lt;&gt;();
     * attributes.put("name", "John");
     * attributes.put("address.city", "New York");
     * User user = repository.findByAttributes(attributes);
     * </pre>
     *
     * @param attributes a map where keys are attribute names (supporting dot notation for nested properties)
     *                  and values are the expected attribute values
     * @return the first matching entity, or null if no entity matches the criteria
     * @throws RuntimeException if the query execution fails
     */
    @Nullable
    public T findByAttributes(@NotNull final Map<String, Object> attributes) {
        return this.executeQuery(entityManager -> {
            final CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            final CriteriaQuery<T> cq = cb.createQuery(this.entityClass);
            final Root<T> root = cq.from(this.entityClass);

            try {
                this.applyPredicates(attributes, cb, cq, root);
                return entityManager.createQuery(cq).getResultStream().findFirst().orElse(null);
            } catch (final RuntimeException exception) {
                this.logger.log(Level.WARNING, "Failed to build attribute predicates", exception);
                return null;
            }
        });
    }
    
    /**
     * Asynchronously finds a list of entities by multiple attributes.
     *
     * <p>This method executes the search operation on a separate thread using the configured
     * ExecutorService. Supports complex queries with multiple attribute conditions and nested properties.</p>
     *
     * @param attributes a map where keys are attribute names (supporting dot notation for nested properties)
     *                  and values are the expected attribute values
     * @return a CompletableFuture that will complete with a list of entities matching the attributes,
     *         or an empty list if none found
     * @throws RuntimeException if the query execution fails
     * @see #findListByAttributes(Map)
     */
    @NotNull
    public CompletableFuture<List<T>> findListByAttributesAsync(@NotNull final Map<String, Object> attributes) {
        return CompletableFuture.supplyAsync(() -> this.findListByAttributes(attributes), this.executorService);
    }
    
    /**
     * Finds a list of entities by multiple attributes using dynamic criteria.
     *
     * <p>Constructs a dynamic query based on the provided attributes map. Supports
     * nested property access using dot notation (e.g., "address.city"). Returns
     * all matching entities or an empty list if none found.</p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Map&lt;String, Object&gt; attributes = new HashMap&lt;&gt;();
     * attributes.put("status", "ACTIVE");
     * attributes.put("department.name", "Engineering");
     * List&lt;Employee&gt; employees = repository.findListByAttributes(attributes);
     * </pre>
     *
     * @param attributes a map where keys are attribute names (supporting dot notation for nested properties)
     *                  and values are the expected attribute values
     * @return a list of entities matching the attributes, or an empty list if none found
     * @throws RuntimeException if the query execution fails
     */
    @NotNull
    public List<T> findListByAttributes(@NotNull final Map<String, Object> attributes) {
        return this.executeQuery(entityManager -> {
            final CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            final CriteriaQuery<T> cq = cb.createQuery(this.entityClass);
            final Root<T> root = cq.from(this.entityClass);

            try {
                this.applyPredicates(attributes, cb, cq, root);
                return entityManager.createQuery(cq).getResultList();
            } catch (final RuntimeException exception) {
                this.logger.log(Level.WARNING, "Failed to build attribute predicates", exception);
                return Collections.emptyList();
            }
        });
    }
    
    /**
     * Executes a database operation within a managed transaction with proper exception handling.
     *
     * <p>This method provides a template for executing database operations with the following guarantees:</p>
     * <ul>
     *   <li>Automatic transaction management (begin, commit, rollback)</li>
     *   <li>Proper resource cleanup (EntityManager auto-close)</li>
     *   <li>Exception logging and propagation</li>
     *   <li>Transaction rollback on any exception</li>
     * </ul>
     *
     * <p>All repository methods use this template to ensure consistent transaction behavior
     * and error handling across all database operations.</p>
     *
     * @param action the function to execute within the transaction context
     * @param <R> the type of the result returned by the action
     * @return the result of the executed action
     * @throws RuntimeException if the operation fails, after attempting transaction rollback
     */
    @NotNull
    public <R> R executeQuery(@NotNull final Function<EntityManager, R> action) {
        EntityTransaction transaction = null;
        try (EntityManager entityManager = this.entityManagerFactory.createEntityManager()) {
            transaction = entityManager.getTransaction();
            transaction.begin();
            final R result = action.apply(entityManager);
            transaction.commit();
            return result;
        } catch (final RuntimeException runtimeException) {
            this.rollbackQuietly(transaction);
            throw runtimeException;
        } catch (final Exception exception) {
            this.rollbackQuietly(transaction);
            throw new IllegalStateException("Failed to execute repository operation", exception);
        }
    }

    /**
     * Builds JPA predicates based on the provided attributes map for dynamic querying.
     *
     * <p>This method constructs equality predicates for each attribute in the map.
     * It supports nested property access using dot notation, allowing queries on
     * related entities and embedded objects.</p>
     *
     * <p>For nested properties, the method traverses the object graph by splitting
     * the attribute name on dots and building the appropriate JPA Path objects.</p>
     *
     * <p>All predicates are combined using AND logic to create the final query condition.</p>
     *
     * @param attributes a map of attribute names to their expected values
     * @param cb the CriteriaBuilder for constructing predicates
     * @param cq the CriteriaQuery to apply the predicates to
     * @param root the Root entity for the query
     * @throws RuntimeException if any attribute path is invalid or cannot be resolved
     */
    private void applyPredicates(
        @NotNull final Map<String, Object> attributes,
        @NotNull final CriteriaBuilder cb,
        @NotNull final CriteriaQuery<T> cq,
        @NotNull final Root<T> root
    ) {
        final List<Predicate> predicates = new ArrayList<>();

        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();

            if (key.contains(".")) {
                final String[] parts = key.split("\\.");
                Path<?> path = root.get(parts[0]);

                for (int i = 1; i < parts.length; i++) {
                    path = path.get(parts[i]);
                }

                predicates.add(cb.equal(path, value));
            } else {
                predicates.add(cb.equal(root.get(key), value));
            }
        }

        cq.select(root).where(predicates.toArray(Predicate[]::new));
    }

    private void rollbackQuietly(@Nullable final EntityTransaction transaction) {
        if (transaction == null || !transaction.isActive()) {
            return;
        }
        try {
            transaction.rollback();
        } catch (final RuntimeException rollbackException) {
            this.logger.log(Level.WARNING, "Failed to rollback transaction", rollbackException);
        }
    }
}