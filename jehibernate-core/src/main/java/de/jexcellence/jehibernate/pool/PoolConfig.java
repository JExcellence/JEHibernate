package de.jexcellence.jehibernate.pool;

import java.util.Properties;

/**
 * Immutable connection-pool configuration for HikariCP.
 * <p>
 * Kept separate from {@link de.jexcellence.jehibernate.config.DatabaseConfig} on purpose:
 * bundling the seven pool tuning knobs into {@code DatabaseConfig} would push that record
 * past seven components and entangle connection identity (URL/credentials) with pool tuning.
 * A dedicated value object keeps both records small and lets pool tuning evolve independently.
 * <p>
 * All durations are expressed in <b>milliseconds</b>, matching HikariCP's setter contract.
 * <p>
 * <b>Defaults</b> (see {@link #defaults()}):
 * <ul>
 *   <li>{@code maximumPoolSize = 10}</li>
 *   <li>{@code minimumIdle = 2}</li>
 *   <li>{@code idleTimeout = 600_000} (10&nbsp;min)</li>
 *   <li>{@code connectionTimeout = 30_000} (30&nbsp;s)</li>
 *   <li>{@code maxLifetime = 1_800_000} (30&nbsp;min)</li>
 *   <li>{@code leakDetectionThreshold = 60_000} (60&nbsp;s)</li>
 *   <li>{@code validationTimeout = 5_000} (5&nbsp;s)</li>
 * </ul>
 *
 * @param maximumPoolSize          maximum number of connections in the pool
 * @param minimumIdle              minimum number of idle connections HikariCP keeps ready
 * @param idleTimeoutMillis        idle time before an excess connection is retired
 * @param connectionTimeoutMillis  maximum wait for a connection from the pool
 * @param maxLifetimeMillis        maximum lifetime of a connection before retirement
 * @param leakDetectionThresholdMillis time a connection may be out of the pool before a leak warning
 * @param validationTimeoutMillis  maximum time the pool waits validating a connection
 * @since 4.0
 */
public record PoolConfig(
    int maximumPoolSize,
    int minimumIdle,
    long idleTimeoutMillis,
    long connectionTimeoutMillis,
    long maxLifetimeMillis,
    long leakDetectionThresholdMillis,
    long validationTimeoutMillis
) {

    private static final String KEY_PREFIX = "jehibernate.pool.";
    private static final String KEY_MAX_POOL_SIZE = KEY_PREFIX + "maximumPoolSize";
    private static final String KEY_MIN_IDLE = KEY_PREFIX + "minimumIdle";
    private static final String KEY_IDLE_TIMEOUT = KEY_PREFIX + "idleTimeout";
    private static final String KEY_CONNECTION_TIMEOUT = KEY_PREFIX + "connectionTimeout";
    private static final String KEY_MAX_LIFETIME = KEY_PREFIX + "maxLifetime";
    private static final String KEY_LEAK_DETECTION = KEY_PREFIX + "leakDetectionThreshold";
    private static final String KEY_VALIDATION_TIMEOUT = KEY_PREFIX + "validationTimeout";

    public PoolConfig {
        if (maximumPoolSize < 1) {
            throw new IllegalArgumentException("maximumPoolSize must be >= 1");
        }
        if (minimumIdle < 0) {
            throw new IllegalArgumentException("minimumIdle must be >= 0");
        }
        if (minimumIdle > maximumPoolSize) {
            throw new IllegalArgumentException("minimumIdle must not exceed maximumPoolSize");
        }
        if (connectionTimeoutMillis < 250) {
            throw new IllegalArgumentException("connectionTimeoutMillis must be >= 250 (HikariCP minimum)");
        }
    }

    /**
     * Returns the sensible production defaults documented on this type.
     *
     * @return a {@code PoolConfig} with default values
     */
    public static PoolConfig defaults() {
        return new PoolConfig(10, 2, 600_000L, 30_000L, 1_800_000L, 60_000L, 5_000L);
    }

    /**
     * Reads pool settings from a properties source, falling back to the supplied base
     * for any key that is absent. This lets {@code hibernate.properties} (plugin) and
     * {@code application.properties}-style files (web) share one loader API.
     *
     * @param props the properties to read {@code jehibernate.pool.*} keys from
     * @param base  the fallback configuration for absent keys (e.g. {@link #defaults()})
     * @return a {@code PoolConfig} merged from {@code props} over {@code base}
     */
    public static PoolConfig fromProperties(Properties props, PoolConfig base) {
        return new PoolConfig(
            intProp(props, KEY_MAX_POOL_SIZE, base.maximumPoolSize),
            intProp(props, KEY_MIN_IDLE, base.minimumIdle),
            longProp(props, KEY_IDLE_TIMEOUT, base.idleTimeoutMillis),
            longProp(props, KEY_CONNECTION_TIMEOUT, base.connectionTimeoutMillis),
            longProp(props, KEY_MAX_LIFETIME, base.maxLifetimeMillis),
            longProp(props, KEY_LEAK_DETECTION, base.leakDetectionThresholdMillis),
            longProp(props, KEY_VALIDATION_TIMEOUT, base.validationTimeoutMillis)
        );
    }

    public static Builder builder() {
        return new Builder(defaults());
    }

    private static int intProp(Properties props, String key, int fallback) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(value.trim());
    }

    private static long longProp(Properties props, String key, long fallback) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Long.parseLong(value.trim());
    }

    /**
     * Mutable builder seeded from {@link #defaults()}; override only what you need.
     */
    public static final class Builder {
        private int maximumPoolSize;
        private int minimumIdle;
        private long idleTimeoutMillis;
        private long connectionTimeoutMillis;
        private long maxLifetimeMillis;
        private long leakDetectionThresholdMillis;
        private long validationTimeoutMillis;

        private Builder(PoolConfig seed) {
            this.maximumPoolSize = seed.maximumPoolSize;
            this.minimumIdle = seed.minimumIdle;
            this.idleTimeoutMillis = seed.idleTimeoutMillis;
            this.connectionTimeoutMillis = seed.connectionTimeoutMillis;
            this.maxLifetimeMillis = seed.maxLifetimeMillis;
            this.leakDetectionThresholdMillis = seed.leakDetectionThresholdMillis;
            this.validationTimeoutMillis = seed.validationTimeoutMillis;
        }

        public Builder maximumPoolSize(int value) {
            this.maximumPoolSize = value;
            return this;
        }

        public Builder minimumIdle(int value) {
            this.minimumIdle = value;
            return this;
        }

        public Builder idleTimeoutMillis(long value) {
            this.idleTimeoutMillis = value;
            return this;
        }

        public Builder connectionTimeoutMillis(long value) {
            this.connectionTimeoutMillis = value;
            return this;
        }

        public Builder maxLifetimeMillis(long value) {
            this.maxLifetimeMillis = value;
            return this;
        }

        public Builder leakDetectionThresholdMillis(long value) {
            this.leakDetectionThresholdMillis = value;
            return this;
        }

        public Builder validationTimeoutMillis(long value) {
            this.validationTimeoutMillis = value;
            return this;
        }

        public PoolConfig build() {
            return new PoolConfig(
                maximumPoolSize,
                minimumIdle,
                idleTimeoutMillis,
                connectionTimeoutMillis,
                maxLifetimeMillis,
                leakDetectionThresholdMillis,
                validationTimeoutMillis
            );
        }
    }
}
