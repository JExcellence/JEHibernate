package de.jexcellence.hibernate.repository;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import org.hibernate.StaleObjectStateException;

import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for handling optimistic lock exceptions in repository operations.
 * Implements retry logic for operations that might face concurrent modifications.
 */
public class OptimisticLockHandler {
    
    private static final Logger logger = Logger.getLogger(OptimisticLockHandler.class.getName());
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 100;
    
    /**
     * Executes the given operation with retry logic for optimistic lock exceptions.
     * 
     * @param operation The database operation to execute
     * @param entityName The name of the entity being operated on (for logging)
     * @param <T> The return type of the operation
     * @return The result of the operation
     * @throws Exception If the operation fails after all retry attempts
     */
    public static <T> T executeWithRetry(Callable<T> operation, String entityName) throws Exception {
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                return operation.call();
            } catch (Exception e) {
                lastException = e;

                if (isOptimisticLockException(e.fillInStackTrace())) {
                    attempts++;
                    logger.log(Level.WARNING,
                        "Optimistic lock exception on {0}, attempt {1}/{2}: {3}", 
                        new Object[]{entityName, attempts, MAX_RETRY_ATTEMPTS, e.getMessage()});
                    
                    if (attempts < MAX_RETRY_ATTEMPTS) {
                        Thread.sleep(RETRY_DELAY_MS);
                        attempts++;
                        continue;
                    }
                }

                throw e;
            }
        }

        throw lastException;
    }
    
    /**
     * Checks if the given exception is related to optimistic locking.
     */
    private static boolean isOptimisticLockException(Throwable e) {
        if (e == null) return false;

        // Direct instance check
        if (e instanceof OptimisticLockException || e instanceof StaleObjectStateException) {
            return true;
        }

        // Check for RollbackException with OptimisticLockException cause
        if (e instanceof RollbackException && e.getCause() != null) {
            return isOptimisticLockException(e.getCause());
        }

        // Check message content as fallback
        String message = e.getMessage();
        if (message != null && (
                message.contains("OptimisticLockException") ||
                        message.contains("StaleObjectStateException") ||
                        message.contains("Row was updated or deleted by another transaction"))) {
            return true;
        }

        // Recursively check cause
        return isOptimisticLockException(e.getCause());
    }
}