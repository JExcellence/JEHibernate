package de.jexcellence.jehibernate.exception;

import java.io.Serial;

/**
 * Exception thrown when optimistic locking retry attempts are exhausted.
 * <p>
 * This exception is thrown by {@link de.jexcellence.jehibernate.transaction.OptimisticLockRetry}
 * when the maximum number of retry attempts has been reached after encountering
 * optimistic lock conflicts.
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * try {
 *     OptimisticLockRetry.execute(() -> updateEntity(entity), 5, Duration.ofMillis(200));
 * } catch (OptimisticLockRetryException e) {
 *     // Handle exhausted retries
 * }
 * }</pre>
 * 
 * @since 1.0
 * @see TransactionException
 * @see de.jexcellence.jehibernate.transaction.OptimisticLockRetry
 */
public final class OptimisticLockRetryException extends TransactionException {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    /**
     * Constructs a new OptimisticLockRetryException with the specified detail message and cause.
     * 
     * @param message the detail message
     * @param cause the underlying cause (typically an OptimisticLockException)
     */
    public OptimisticLockRetryException(String message, Throwable cause) {
        super(message, cause);
    }
}
