package de.jexcellence.testfixture;

import de.jexcellence.jehibernate.config.DatabaseType;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** External-DataSource scenario against MySQL via Testcontainers (skipped without Docker). */
@Testcontainers(disabledWithoutDocker = true)
class MySqlExternalDataSourceIT extends AbstractExternalDataSourceIT {

    @Container
    private static final MySQLContainer<?> DATABASE = new MySQLContainer<>("mysql:8.4");

    @Override
    protected JdbcDatabaseContainer<?> container() {
        return DATABASE;
    }

    @Override
    protected DatabaseType databaseType() {
        return DatabaseType.MYSQL;
    }
}
