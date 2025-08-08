package de.jexcellence.hibernate.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.criteria.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AbstractCRUDRepository provides generic CRUD operations for JPA entities with both synchronous
 * and asynchronous execution capabilities.
 *
 * <p>This abstract repository class offers a comprehensive set of database operations including:</p>
 * <ul>
 *   <li>Basic CRUD operations (Create, Read, Update, Delete)</li>
 *   <li>Asynchronous variants of all operations using CompletableFuture</li>
 *   <li>Advanced querying with multiple attributes and nested properties</li>
 *   <li>Pagination support for large datasets</li>
 *   <li>Transaction management with automatic rollback on errors</li>
 * </ul>
 *
 * <p>All database operations are executed within managed transactions and include proper
 * exception handling with logging. The class uses JPA Criteria API for type-safe queries
 * and supports complex attribute-based searches including nested object properties.</p>
 *
 * <p>Thread safety is ensured through the use of an ExecutorService for asynchronous operations
 * and proper transaction isolation for each database operation.</p>
 *
 * @param <T>  the type of the entity this repository manages
 * @param <ID> the type of the entity's primary key identifier
 *
 * @author JExcellence
 * @version 1.0
 * @since 1.0
 * @see EntityManager
 * @see CompletableFuture
 * @see CriteriaBuilder
 */
public class AbstractCRUDRepository<T, ID> {
    
    private final Logger logger;
    private final Class<T> entityClass;
    private final EntityManagerFactory entityManagerFactory;
    private final ExecutorService executorService;
    
    /**
     * Constructs an AbstractCRUDRepository with the specified dependencies.
     *
     * <p>Initializes the repository with all required components for database operations.
     * The logger is automatically configured using the entity class name for better
     * traceability of operations.</p>
     *
     * @param executor the ExecutorService for handling asynchronous operations
     * @param entityManagerFactory the factory to create EntityManager instances for database operations
     * @param entityClass the Class object representing the entity type this repository manages
     * @throws IllegalArgumentException if any parameter is null
     */
    public AbstractCRUDRepository(
        final ExecutorService executor,
        final EntityManagerFactory entityManagerFactory,
        final Class<T> entityClass
    ) {
        this.entityManagerFactory = entityManagerFactory;
        this.entityClass = entityClass;
        this.logger = Logger.getLogger(entityClass.getName());
        this.executorService = executor;
    }
    
    /**
     * Asynchronously creates a new entity in the database.
     *
     * <p>This method executes the create operation on a separate thread using the configured
     * ExecutorService. The entity will be persisted within a managed transaction.</p>
     *
     * @param entity the entity instance to create in the database
     * @return a CompletableFuture that will complete with the created entity
     * @throws RuntimeException if the entity cannot be persisted
     * @see #create(Object)
     */
    public CompletableFuture<T> createAsync(T entity) {
        return CompletableFuture.supplyAsync(() -> this.create(entity), this.executorService);
    }
    
    /**
     * Asynchronously updates an existing entity in the database.
     *
     * <p>This method executes the update operation on a separate thread using the configured
     * ExecutorService. The entity will be merged within a managed transaction.</p>
     *
     * @param entity the entity instance to update in the database
     * @return a CompletableFuture that will complete with the updated entity
     * @throws RuntimeException if the entity cannot be updated
     * @see #update(Object)
     */
    public CompletableFuture<T> updateAsync(T entity) {
        return CompletableFuture.supplyAsync(() -> this.update(entity), executorService);
    }
    
    /**
     * Asynchronously deletes an entity by its identifier.
     *
     * <p>This method executes the delete operation on a separate thread using the configured
     * ExecutorService. The operation will be performed within a managed transaction.</p>
     *
     * @param id the identifier of the entity to delete
     * @return a CompletableFuture that will complete with true if the entity was deleted, false if not found
     * @throws RuntimeException if the delete operation fails
     * @see #delete(Object)
     */
    public CompletableFuture<Boolean> deleteAsync(ID id) {
        return CompletableFuture.supplyAsync(() -> this.delete(id), this.executorService);
    }
    
    /**
     * Asynchronously finds an entity by its identifier.
     *
     * <p>This method executes the find operation on a separate thread using the configured
     * ExecutorService. Uses JPA Criteria API for type-safe querying.</p>
     *
     * @param id the identifier of the entity to find
     * @return a CompletableFuture that will complete with the found entity, or null if not found
     * @throws RuntimeException if the query execution fails
     * @see #findById(Object)
     */
    public CompletableFuture<T> findByIdAsync(ID id) {
        return CompletableFuture.supplyAsync(() -> this.findById(id), this.executorService);
    }
    
    /**
     * Asynchronously retrieves a paginated list of all entities of the specified type.
     *
     * <p>This method executes the find operation on a separate thread using the configured
     * ExecutorService. Supports pagination to handle large datasets efficiently.</p>
     *
     * @param pageNumber the zero-based page number to retrieve
     * @param pageSize the maximum number of entities to return per page
     * @return a CompletableFuture that will complete with a list of entities for the specified page
     * @throws RuntimeException if the query execution fails
     * @see #findAll(int, int)
     */
    public CompletableFuture<List<T>> findAllAsync(
        int pageNumber,
        int pageSize
    ) {
        return CompletableFuture.supplyAsync(() -> this.findAll(pageNumber, pageSize), this.executorService);
    }
    
    /**
     * Asynchronously finds a single entity by multiple attributes.
     *
     * <p>This method executes the search operation on a separate thread using the configured
     * ExecutorService. Supports complex queries with multiple attribute conditions and nested properties.</p>
     *
     * @param attributes a map where keys are attribute names (supporting dot notation for nested properties)
     *                  and values are the expected attribute values
     * @return a CompletableFuture that will complete with the first matching entity, or null if none found
     * @throws RuntimeException if the query execution fails
     * @see #findByAttributes(Map)
     */
    public CompletableFuture<T> findByAttributesAsync(Map<String, Object> attributes) {
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
    public T create(final T entity) {
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
    public T update(T entity) {
        return this.executeQuery(em -> em.merge(entity));
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
    public boolean delete(ID id) {
        return this.executeQuery(em -> {
            T entity = em.find(this.entityClass, id);
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
    public T findById(final ID id) {
        return this.executeQuery(entityManager -> {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(this.entityClass);
            Root<T> root = cq.from(this.entityClass);
            
            cq.select(root).where(cb.equal(root.get("id"), id));
            return entityManager.createQuery(cq).getSingleResult();
        });
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
    public List<T> findAll(final int pageNumber, final int pageSize) {
        return this.executeQuery(entityManager -> {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(this.entityClass);
            Root<T> root = cq.from(this.entityClass);
            cq.select(root);
            
            return entityManager.createQuery(cq)
                                .setFirstResult(pageNumber * pageSize)
                                .setMaxResults(pageSize)
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
    public T findByAttributes(
        final Map<String, Object> attributes
    ) {
        return this.executeQuery(entityManager -> {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(this.entityClass);
            Root<T> root = cq.from(this.entityClass);
            
            try {
                this.buildPredicate(
                    attributes,
                    cb,
                    cq,
                    root
                );
                
                return entityManager.createQuery(cq).getResultStream().findFirst().orElse(null);
            } catch (
                  final Exception exception
            ) {
                this.logger.log(Level.WARNING, "Error building predicates: ", exception);
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
    public CompletableFuture<List<T>> findListByAttributesAsync(
        final Map<String, Object> attributes
    ) {
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
    public List<T> findListByAttributes(
        final Map<String, Object> attributes
    ) {
        return this.executeQuery(entityManager -> {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(this.entityClass);
            Root<T> root = cq.from(this.entityClass);
            
            try {
                this.buildPredicate(
                    attributes,
                    cb,
                    cq,
                    root
                );
                return entityManager.createQuery(cq).getResultList();
            } catch (
                  final Exception exception
            ) {
                this.logger.log(Level.WARNING, "Error building predicates: ", exception);
                return new ArrayList<>();
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
    public <R> R executeQuery(
        final Function<EntityManager, R> action
    ) {
        EntityTransaction transaction = null;
        try (
            EntityManager entityManager = this.entityManagerFactory.createEntityManager()
        ) {
            transaction = entityManager.getTransaction();
            transaction.begin();
            R result = action.apply(entityManager);
            transaction.commit();
            return result;
        } catch (
              final Exception exception
        ) {
            if (
                transaction != null &&
                transaction.isActive()
            ) {
                transaction.rollback();
                this.logger.log(Level.WARNING, "Transaction rolled back due to an error:");
            }
            this.logger.log(Level.WARNING, "Exception occurred: ", exception);
            throw exception;
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
    private void buildPredicate(
        final Map<String, Object> attributes,
        final CriteriaBuilder cb,
        final CriteriaQuery<T> cq,
        final Root<T> root
    ) {
        
        List<Predicate> predicates = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (key.contains(".")) {
                String[] parts = key.split("\\.");
                Path<?>  path  = root.get(parts[0]);
                
                for (int i = 1; i < parts.length; i++) {
                    path = path.get(parts[i]);
                }
                
                predicates.add(cb.equal(path, value));
            } else {
                predicates.add(cb.equal(root.get(key), value));
            }
        }
        
        cq.select(root).where(predicates.toArray(new Predicate[0]));
    }
}