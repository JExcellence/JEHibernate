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
 * AbstractCRUDRepository provides generic CRUD operations for entities.
 *
 * @param <T>  the type of the entity
 * @param <ID> the type of the entity's identifier
 */
public class AbstractCRUDRepository<T, ID> {

    private final Logger logger;
    private final Class<T> entityClass;
    private final EntityManagerFactory entityManagerFactory;
    private final ExecutorService executorService;

    /**
     * Constructs an AbstractCRUDRepository with the specified EntityManagerFactory and entity class.
     *
     * @param entityManagerFactory the factory to create EntityManager instances
     * @param entityClass          the class of the entity
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
     * @param entity the entity to create
     * @return a CompletableFuture containing the created entity
     */
    public CompletableFuture<T> createAsync(T entity) {
        return CompletableFuture.supplyAsync(() -> this.create(entity), this.executorService);
    }

    /**
     * Asynchronously updates an existing entity in the database.
     *
     * @param entity the entity to update
     * @return a CompletableFuture containing the updated entity
     */
    public CompletableFuture<T> updateAsync(T entity) {
        return CompletableFuture.supplyAsync(() -> update(entity), executorService);
    }

    /**
     * Asynchronously deletes an entity by its identifier.
     *
     * @param id the identifier of the entity to delete
     * @return a CompletableFuture representing the completion of the operation
     */
    public CompletableFuture<Void> deleteAsync(ID id) {
        return CompletableFuture.runAsync(() -> delete(id), executorService);
    }

    /**
     * Asynchronously finds an entity by its identifier.
     *
     * @param id the identifier of the entity to find
     * @return a CompletableFuture containing the found entity
     */
    public CompletableFuture<T> findByIdAsync(ID id) {
        return CompletableFuture.supplyAsync(() -> findById(id), executorService);
    }

    /**
     * Asynchronously retrieves all entities of the specified type.
     *
     * @return a CompletableFuture containing a list of all entities
     */
    public CompletableFuture<List<T>> findAllAsync(
            int pageNumber,
            int pageSize
    ) {
        return CompletableFuture.supplyAsync(() -> findAll(pageNumber, pageSize), executorService);
    }

    /**
     * Asynchronously finds an entity by multiple attributes.
     *
     * @param attributes a map of attribute names and their corresponding values
     * @return a CompletableFuture containing the found entity
     */
    public CompletableFuture<T> findByAttributesAsync(Map<String, Object> attributes) {
        return CompletableFuture.supplyAsync(() -> findByAttributes(attributes), executorService);
    }

    /**
     * Creates a new entity in the database.
     *
     * @param entity the entity to create
     * @return the created entity
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
     * @param entity the entity to update
     * @return the updated entity
     */
    public T update(T entity) {
        return this.executeQuery(em -> em.merge(entity));
    }

    /**
     * Deletes an entity by its identifier.
     *
     * @param id the identifier of the entity to delete
     */
    public void delete(ID id) {
        this.executeQuery(em -> {
            T entity = em.find(this.entityClass, id);
            if (entity != null) {
                em.remove(entity);
            }
            return null;
        });
    }

    /**
     * Finds an entity by its identifier.
     *
     * @param id the identifier of the entity to find
     * @return the found entity
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
     * Retrieves all entities of the specified type.
     *
     * @return a list of all entities
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
     * Finds an entity by multiple attributes.
     *
     * @param attributes a map of attribute names and their corresponding values
     * @return the found entity or null if not found
     */
    public T findByAttributes(final Map<String, Object> attributes) {
        return this.executeQuery(entityManager -> {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(this.entityClass);
            Root<T> root = cq.from(this.entityClass);

            try {
                List<Predicate> predicates = new ArrayList<>();

                for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();

                    if (key.contains(".")) {
                        String[] parts = key.split("\\.");
                        Path<?> path = root.get(parts[0]);

                        for (int i = 1; i < parts.length; i++) {
                            path = path.get(parts[i]);
                        }

                        predicates.add(cb.equal(path, value));
                    } else {
                        predicates.add(cb.equal(root.get(key), value));
                    }
                }

                cq.select(root).where(predicates.toArray(new Predicate[0]));
                return entityManager.createQuery(cq).getResultStream().findFirst().orElse(null);
            } catch (Exception e) {
                this.logger.log(Level.WARNING, "Error building predicates: " + e.getMessage(), e);
                return null;
            }
        });
    }
    
    /**
     * Finds a list asynchronously by multiple attributes.
     *
     * @param attributes a map of attribute names and their corresponding values
     * @return asynchronous list of entities matching the attributes or an empty list if none found
     */
    public CompletableFuture<List<T>> findListByAttributesAsync(final Map<String, Object> attributes) {
        return CompletableFuture.supplyAsync(() -> this.findListByAttributes(attributes), this.executorService);
    }

    /**
     * Finds a list of entities by multiple attributes.
     *
     * @param attributes a map of attribute names and their corresponding values
     * @return a list of entities matching the attributes or an empty list if none found
     */
    public List<T> findListByAttributes(final Map<String, Object> attributes) {
        return this.executeQuery(entityManager -> {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(this.entityClass);
            Root<T> root = cq.from(this.entityClass);

            try {
                List<Predicate> predicates = new ArrayList<>();

                for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();

                    if (key.contains(".")) {
                        String[] parts = key.split("\\.");
                        Path<?> path = root.get(parts[0]);

                        for (int i = 1; i < parts.length; i++) {
                            path = path.get(parts[i]);
                        }

                        predicates.add(cb.equal(path, value));
                    } else {
                        predicates.add(cb.equal(root.get(key), value));
                    }
                }

                cq.select(root).where(predicates.toArray(new Predicate[0]));
                return entityManager.createQuery(cq).getResultList();
            } catch (Exception e) {
                this.logger.log(Level.WARNING, "Error building predicates: " + e.getMessage(), e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Executes a query within a transaction and handles exceptions.
     *
     * @param action the function to execute
     * @param <R>    the type of the result
     * @return the result of the query
     */
    public <R> R executeQuery(final Function<EntityManager, R> action) {
        EntityTransaction transaction = null;
        try (EntityManager entityManager = this.entityManagerFactory.createEntityManager()) {
            transaction = entityManager.getTransaction();
            transaction.begin();
            R result = action.apply(entityManager);
            transaction.commit();
            return result;
        } catch (final Exception exception) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
                this.logger.log(Level.WARNING, "Transaction rolled back due to: {0}", exception.getLocalizedMessage());
            }
            this.logger.log(Level.WARNING, "Exception occurred: ", exception);
            throw exception;
        }
    }
}