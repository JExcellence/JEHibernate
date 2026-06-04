package de.jexcellence.jehibernate.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Bootstrap entry point for schema migrations.
 * <p>
 * Invoked from {@code ConfigurationBuilder.build()} after the {@link DataSource} is resolved and
 * <b>before</b> Hibernate builds its {@code SessionFactory}, so migrations own the schema and
 * {@code ddl-auto=validate} can verify it.
 * <p>
 * <b>Optional by design:</b> the chosen tool's classes are referenced only after a
 * {@code Class.forName} guard confirms they are on the classpath. When the tool is absent the
 * step is a silent no-op (logged at INFO) — never an error. This keeps Flyway/Liquibase optional
 * dependencies for the plugin use-case.
 *
 * @since 4.0
 */
public final class MigrationSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationSupport.class);

    private static final String FLYWAY_MARKER = "org.flywaydb.core.Flyway";
    private static final String LIQUIBASE_MARKER = "liquibase.Liquibase";

    private MigrationSupport() {
    }

    /**
     * Runs migrations if enabled and the selected tool is available; otherwise a no-op.
     *
     * @param dataSource the data source to migrate
     * @param config     the migration configuration
     */
    public static void run(DataSource dataSource, MigrationConfig config) {
        if (!config.enabled()) {
            LOGGER.info("Migration disabled (jehibernate.migration.enabled=false) — skipping");
            return;
        }

        switch (config.tool()) {
            case FLYWAY -> runFlyway(dataSource, config);
            case LIQUIBASE -> runLiquibase(dataSource, config);
            case NONE -> LOGGER.info("Migration tool set to NONE — skipping");
            default -> throw new IllegalStateException("Unhandled migration tool: " + config.tool());
        }
    }

    private static void runFlyway(DataSource dataSource, MigrationConfig config) {
        if (!isPresent(FLYWAY_MARKER)) {
            LOGGER.info("Flyway not on classpath — skipping migration (silent no-op)");
            return;
        }
        // FlywayMigrationRunner is linked only here, after the guard above.
        new FlywayMigrationRunner().migrate(dataSource, config);
    }

    private static void runLiquibase(DataSource dataSource, MigrationConfig config) {
        if (!isPresent(LIQUIBASE_MARKER)) {
            LOGGER.info("Liquibase not on classpath — skipping migration (silent no-op)");
            return;
        }
        new LiquibaseMigrationRunner().migrate(dataSource, config);
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className, false, MigrationSupport.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
