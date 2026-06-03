package de.jexcellence.jehibernate.tenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * Database-per-tenant {@link MultiTenantConnectionProvider}: each tenant maps to its own
 * {@link DataSource}. The {@code anyDataSource} (typically the JEHibernate-managed pool) backs
 * tenant-agnostic operations such as bootstrap metadata access.
 * <p>
 * Each tenant's {@code DataSource} owns its own pool; this strategy has the highest isolation and
 * the highest ops cost. For a shared single pool prefer {@link MultiTenancyStrategy#SCHEMA} or
 * {@link MultiTenancyStrategy#DISCRIMINATOR}.
 *
 * @since 4.0
 */
public final class DatabaseMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final transient Map<String, DataSource> tenantDataSources;
    private final transient DataSource anyDataSource;

    public DatabaseMultiTenantConnectionProvider(Map<String, DataSource> tenantDataSources, DataSource anyDataSource) {
        if (tenantDataSources == null || tenantDataSources.isEmpty()) {
            throw new IllegalArgumentException("tenantDataSources must not be null or empty");
        }
        if (anyDataSource == null) {
            throw new IllegalArgumentException("anyDataSource must not be null");
        }
        this.tenantDataSources = Map.copyOf(tenantDataSources);
        this.anyDataSource = anyDataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return anyDataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        DataSource dataSource = tenantDataSources.get(tenantIdentifier);
        if (dataSource == null) {
            throw new SQLException("No DataSource registered for tenant: " + tenantIdentifier);
        }
        return dataSource.getConnection();
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return unwrapType != null && unwrapType.isAssignableFrom(getClass());
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        if (unwrapType.isInstance(this)) {
            return unwrapType.cast(this);
        }
        throw new IllegalArgumentException("Cannot unwrap to " + unwrapType.getName());
    }
}
