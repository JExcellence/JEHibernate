package de.jexcellence.testfixture;

import de.jexcellence.jehibernate.config.DatabaseType;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** External-DataSource scenario against MariaDB via Testcontainers (skipped without Docker). */
@Testcontainers(disabledWithoutDocker = true)
class MariaDbExternalDataSourceIT extends AbstractExternalDataSourceIT {

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.4");

    @Override
    protected JdbcDatabaseContainer<?> container() {
        return DATABASE;
    }

    @Override
    protected DatabaseType databaseType() {
        return DatabaseType.MARIADB;
    }
}
