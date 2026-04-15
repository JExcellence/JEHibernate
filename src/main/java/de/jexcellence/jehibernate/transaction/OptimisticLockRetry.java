package de.jexcellence.jehibernate.transaction;

import de.jexcellence.jehibernate.exception.OptimisticLockRetryException;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Utility class for handling optimistic locking conflicts with automatic retry logic.
 * <p>
 * This class provides methods to execute operations that may encounter optimistic lock
 * exceptions, automatically retrying with exponential backoff until success or maximum
 * retries are reached.
 * <p>
 * Handles exceptions that Hibernate wraps around optimistic lock failures, including:
 * <ul>
 *   <li>{@link OptimisticLockException} (Jakarta Persistence)</li>
 *   <li>{@code StaleObjectStateException} (Hibernate)</li>
 *   <li>{@code StaleStateException} (Hibernate)</li>
 *   <li>{@code LockAcquisitionException} (Hibernate — deadlock detection, optional)</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Using default retry settings (3 retries, 100ms backoff)
 * User updatedUser = OptimisticLockRetry.execute(() -> {
 *     User user = userRepository.findById(userId).orElseThrow();
 *     user.incrementLoginCount();
 *     return userRepository.save(user);
 * });
 *
 * // Using custom retry settings with deadlock retry
 * Order order = OptimisticLockRetry.execute(
 *     () -> updateOrderStatus(orderId, newStatus),
 *     5,
 *     Duration.ofMillis(200),
 *     true  // also retry on deadlocks
 * );
 *
 * // Void operation
 * OptimisticLockRetry.executeVoid(() -> {
 *     User user = userRepository.findByIdOrThrow(userId);
 *     user.setEmail("new@example.com");
 *     userRepository.save(user);
 * });
 * }</pre>
 *
 * @since 1.0
 * @see OptimisticLockRetryException
 */
public final class OptimisticLockRetry {

    private static final Logger LOGGER = LoggerFactory.getLogger(OptimisticLockRetry.class);
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final Duration DEFAULT_BACKOFF = Duration.ofMillis(100);

    private OptimisticLockRetry() {
    }

    /**
     * Executes an operation with automatic retry on optimistic lock conflicts.
     *
     * @param <T>           the return type of the operation
     * @param operation     the operation to execute
     * @param maxRetries    the maximum number of retry attempts
     * @param backoff       the initial backoff duration (multiplied by attempt number)
     * @param retryDeadlock whether to also retry on deadlock/lock acquisition failures
     * @return the result of the operation
     * @throws OptimisticLockRetryException if all retry attempts are exhausted
     */
    public static <T> T execute(Supplier<T> operation, int maxRetries, Duration backoff, boolean retryDeadlock) {
        int attempt = 0;
        while (true) {
            try {
                return operation.get();
            } catch (RuntimeException e) {
                if (isRetryable(e, retryDeadlock)) {
                    if (++attempt >= maxRetries) {
                        throw new OptimisticLockRetryException(
                            "Failed after " + maxRetries + " retries due to: " + extractCauseType(e), e
                        );
                    }
                    Duration sleepDuration = backoff.multipliedBy(attempt);
                    LOGGER.debug("Retryable conflict detected (attempt {}/{}), backing off {}ms: {}",
                        attempt, maxRetries, sleepDuration.toMillis(), extractCauseType(e));
                    sleep(sleepDuration);
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * Executes an operation with automatic retry on optimistic lock conflicts.
     *
     * @param <T>        the return type of the operation
     * @param operation  the operation to execute
     * @param maxRetries the maximum number of retry attempts
     * @param backoff    the initial backoff duration (multiplied by attempt number)
     * @return the result of the operation
     * @throws OptimisticLockRetryException if all retry attempts are exhausted
     */
    public static <T> T execute(Supplier<T> operation, int maxRetries, Duration backoff) {
        return execute(operation, maxRetries, backoff, false);
    }

    /**
     * Executes an operation with default retry settings (3 retries, 100ms backoff).
     *
     * @param <T>       the return type of the operation
     * @param operation the operation to execute
     * @return the result of the operation
     * @throws OptimisticLockRetryException if all retry attempts are exhausted
     */
    public static <T> T execute(Supplier<T> operation) {
        return execute(operation, DEFAULT_MAX_RETRIES, DEFAULT_BACKOFF, false);
    }

    /**
     * Executes a void operation with automatic retry on optimistic lock conflicts.
     *
     * @param operation the operation to execute
     * @throws OptimisticLockRetryException if all retry attempts are exhausted
     */
    public static void executeVoid(Runnable operation) {
        execute(() -> {
            operation.run();
            return null;
        });
    }

    /**
     * Executes a void operation with custom retry settings.
     *
     * @param operation     the operation to execute
     * @param maxRetries    the maximum number of retry attempts
     * @param backoff       the initial backoff duration
     * @param retryDeadlock whether to also retry on deadlock failures
     * @throws OptimisticLockRetryException if all retry attempts are exhausted
     */
    public static void executeVoid(Runnable operation, int maxRetries, Duration backoff, boolean retryDeadlock) {
        execute(() -> {
            operation.run();
            return null;
        }, maxRetries, backoff, retryDeadlock);
    }

    /**
     * Checks whether the given exception (or any exception in its cause chain)
     * is a retryable optimistic lock or deadlock conflict.
     */
    private static boolean isRetryable(Throwable throwable, boolean includeDeadlock) {
        Throwable current = throwable;
        while (current != null) {
            String className = current.getClass().getName();
            if (current instanceof OptimisticLockException
                || className.equals("org.hibernate.StaleObjectStateException")
                || className.equals("org.hibernate.StaleStateException")) {
                return true;
            }
            if (includeDeadlock && className.equals("org.hibernate.exception.LockAcquisitionException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String extractCauseType(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof OptimisticLockException
                || current.getClass().getName().contains("Stale")
                || current.getClass().getName().contains("LockAcquisition")) {
                return current.getClass().getSimpleName();
            }
            current = current.getCause();
        }
        return throwable.getClass().getSimpleName();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retry backoff", e);
        }
    }
}
