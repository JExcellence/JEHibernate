package de.jexcellence.jehibernate.migration;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * {@link MigrationRunner} backed by Flyway (the JEHibernate default).
 * <p>
 * This class imports {@code org.flywaydb.*} directly. It is therefore loaded only from inside
 * {@link MigrationSupport}, after a {@code Class.forName} guard has confirmed Flyway is on the
 * classpath — keeping Flyway a truly optional dependency for the plugin use-case.
 * <p>
 * {@code baselineOnMigrate} is enabled so an existing, non-empty database (a plugin upgrading
 * from DDL-managed schemas) is baselined instead of rejected.
 *
 * @since 4.0
 */
public final class FlywayMigrationRunner implements MigrationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlywayMigrationRunner.class);

    @Override
    public void migrate(DataSource dataSource, MigrationConfig config) {
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations(config.location())
            .baselineOnMigrate(true)
            .load();

        int applied = flyway.migrate().migrationsExecuted;
        LOGGER.info("Flyway migration complete: {} migration(s) applied from {}",
            applied, config.location());
    }
}
