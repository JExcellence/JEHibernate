package de.jexcellence.jehibernate.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Utility class for logging query/operation timing with slow-query detection.
 * <p>
 * Automatically logs operation duration and warns when operations exceed the
 * configured threshold. The default slow query threshold is 500ms.
 * <p>
 * <b>Usage Example:</b>
 * <pre>{@code
 * T result = QueryLogger.timed("UserRepository.findById", () -> {
 *     return em.find(User.class, id);
 * });
 * }</pre>
 *
 * @since 2.0
 */
public final class QueryLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryLogger.class);
    private static volatile long slowQueryThresholdMs = 500;

    private QueryLogger() {
    }

    /**
     * Sets the slow query threshold in milliseconds.
     * Operations exceeding this threshold are logged at WARN level.
     *
     * @param thresholdMs the threshold in milliseconds
     */
    public static void setSlowQueryThreshold(long thresholdMs) {
        slowQueryThresholdMs = thresholdMs;
    }

    /**
     * Executes a supplier and logs timing information.
     *
     * @param operation a descriptive name for the operation
     * @param work      the work to execute
     * @param <R>       the return type
     * @return the result of the work
     */
    public static <R> R timed(String operation, Supplier<R> work) {
        long start = System.nanoTime();
        try {
            R result = work.get();
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            if (durationMs > slowQueryThresholdMs) {
                LOGGER.warn("Slow operation: {} took {}ms (threshold: {}ms)",
                    operation, durationMs, slowQueryThresholdMs);
            } else {
                LOGGER.debug("Operation: {} completed in {}ms", operation, durationMs);
            }
            return result;
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            LOGGER.error("Operation failed: {} after {}ms — {}", operation, durationMs, e.getMessage());
            throw e;
        }
    }

    /**
     * Executes a runnable and logs timing information.
     *
     * @param operation a descriptive name for the operation
     * @param work      the work to execute
     */
    public static void timedVoid(String operation, Runnable work) {
        timed(operation, () -> {
            work.run();
            return null;
        });
    }
}
