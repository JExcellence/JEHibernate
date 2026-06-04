package de.jexcellence.jehibernate.pool;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import de.jexcellence.jehibernate.config.DatabaseConfig;

import javax.sql.DataSource;

/**
 * Builds and inspects the HikariCP {@link DataSource} that JEHibernate owns.
 * <p>
 * JEHibernate deliberately owns the {@link HikariDataSource} (handed to Hibernate via
 * {@code DatasourceConnectionProviderImpl}) rather than letting Hibernate instantiate the pool
 * through {@code HikariCPConnectionProvider}. Owning the instance is the only way to satisfy all
 * three connection-pool acceptance criteria with one wiring: direct {@code getPoolHealth()} metrics,
 * deterministic shutdown, and reuse of an externally supplied {@code DataSource} (Spring Boot).
 *
 * @since 4.0
 */
public final class HikariDataSourceFactory {

    private HikariDataSourceFactory() {
    }

    /**
     * Creates a configured {@link HikariDataSource} from the database and pool settings.
     *
     * @param database the JDBC connection identity (URL, driver, credentials)
     * @param pool     the pool tuning parameters
     * @return a started HikariCP data source
     */
    public static HikariDataSource create(DatabaseConfig database, PoolConfig pool) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("JEHibernate-" + database.type().name());
        hikari.setJdbcUrl(database.url());
        hikari.setDriverClassName(database.driver());

        if (database.username() != null) {
            hikari.setUsername(database.username());
        }
        if (database.password() != null) {
            hikari.setPassword(database.password());
        }

        hikari.setMaximumPoolSize(pool.maximumPoolSize());
        hikari.setMinimumIdle(pool.minimumIdle());
        hikari.setIdleTimeout(pool.idleTimeoutMillis());
        hikari.setConnectionTimeout(pool.connectionTimeoutMillis());
        hikari.setMaxLifetime(pool.maxLifetimeMillis());
        hikari.setLeakDetectionThreshold(pool.leakDetectionThresholdMillis());
        hikari.setValidationTimeout(pool.validationTimeoutMillis());

        return new HikariDataSource(hikari);
    }

    /**
     * Reads a live health snapshot from any {@link DataSource}.
     * <p>
     * Returns {@link PoolHealth#unavailable()} when the data source is not a HikariCP pool
     * or its MX bean is not yet registered.
     *
     * @param dataSource the data source to inspect (may be {@code null})
     * @return a health snapshot, never {@code null}
     */
    public static PoolHealth health(DataSource dataSource) {
        if (!(dataSource instanceof HikariDataSource hikari)) {
            return PoolHealth.unavailable();
        }
        HikariPoolMXBean mxBean = hikari.getHikariPoolMXBean();
        if (mxBean == null) {
            return PoolHealth.unavailable();
        }
        return new PoolHealth(
            mxBean.getActiveConnections(),
            mxBean.getIdleConnections(),
            mxBean.getTotalConnections(),
            mxBean.getThreadsAwaitingConnection(),
            true
        );
    }
}
