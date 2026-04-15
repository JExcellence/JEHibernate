package de.jexcellence.jehibernate.transaction;

import de.jexcellence.jehibernate.exception.TransactionException;
import de.jexcellence.jehibernate.exception.ValidationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Template class for executing operations within JPA transactions.
 * <p>
 * Provides a simplified API for transaction management with automatic
 * entity manager lifecycle, transaction boundaries, rollback on errors,
 * and pattern-matching exception classification.
 * <p>
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * TransactionTemplate template = new TransactionTemplate(entityManagerFactory);
 *
 * // Execute with return value
 * User savedUser = template.execute(em -> {
 *     User user = new User("john@example.com");
 *     em.persist(user);
 *     return user;
 * });
 *
 * // Read-only operation (no transaction overhead)
 * List<User> users = template.executeReadOnly(em ->
 *     em.createQuery("SELECT u FROM User u", User.class).getResultList()
 * );
 * }</pre>
 *
 * @since 1.0
 * @see TransactionException
 */
public final class TransactionTemplate {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionTemplate.class);

    private final EntityManagerFactory entityManagerFactory;

    public TransactionTemplate(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    /**
     * Executes an action within a transaction and returns a result.
     * Uses pattern-matching exception handling for clean error classification.
     *
     * @param <T>    the return type
     * @param action the action to execute
     * @return the result of the action
     * @throws TransactionException if the transaction fails
     * @throws ValidationException  if validation fails
     */
    public <T> T execute(Function<EntityManager, T> action) {
        EntityManager em = entityManagerFactory.createEntityManager();
        EntityTransaction tx = null;
        try (em) {
            tx = em.getTransaction();
            tx.begin();
            T result = action.apply(em);
            tx.commit();
            return result;
        } catch (Exception e) {
            rollbackQuietly(tx);
            throw classifyException(e);
        }
    }

    /**
     * Executes an action within a transaction without returning a result.
     *
     * @param action the action to execute
     * @throws TransactionException if the transaction fails
     */
    public void executeVoid(Consumer<EntityManager> action) {
        execute(em -> {
            action.accept(em);
            return null;
        });
    }

    /**
     * Executes a read-only operation without explicit transaction management.
     * Optimized for read operations — no transaction begin/commit overhead.
     *
     * @param <T>    the return type
     * @param action the read-only action to execute
     * @return the result of the action
     */
    public <T> T executeReadOnly(Function<EntityManager, T> action) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try (em) {
            em.setProperty("org.hibernate.readOnly", true);
            return action.apply(em);
        } catch (Exception e) {
            throw classifyException(e);
        }
    }

    /**
     * Executes an action asynchronously within a transaction.
     *
     * @param <T>      the return type
     * @param action   the action to execute
     * @param executor the executor service for async execution
     * @return a CompletableFuture containing the result
     */
    public <T> CompletableFuture<T> executeAsync(Function<EntityManager, T> action, ExecutorService executor) {
        return CompletableFuture.supplyAsync(() -> execute(action), executor);
    }

    /**
     * Executes an action asynchronously within a transaction without returning a result.
     *
     * @param action   the action to execute
     * @param executor the executor service for async execution
     * @return a CompletableFuture that completes when the action finishes
     */
    public CompletableFuture<Void> executeVoidAsync(Consumer<EntityManager> action, ExecutorService executor) {
        return CompletableFuture.runAsync(() -> executeVoid(action), executor);
    }

    /**
     * Classifies an exception using pattern matching for clean error handling.
     */
    public static RuntimeException classifyException(Exception e) {
        if (e instanceof TransactionException te) return te;
        if (e instanceof ValidationException ve) return ve;
        if (e instanceof PersistenceException pe) return new TransactionException("Persistence operation failed", pe);
        if (e instanceof IllegalArgumentException iae) return new ValidationException("Invalid argument provided", iae);
        if (e instanceof RuntimeException re) return new TransactionException("Transaction failed", re);
        return new TransactionException("Unexpected error during transaction", e);
    }

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
}
