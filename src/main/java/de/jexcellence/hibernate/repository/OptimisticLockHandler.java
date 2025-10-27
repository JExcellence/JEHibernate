package de.jexcellence.hibernate.repository;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import org.hibernate.StaleObjectStateException;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility providing retry support for operations that may fail with optimistic locking conflicts.
 */
public final class OptimisticLockHandler {

    private static final Logger LOGGER = Logger.getLogger(OptimisticLockHandler.class.getName());
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 100L;

    private OptimisticLockHandler() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Executes {@code operation} and retries on optimistic lock conflicts up to {@value #MAX_RETRY_ATTEMPTS} times.
     *
     * @param operation  unit of work that might trigger an {@link OptimisticLockException}
     * @param entityName identifier used in log messages for contextual information
     * @param <T>        result type
     * @return the operation result when it eventually succeeds
     * @throws Exception if retries are exhausted or a non-optimistic lock exception occurs
     */
    public static <T> T executeWithRetry(
        @NotNull final Callable<T> operation,
        @NotNull final String entityName
    ) throws Exception {
        Objects.requireNonNull(operation, "operation");
        if (entityName.isBlank()) {
            throw new IllegalArgumentException("entityName must not be blank");
        }

        int attempts = 0;
        Exception lastException = null;

        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                return operation.call();
            } catch (final Exception exception) {
                lastException = exception;

                if (!isOptimisticLockException(exception)) {
                    throw exception;
                }

                attempts++;
                LOGGER.log(Level.WARNING,
                    "Optimistic lock on {0}, retry {1}/{2}",
                    new Object[]{entityName, attempts, MAX_RETRY_ATTEMPTS});

                if (attempts >= MAX_RETRY_ATTEMPTS) {
                    break;
                }

                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
            }
        }

        throw lastException;
    }

    private static boolean isOptimisticLockException(final Throwable throwable) {
        if (throwable == null) {
            return false;
        }

        if (throwable instanceof OptimisticLockException || throwable instanceof StaleObjectStateException) {
            return true;
        }

        if (throwable instanceof RollbackException rollback && rollback.getCause() != null) {
            return isOptimisticLockException(rollback.getCause());
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