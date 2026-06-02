package de.jexcellence.jehibernate.pool;

/**
 * Point-in-time snapshot of connection-pool health.
 * <p>
 * Returned by {@code JEHibernate.getPoolHealth()}. When the underlying data source is not a
 * HikariCP pool (e.g. an externally supplied Spring {@code DataSource}) or the pool has not yet
 * initialised, {@link #available()} is {@code false} and all counts are {@code -1}.
 *
 * @param activeConnections          connections currently in use (checked out)
 * @param idleConnections            connections currently idle in the pool
 * @param totalConnections           total connections (active + idle)
 * @param threadsAwaitingConnection  threads currently blocked waiting for a connection
 * @param available                  whether live pool metrics could be read
 * @since 4.0
 */
public record PoolHealth(
    int activeConnections,
    int idleConnections,
    int totalConnections,
    int threadsAwaitingConnection,
    boolean available
) {

    /**
     * Sentinel returned when no HikariCP metrics are obtainable.
     *
     * @return an unavailable health snapshot with all counts set to {@code -1}
     */
    public static PoolHealth unavailable() {
        return new PoolHealth(-1, -1, -1, -1, false);
    }
}
