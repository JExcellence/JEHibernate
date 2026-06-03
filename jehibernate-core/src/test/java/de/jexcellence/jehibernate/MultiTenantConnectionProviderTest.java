package de.jexcellence.jehibernate;

import de.jexcellence.jehibernate.tenant.DatabaseMultiTenantConnectionProvider;
import de.jexcellence.jehibernate.tenant.SchemaMultiTenantConnectionProvider;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the SCHEMA and DATABASE connection providers — deterministic, without a live
 * database, so the per-tenant connection routing and schema reset-on-release behaviour are pinned.
 */
class MultiTenantConnectionProviderTest {

    @Test
    void schemaProviderSwitchesSchemaPerTenantAndResetsOnRelease() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);

        var provider = new SchemaMultiTenantConnectionProvider(dataSource, "PUBLIC");

        Connection acme = provider.getConnection("acme");
        verify(connection).setSchema("acme");
        assertThat(acme).isSameAs(connection);

        provider.releaseConnection("acme", connection);
        verify(connection).setSchema("PUBLIC"); // reset before returning to the pool
        verify(connection).close();
    }

    @Test
    void schemaProviderResetsAnyConnectionOnRelease() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);

        var provider = new SchemaMultiTenantConnectionProvider(dataSource, "PUBLIC");
        provider.getAnyConnection();
        provider.releaseAnyConnection(connection);

        verify(connection).setSchema("PUBLIC");
        verify(connection).close();
    }

    @Test
    void schemaProviderUnwrapsToDataSource() {
        DataSource dataSource = mock(DataSource.class);
        var provider = new SchemaMultiTenantConnectionProvider(dataSource, "PUBLIC");

        assertThat(provider.isUnwrappableAs(DataSource.class)).isTrue();
        assertThat(provider.unwrap(DataSource.class)).isSameAs(dataSource);
    }

    @Test
    void databaseProviderRoutesToTenantDataSource() throws SQLException {
        DataSource acmeDs = mock(DataSource.class);
        DataSource anyDs = mock(DataSource.class);
        Connection acmeConn = mock(Connection.class);
        when(acmeDs.getConnection()).thenReturn(acmeConn);

        var provider = new DatabaseMultiTenantConnectionProvider(Map.of("acme", acmeDs), anyDs);

        assertThat(provider.getConnection("acme")).isSameAs(acmeConn);
        verify(acmeDs).getConnection();
    }

    @Test
    void databaseProviderThrowsForUnknownTenant() {
        DataSource acmeDs = mock(DataSource.class);
        DataSource anyDs = mock(DataSource.class);

        var provider = new DatabaseMultiTenantConnectionProvider(Map.of("acme", acmeDs), anyDs);

        assertThatThrownBy(() -> provider.getConnection("unknown"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("unknown");
    }
}
