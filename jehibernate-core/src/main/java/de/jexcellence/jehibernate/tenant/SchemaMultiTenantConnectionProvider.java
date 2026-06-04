package de.jexcellence.jehibernate.tenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Schema-per-tenant {@link MultiTenantConnectionProvider} over a single shared {@link DataSource}.
 * <p>
 * Connections come from one HikariCP pool (no pool-per-tenant explosion). Before handing a
 * connection to a tenant, its active schema is switched via {@link Connection#setSchema(String)};
 * on release the schema is reset to {@code defaultSchema} so a pooled connection never leaks
 * another tenant's schema to the next borrower.
 * <p>
 * Schema switching uses the JDBC {@code setSchema} contract, which maps to {@code SET SCHEMA} on
 * H2/PostgreSQL. On MySQL/MariaDB "schema" equals "database" (catalog); schema-per-tenant there is
 * effectively database-per-tenant — prefer {@link MultiTenancyStrategy#DATABASE} for those.
 *
 * @since 4.0
 */
public final class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final transient DataSource dataSource;
    private final String defaultSchema;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource, String defaultSchema) {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource must not be null");
        }
        if (defaultSchema == null || defaultSchema.isBlank()) {
            throw new IllegalArgumentException("defaultSchema must not be null or blank");
        }
        this.dataSource = dataSource;
        this.defaultSchema = defaultSchema;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.setSchema(defaultSchema);
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            connection.setSchema(tenantIdentifier);
            return connection;
        } catch (SQLException | RuntimeException e) {
            // Close on the failure path so a failed schema switch never leaks the connection;
            // on success the open connection is returned (Hibernate releases it later).
            connection.close();
            throw e;
        }
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        // Reset before returning to the pool so the next borrower starts on the default schema.
        connection.setSchema(defaultSchema);
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return unwrapType != null
            && (unwrapType.isAssignableFrom(getClass()) || unwrapType.isAssignableFrom(DataSource.class));
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        if (unwrapType.isInstance(this)) {
            return unwrapType.cast(this);
        }
        if (unwrapType.isAssignableFrom(DataSource.class)) {
            return unwrapType.cast(dataSource);
        }
        throw new IllegalArgumentException("Cannot unwrap to " + unwrapType.getName());
    }
}
