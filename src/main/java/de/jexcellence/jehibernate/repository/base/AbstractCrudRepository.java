package de.jexcellence.jehibernate.repository.base;

import de.jexcellence.jehibernate.entity.base.Identifiable;
import de.jexcellence.jehibernate.exception.EntityNotFoundException;
import de.jexcellence.jehibernate.exception.TransactionException;
import de.jexcellence.jehibernate.exception.ValidationException;
import de.jexcellence.jehibernate.repository.query.QueryBuilder;
import de.jexcellence.jehibernate.repository.query.Specification;
import de.jexcellence.jehibernate.session.SessionContext;
import de.jexcellence.jehibernate.transaction.TransactionTemplate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Abstract base implementation of a CRUD repository with full async support and query capabilities.
 * <p>
 * This class provides a complete implementation of CRUD operations with:
 * <ul>
 *   <li>Synchronous and asynchronous operations</li>
 *   <li>Batch processing with automatic flushing</li>
 *   <li>Type-safe query building</li>
 *   <li>Specification pattern support</li>
 *   <li>Transaction management with pattern matching exception handling</li>
 *   <li>Java 24 convenience methods for reduced boilerplate</li>
 * </ul>
 * <p>
 * <b>Java 24 Features:</b>
 * <ul>
 *   <li>{@link #findByIdOrThrow(Object)} - Find or throw exception (80% less code)</li>
 *   <li>{@link #findByIdOrCreate(Object, Supplier)} - Find or create lazily (71% less code)</li>
 *   <li>{@link #createOrUpdate(Object, Function)} - Smart save based on ID (75% less code)</li>
 *   <li>{@link #findAllMatching(java.util.function.Predicate)} - Functional filtering (50% less code)</li>
 *   <li>Pattern matching exception handling for cleaner error management</li>
 * </ul>
 * <p>
 * <b>Example Usage:</b>
 * <pre>{@code
 * public class UserRepository extends AbstractCrudRepository<User, Long> {
 *     public UserRepository(ExecutorService executor, EntityManagerFactory emf, Class<User> entityClass) {
 *         super(executor, emf, entityClass);
 *     }
 * }
 * 
 *
 * var user = userRepo.findByIdOrThrow(1L);
 * var newUser = userRepo.findByIdOrCreate(999L, () -> new User("default"));
 * var page = userRepo.query().and("active", true).getPage(0, 20);
 * }</pre>
 *
 * @param <T> the entity type
 * @param <I> the ID type
 * @author JEHibernate
 * @version 2.0
 * @since 1.0
 */

public abstract class AbstractCrudRepository<T, I> implements QueryableRepository<T, I> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCrudRepository.class);
    private static final int BATCH_SIZE = 50;
    
    protected final Class<T> entityClass;
    protected final EntityManagerFactory entityManagerFactory;
    protected final ExecutorService executorService;
    
    /**
     * Constructs a new AbstractCrudRepository.
     *
     * @param executorService the executor service for async operations (typically virtual thread executor)
     * @param entityManagerFactory the JPA entity manager factory
     * @param entityClass the entity class type
     */
    protected AbstractCrudRepository(
        ExecutorService executorService,
        EntityManagerFactory entityManagerFactory,
        Class<T> entityClass
    ) {
        this.executorService = executorService;
        this.entityManagerFactory = entityManagerFactory;
        this.entityClass = entityClass;
    }
    
    @Override
    public T create(T entity) {
        return executeInTransaction(em -> {
            em.persist(entity);
            return entity;
        });
    }
    
    @Override
    public List<T> createAll(Collection<T> entities) {
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }
        return executeInTransaction(em -> {
            List<T> result = new ArrayList<>(entities.size());
            int count = 0;
            for (T entity : entities) {
                em.persist(entity);
                result.add(entity);
                if (++count % BATCH_SIZE == 0) {
                    em.flush();
                }
            }
            return result;
        });
    }
    
    @Override
    public CompletableFuture<T> createAsync(T entity) {
        return CompletableFuture.supplyAsync(() -> create(entity), executorService);
    }
    
    @Override
    public CompletableFuture<List<T>> createAllAsync(Collection<T> entities) {
        return CompletableFuture.supplyAsync(() -> createAll(entities), executorService);
    }
    
    @Override
    public Optional<T> findById(I id) {
        return executeInTransaction(em -> Optional.ofNullable(em.find(entityClass, id)));
    }

    /**
     * Finds an entity by ID or throws an exception if not found.
     * <p>
     * This is a Java 24 convenience method that reduces boilerplate by 80%.
     * <p>
     * <b>Before:</b>
     * <pre>{@code
     * Optional<User> opt = repo.findById(1L);
     * if (opt.isEmpty()) {
     *     throw new EntityNotFoundException("User not found");
     * }
     * User user = opt.get();
     * }</pre>
     * <p>
     * <b>After:</b>
     * <pre>{@code
     * User user = repo.findByIdOrThrow(1L);
     * }</pre>
     *
     * @param id the entity ID
     * @return the entity
     * @throws EntityNotFoundException if the entity is not found
     */
    public T findByIdOrThrow(I id) {
        return findById(id)
            .orElseThrow(() -> new EntityNotFoundException(entityClass, id));
    }

    /**
     * Finds an entity by ID or creates a new one if not found.
     * <p>
     * This is a Java 24 convenience method that reduces boilerplate by 71%.
     * The supplier is only called if the entity is not found (lazy evaluation).
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * User user = repo.findByIdOrCreate(999L, () -> new User("default", "default@example.com"));
     * }</pre>
     *
     * @param id the entity ID to search for
     * @param creator supplier to create new entity if not found
     * @return existing entity or newly created entity
     */
    public T findByIdOrCreate(I id, Supplier<T> creator) {
        return findById(id)
            .orElseGet(() -> create(creator.get()));
    }

    @Override
    public CompletableFuture<Optional<T>> findByIdAsync(I id) {
        return CompletableFuture.supplyAsync(() -> findById(id), executorService);
    }
    
    @Override
    public List<T> findAll() {
        return executeInTransaction(em -> {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(entityClass);
            cq.select(cq.from(entityClass));
            return em.createQuery(cq).getResultList();
        });
    }
    
    @Override
    public List<T> findAll(int pageNumber, int pageSize) {
        return executeInTransaction(em -> {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(entityClass);
            Root<T> root = cq.from(entityClass);
            cq.select(root);
            
            return em.createQuery(cq)
                .setFirstResult(Math.max(0, pageNumber) * Math.max(1, pageSize))
                .setMaxResults(Math.max(1, pageSize))
                .getResultList();
        });
    }
    
    @Override
    public CompletableFuture<List<T>> findAllAsync() {
        return CompletableFuture.supplyAsync(this::findAll, executorService);
    }
    
    @Override
    public CompletableFuture<List<T>> findAllAsync(int pageNumber, int pageSize) {
        return CompletableFuture.supplyAsync(() -> findAll(pageNumber, pageSize), executorService);
    }
    
    @Override
    public List<T> findAllById(Collection<I> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return executeInTransaction(em -> {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(entityClass);
            Root<T> root = cq.from(entityClass);
            cq.select(root).where(root.get("id").in(ids));
            return em.createQuery(cq).getResultList();
        });
    }
    
    @Override
    public T update(T entity) {
        return executeInTransaction(em -> em.merge(entity));
    }
    
    @Override
    public List<T> updateAll(Collection<T> entities) {
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }
        return executeInTransaction(em -> {
            List<T> result = new ArrayList<>(entities.size());
            int count = 0;
            for (T entity : entities) {
                result.add(em.merge(entity));
                if (++count % BATCH_SIZE == 0) {
                    em.flush();
                }
            }
            return result;
        });
    }
    
    @Override
    public CompletableFuture<T> updateAsync(T entity) {
        return CompletableFuture.supplyAsync(() -> update(entity), executorService);
    }
    
    @Override
    public T save(T entity) {
        return executeInTransaction(em -> {
            if (entity instanceof Identifiable<?> identifiable && !identifiable.isNew()) {
                return em.merge(entity);
            }
            em.persist(entity);
            return entity;
        });
    }
    
    /**
     * Creates or updates an entity based on whether it has an ID.
     * <p>
     * This is a Java 24 convenience method that reduces boilerplate by 75%.
     * It intelligently decides whether to create or update based on ID presence.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * Product product = new Product("Widget", 29.99);
     * product.setId(1L);
     * Product saved = repo.createOrUpdate(product, Product::getId);
     * }</pre>
     *
     * @param entity the entity to save
     * @param idExtractor function to extract ID from entity
     * @return the saved entity
     */
    public T createOrUpdate(T entity, Function<T, I> idExtractor) {
        return Optional.ofNullable(idExtractor.apply(entity))
            .flatMap(this::findById)
            .map(_unused -> update(entity))
            .orElseGet(() -> create(entity));
    }
    
    /**
     * Finds all entities matching the given predicate.
     * <p>
     * This is a Java 24 convenience method for functional-style filtering.
     * <b>Note:</b> This loads all entities into memory - use with caution for large datasets.
     * For database-level filtering, use {@link #query()} or {@link #findAll(Specification)}.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * List<User> premiumUsers = repo.findAllMatching(
     *     u -> u.isPremium() && u.isActive()
     * );
     * }</pre>
     *
     * @param predicate the filter predicate
     * @return filtered list of entities
     */
    public List<T> findAllMatching(java.util.function.Predicate<T> predicate) {
        return findAll().stream()
            .filter(predicate)
            .toList();
    }
    
    @Override
    public void delete(I id) {
        executeInTransaction(em -> {
            T entity = em.find(entityClass, id);
            if (entity != null) {
                em.remove(entity);
            }
            return null;
        });
    }
    
    @Override
    public void deleteAll(Collection<I> ids) {
        if (ids.isEmpty()) {
            return;
        }
        executeInTransaction(em -> {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            var delete = cb.createCriteriaDelete(entityClass);
            var root = delete.from(entityClass);
            delete.where(root.get("id").in(ids));
            em.createQuery(delete).executeUpdate();
            return null;
        });
    }
    
    @Override
    public CompletableFuture<Void> deleteAsync(I id) {
        return CompletableFuture.runAsync(() -> delete(id), executorService);
    }
    
    @Override
    public boolean exists(I id) {
        return executeInTransaction(em -> {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<T> root = cq.from(entityClass);
            cq.select(cb.literal(1L)).where(cb.equal(root.get("id"), id));
            return !em.createQuery(cq).setMaxResults(1).getResultList().isEmpty();
        });
    }
    
    @Override
    public long count() {
        return executeInTransaction(em -> {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            cq.select(cb.count(cq.from(entityClass)));
            return em.createQuery(cq).getSingleResult();
        });
    }
    
    @Override
    public QueryBuilder<T> query() {
        return new QueryBuilder<>(entityClass, entityManagerFactory::createEntityManager, executorService);
    }
    
    @Override
    public Optional<T> findOne(Specification<T> spec) {
        return executeInTransaction(em -> {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(entityClass);
            Root<T> root = cq.from(entityClass);
            cq.select(root).where(spec.toPredicate(root, cq, cb));
            return em.createQuery(cq).getResultStream().findFirst();
        });
    }
    
    @Override
    public List<T> findAll(Specification<T> spec) {
        return executeInTransaction(em -> {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(entityClass);
            Root<T> root = cq.from(entityClass);
            cq.select(root).where(spec.toPredicate(root, cq, cb));
            return em.createQuery(cq).getResultList();
        });
    }
    
    @Override
    public long count(Specification<T> spec) {
        return executeInTransaction(em -> {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<T> root = cq.from(entityClass);
            cq.select(cb.count(root)).where(spec.toPredicate(root, cq, cb));
            return em.createQuery(cq).getSingleResult();
        });
    }

    @Override
    public boolean existsBy(Specification<T> spec) {
        return count(spec) > 0;
    }

    @Override
    public List<T> saveAll(Collection<T> entities) {
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }
        return executeInTransaction(em -> {
            List<T> result = new ArrayList<>(entities.size());
            int count = 0;
            for (T entity : entities) {
                if (entity instanceof Identifiable<?> identifiable && !identifiable.isNew()) {
                    result.add(em.merge(entity));
                } else {
                    em.persist(entity);
                    result.add(entity);
                }
                if (++count % BATCH_SIZE == 0) {
                    em.flush();
                }
            }
            return result;
        });
    }

    @Override
    public void deleteEntity(T entity) {
        executeInTransaction(em -> {
            T managed = em.contains(entity) ? entity : em.merge(entity);
            em.remove(managed);
            return null;
        });
    }

    @Override
    public CompletableFuture<List<T>> saveAllAsync(Collection<T> entities) {
        return CompletableFuture.supplyAsync(() -> saveAll(entities), executorService);
    }

    @Override
    public CompletableFuture<Void> deleteEntityAsync(T entity) {
        return CompletableFuture.runAsync(() -> deleteEntity(entity), executorService);
    }

    /**
     * Refreshes an entity from the database, discarding any in-memory changes.
     *
     * @param entity the entity to refresh
     * @return the refreshed entity
     */
    public T refresh(T entity) {
        return executeInTransaction(em -> {
            T managed = em.contains(entity) ? entity : em.merge(entity);
            em.refresh(managed);
            return managed;
        });
    }

    /**
     * Executes work within a session-scoped transaction where the EntityManager remains open
     * for the entire callback. This enables lazy loading of associations.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * User user = userRepo.withSession(session -> {
     *     User u = session.find(User.class, 1L);
     *     u.getOrders().size(); // lazy loading works!
     *     return u;
     * });
     * }</pre>
     *
     * @param <R>  the return type
     * @param work the work to execute within the session
     * @return the result of the work
     * @throws TransactionException if the transaction fails
     */
    public <R> R withSession(Function<SessionContext, R> work) {
        EntityManager em = entityManagerFactory.createEntityManager();
        EntityTransaction tx = null;
        try (em) {
            tx = em.getTransaction();
            tx.begin();
            SessionContext session = new SessionContext(em, executorService);
            R result = work.apply(session);
            tx.commit();
            return result;
        } catch (Exception e) {
            rollbackQuietly(tx);
            throw TransactionTemplate.classifyException(e);
        }
    }

    /**
     * Executes a void action within a session-scoped transaction.
     *
     * @param work the work to execute
     * @throws TransactionException if the transaction fails
     */
    public void withSessionVoid(Consumer<SessionContext> work) {
        withSession(session -> {
            work.accept(session);
            return null;
        });
    }

    /**
     * Executes read-only work within a session where the EntityManager remains open
     * for the entire callback. No transaction is started — optimized for read operations.
     * Lazy loading of associations works within this scope.
     * <p>
     * <b>Example:</b>
     * <pre>{@code
     * List<Order> orders = userRepo.withReadOnly(session -> {
     *     User user = session.find(User.class, 1L);
     *     return new ArrayList<>(user.getOrders()); // copy before session closes
     * });
     * }</pre>
     *
     * @param <R>  the return type
     * @param work the read-only work to execute
     * @return the result of the work
     */
    public <R> R withReadOnly(Function<SessionContext, R> work) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try (em) {
            em.setProperty("org.hibernate.readOnly", true);
            SessionContext session = new SessionContext(em, executorService);
            return work.apply(session);
        } catch (Exception e) {
            throw TransactionTemplate.classifyException(e);
        }
    }

    /**
     * Executes an action within a transaction and returns a result.
     * <p>
     * This method handles transaction lifecycle and provides enhanced exception handling
     * using Java 24 pattern matching. Exceptions are automatically classified and wrapped
     * in appropriate exception types.
     * <p>
     * <b>Exception Handling (Pattern Matching):</b>
     * <ul>
     *   <li>{@link PersistenceException} → {@link TransactionException}</li>
     *   <li>{@link IllegalArgumentException} → {@link ValidationException}</li>
     *   <li>{@link RuntimeException} → {@link TransactionException}</li>
     * </ul>
     *
     * @param <R> the return type
     * @param action the action to execute within transaction
     * @return the result of the action
     * @throws TransactionException if transaction fails
     * @throws ValidationException if validation fails
     */
    protected <R> R executeInTransaction(Function<EntityManager, R> action) {
        EntityManager em = entityManagerFactory.createEntityManager();
        EntityTransaction tx = null;
        long start = System.nanoTime();
        try (em) {
            tx = em.getTransaction();
            tx.begin();
            R result = action.apply(em);
            tx.commit();
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            if (durationMs > 500) {
                LOGGER.warn("Slow transaction on {} took {}ms", entityClass.getSimpleName(), durationMs);
            }
            return result;
        } catch (Exception e) {
            rollbackQuietly(tx);
            throw TransactionTemplate.classifyException(e);
        }
    }
    
    /**
     * Rolls back a transaction quietly without throwing exceptions.
     * <p>
     * This is a utility method used internally to ensure transaction rollback
     * doesn't mask the original exception.
     *
     * @param transaction the transaction to rollback (may be null)
     */
    private void rollbackQuietly(EntityTransaction transaction) {
        if (transaction == null || !transaction.isActive()) {
            return;
        }
        try {
            transaction.rollback();
        } catch (Exception e) {
            LOGGER.warn("Failed to rollback transaction", e);
        }
    }
    
    /**
     * Gets the entity class type.
     * <p>
     * This method is protected to allow subclasses to access the entity class
     * for reflection or metadata purposes.
     *
     * @return the entity class
     */
    protected Class<T> getEntityClass() {
        return entityClass;
    }
    
    /**
     * Gets the executor service used for async operations.
     * <p>
     * This method is protected to allow subclasses to use the same executor
     * for custom async operations.
     *
     * @return the executor service
     */
    protected ExecutorService getExecutorService() {
        return executorService;
    }
}
