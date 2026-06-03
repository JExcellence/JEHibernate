package de.jexcellence.jehibernate.testing;

import de.jexcellence.jehibernate.config.DatabaseType;

/**
 * Test database target for {@link JEHibernateTest}. {@link #H2} runs in-memory (no Docker);
 * the others start a Testcontainers container.
 *
 * @since 4.0
 */
public enum TestDatabase {
    H2(DatabaseType.H2),
    POSTGRES(DatabaseType.POSTGRESQL),
    MYSQL(DatabaseType.MYSQL),
    MARIADB(DatabaseType.MARIADB),
    MSSQL(DatabaseType.MSSQL_SERVER);

    private final DatabaseType databaseType;

    TestDatabase(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    public DatabaseType databaseType() {
        return databaseType;
    }

    public boolean requiresContainer() {
        return this != H2;
    }
}
