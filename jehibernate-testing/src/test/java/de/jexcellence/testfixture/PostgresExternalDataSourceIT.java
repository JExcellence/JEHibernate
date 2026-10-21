package de.jexcellence.testfixture;

import de.jexcellence.jehibernate.config.DatabaseType;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** External-DataSource scenario against PostgreSQL via Testcontainers (skipped without Docker). */
@Testcontainers(disabledWithoutDocker = true)
class PostgresExternalDataSourceIT extends AbstractExternalDataSourceIT {

    @Container
    private static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

    @Override
    protected JdbcDatabaseContainer<?> container() {
        return DATABASE;
    }

    @Override
    protected DatabaseType databaseType() {
        return DatabaseType.POSTGRESQL;
    }
}
