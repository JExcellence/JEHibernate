package de.jexcellence.jehibernate.migration;

import de.jexcellence.jehibernate.exception.JEHibernateException;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Opt-in {@link MigrationRunner} backed by Liquibase, for teams that prefer XML/YAML/JSON
 * changelogs. Activated via {@code jehibernate.migration.tool=liquibase}; the
 * {@link MigrationConfig#location()} is then the changelog path
 * (e.g. {@code db/changelog/db.changelog-master.xml}).
 * <p>
 * Imports {@code liquibase.*} directly and is therefore loaded only after {@link MigrationSupport}
 * confirms Liquibase is on the classpath.
 *
 * @since 4.0
 */
public final class LiquibaseMigrationRunner implements MigrationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiquibaseMigrationRunner.class);

    @Override
    public void migrate(DataSource dataSource, MigrationConfig config) {
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase =
                     new Liquibase(config.location(), new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
                LOGGER.info("Liquibase update complete from changelog {}", config.location());
            }
        } catch (Exception e) {
            throw new JEHibernateException("Liquibase migration failed for changelog " + config.location(), e);
        }
    }
}
