package de.jexcellence.jehibernate.migration;

import javax.sql.DataSource;

/**
 * Runs schema migrations against a {@link DataSource} before Hibernate builds its
 * {@code SessionFactory}. Implementations are tool-specific (Flyway, Liquibase) and are loaded
 * only after {@link MigrationSupport} has verified the tool is present on the classpath.
 *
 * @since 4.0
 */
public interface MigrationRunner {

    /**
     * Applies all pending migrations.
     *
     * @param dataSource the data source to migrate (the JEHibernate-owned or external pool)
     * @param config     the migration configuration (location, etc.)
     */
    void migrate(DataSource dataSource, MigrationConfig config);
}
