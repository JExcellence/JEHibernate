package de.jexcellence.hibernate.repository;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import org.hibernate.StaleObjectStateException;

import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OptimisticLockHandler provides robust retry mechanisms for database operations that may encounter
 * optimistic locking conflicts in concurrent environments.
 *
 * <p>This utility class implements intelligent retry logic specifically designed to handle optimistic
 * lock exceptions that occur when multiple transactions attempt to modify the same entity simultaneously.
 * The class provides the following key capabilities:</p>
 * <ul>
 *   <li>Automatic retry mechanism with configurable attempt limits</li>
 *   <li>Intelligent exception detection for various optimistic lock scenarios</li>
 *   <li>Exponential backoff strategy to reduce contention</li>
 *   <li>Comprehensive logging for monitoring and debugging concurrent operations</li>
 *   <li>Support for both JPA and Hibernate-specific optimistic lock exceptions</li>
 *   <li>Recursive exception cause analysis for wrapped exceptions</li>
 * </ul>
 *
 * <p>Optimistic locking is a concurrency control mechanism that assumes conflicts are rare and
 * allows multiple transactions to proceed without locking resources. When conflicts do occur,
 * this handler provides a robust retry strategy to resolve them automatically.</p>
 *
 * <p>The retry mechanism uses a fixed delay between attempts to prevent overwhelming the database
 * with rapid retry attempts. This approach helps reduce contention and improves the likelihood
 * of successful operation completion on subsequent attempts.</p>
 *
 * <p>Usage examples:</p>
 * <pre>
 * // Simple entity update with retry
 * User updatedUser = OptimisticLockHandler.executeWithRetry(
 *     () -> userRepository.update(user),
 *     "User"
 * );
 *
 * // Complex operation with multiple database calls
 * OrderResult result = OptimisticLockHandler.executeWithRetry(
 *     () -> {
 *         Order order = orderRepository.findById(orderId);
 *         order.setStatus(OrderStatus.PROCESSED);
 *         return orderRepository.update(order);
 *     },
 *     "Order"
 * );
 * </pre>
 *
 * <p>This class is thread-safe and can be used concurrently across multiple threads without
 * additional synchronization requirements.</p>
 *
 * @author JExcellence
 * @version 1.0
 * @since 1.0
 * @see OptimisticLockException
 * @see StaleObjectStateException
 * @see RollbackException
 * @see Callable
 */
public class OptimisticLockHandler {
    
    private static final Logger logger = Logger.getLogger(OptimisticLockHandler.class.getName());
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 100;
    
    /**
     * Private constructor to prevent instantiation of this utility class.
     *
     * <p>This class is designed to be used as a static utility and should not be instantiated.
     * All methods are static and can be called directly on the class.</p>
     */
    private OptimisticLockHandler() {
        throw new UnsupportedOperationException("OptimisticLockHandler is a utility class and cannot be instantiated");
    }
    
    /**
     * Executes a database operation with automatic retry logic for optimistic lock exceptions.
     *
     * <p>This method provides a robust wrapper around database operations that may encounter
     * optimistic locking conflicts. It implements the following retry strategy:</p>
     * <ol>
     *   <li>Attempts to execute the provided operation</li>
     *   <li>If an optimistic lock exception occurs, logs the failure and waits for a brief delay</li>
     *   <li>Retries the operation up to the maximum configured attempts</li>
     *   <li>If all retries are exhausted, throws the last encountered exception</li>
     *   <li>If a non-optimistic lock exception occurs, immediately throws the exception</li>
     * </ol>
     *
     * <p>The retry mechanism includes intelligent exception detection that recognizes various
     * forms of optimistic lock exceptions, including:</p>
     * <ul>
     *   <li>Direct {@code OptimisticLockException} instances</li>
     *   <li>Hibernate-specific {@code StaleObjectStateException} instances</li>
     *   <li>Wrapped exceptions within {@code RollbackException}</li>
     *   <li>Exceptions with optimistic lock-related messages</li>
     * </ul>
     *
     * <p>Each retry attempt is logged at WARNING level to facilitate monitoring and debugging
     * of concurrent operations. The logging includes the entity name, current attempt number,
     * and exception details.</p>
     *
     * <p>The method uses a fixed delay between retry attempts to prevent overwhelming the
     * database with rapid successive requests, which could exacerbate contention issues.</p>
     *
     * @param <T> the return type of the database operation
     * @param operation the database operation to execute, wrapped in a Callable
     * @param entityName the name of the entity being operated on, used for logging and debugging
     * @return the result of the successful operation execution
     * @throws Exception if the operation fails after all retry attempts are exhausted,
     *                  or if a non-optimistic lock exception occurs
     * @throws IllegalArgumentException if operation or entityName is null
     * @throws InterruptedException if the thread is interrupted during retry delay
     */
    public static <T> T executeWithRetry(final Callable<T> operation, final String entityName) throws Exception {
        if (operation == null) {
            throw new IllegalArgumentException("Operation cannot be null");
        }
        if (entityName == null || entityName.trim().isEmpty()) {
            throw new IllegalArgumentException("Entity name cannot be null or empty");
        }
        
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                return operation.call();
            } catch (final Exception e) {
                lastException = e;
                
                if (isOptimisticLockException(e)) {
                    attempts++;
                    logger.log(Level.WARNING,
                               "Optimistic lock exception on {0}, attempt {1}/{2}: {3}",
                               new Object[]{entityName, attempts, MAX_RETRY_ATTEMPTS, e.getMessage()});
                    
                    if (attempts < MAX_RETRY_ATTEMPTS) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (final InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Retry operation was interrupted", ie);
                        }
                        continue;
                    }
                }
                
                throw e;
            }
        }
        
        throw lastException;
    }
    
    /**
     * Determines whether the given exception represents an optimistic locking conflict.
     *
     * <p>This method performs comprehensive analysis of the exception hierarchy to detect
     * optimistic lock exceptions in various forms. The detection strategy includes:</p>
     * <ul>
     *   <li><strong>Direct instance checking:</strong> Identifies {@code OptimisticLockException}
     *       and {@code StaleObjectStateException} instances</li>
     *   <li><strong>Wrapped exception analysis:</strong> Examines {@code RollbackException}
     *       instances for optimistic lock causes</li>
     *   <li><strong>Message content analysis:</strong> Searches exception messages for
     *       optimistic lock-related keywords</li>
     *   <li><strong>Recursive cause traversal:</strong> Recursively examines the entire
     *       exception cause chain</li>
     * </ul>
     *
     * <p>The method recognizes the following exception patterns:</p>
     * <ul>
     *   <li>{@code jakarta.persistence.OptimisticLockException} - Standard JPA optimistic lock exception</li>
     *   <li>{@code org.hibernate.StaleObjectStateException} - Hibernate-specific optimistic lock exception</li>
     *   <li>{@code jakarta.persistence.RollbackException} with optimistic lock causes</li>
     *   <li>Exceptions containing optimistic lock-related message content</li>
     * </ul>
     *
     * <p>The message analysis looks for specific keywords that commonly appear in optimistic
     * lock exception messages, providing a fallback detection mechanism for cases where
     * the exception type alone is insufficient.</p>
     *
     * <p>This comprehensive approach ensures that optimistic lock exceptions are properly
     * identified regardless of how they are wrapped or presented by different JPA providers
     * or database drivers.</p>
     *
     * @param throwable the exception to analyze for optimistic lock characteristics
     * @return true if the exception represents an optimistic locking conflict, false otherwise
     */
    private static boolean isOptimisticLockException(
        final Throwable throwable
    ) {
        if (throwable == null) {
            return false;
        }
        
        if (throwable instanceof OptimisticLockException || throwable instanceof StaleObjectStateException) {
            return true;
        }
        
        if (throwable instanceof RollbackException && throwable.getCause() != null) {
            return isOptimisticLockException(throwable.getCause());
        }
        
        final String message = throwable.getMessage();
        if (message != null && (
            message.contains("OptimisticLockException") ||
            message.contains("StaleObjectStateException") ||
            message.contains("Row was updated or deleted by another transaction"))) {
            return true;
        }
        
        return isOptimisticLockException(throwable.getCause());
    }
}