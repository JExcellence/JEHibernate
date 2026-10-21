package de.jexcellence.jehibernate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.migration.MigrationConfig;
import de.jexcellence.jehibernate.migration.MigrationTool;
import org.h2.jdbcx.JdbcDataSource;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code hibernate.connection.provider_disables_autocommit} may only be promised to Hibernate when
 * connections really arrive with autocommit off. The owned pool guarantees that; an external
 * DataSource (e.g. Spring Boot's HikariCP, autoCommit=true by default) is inspected instead.
 * A false promise makes PostgreSQL and MySQL/MariaDB reject every commit — see the
 * {@code External*CrudIT} suites in jehibernate-testing for the real-database check.
 */
class AutoCommitContractTest {

    private static final MigrationConfig MIGRATION_OFF =
        new MigrationConfig(false, MigrationTool.NONE, "classpath:db/migration");

    @Test
    void ownedPoolPromisesDisabledAutoCommit() {
        try (JEHibernate je = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:mem:autocommit-owned;DB_CLOSE_DELAY=-1")
                .credentials("sa", "")
                .ddlAuto("none")
                .migration(MIGRATION_OFF))
            .disableAutoScan()
            .build()) {

            assertThat(providerDisablesAutoCommit(je)).isTrue();
        }
    }

    @Test
    void externalHikariWithAutoCommitOnIsNotPromised() {
        try (HikariDataSource external = hikari("autocommit-ext-on", true);
             JEHibernate je = withExternal(external)) {

            assertThat(providerDisablesAutoCommit(je)).isFalse();
        }
    }

    @Test
    void externalHikariWithAutoCommitOffIsPromised() {
        try (HikariDataSource external = hikari("autocommit-ext-off", false);
             JEHibernate je = withExternal(external)) {

            assertThat(providerDisablesAutoCommit(je)).isTrue();
        }
    }

    @Test
    void plainExternalDataSourceIsProbedThroughAConnection() {
        JdbcDataSource external = new JdbcDataSource();
        external.setURL("jdbc:h2:mem:autocommit-plain;DB_CLOSE_DELAY=-1");
        external.setUser("sa");

        // JDBC connections start with autocommit on, so nothing may be promised.
        try (JEHibernate je = withExternal(external)) {
            assertThat(providerDisablesAutoCommit(je)).isFalse();
        }
    }

    @Test
    void explicitSettingStillWins() {
        try (HikariDataSource external = hikari("autocommit-explicit", true);
             JEHibernate je = JEHibernate.builder()
                 .configuration(config -> config
                     .database(DatabaseType.H2)
                     .url("jdbc:h2:mem:autocommit-explicit")
                     .dataSource(external)
                     .ddlAuto("none")
                     .migration(MIGRATION_OFF)
                     .property(AvailableSettings.CONNECTION_PROVIDER_DISABLES_AUTOCOMMIT, true))
                 .disableAutoScan()
                 .build()) {

            assertThat(providerDisablesAutoCommit(je)).isTrue();
        }
    }

    private static JEHibernate withExternal(DataSource external) {
        return JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:mem:external-placeholder")
                .dataSource(external)
                .ddlAuto("none")
                .migration(MIGRATION_OFF))
            .disableAutoScan()
            .build();
    }

    private static HikariDataSource hikari(String name, boolean autoCommit) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setAutoCommit(autoCommit);
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }

    private static boolean providerDisablesAutoCommit(JEHibernate je) {
        Object value = je.getEntityManagerFactory().getProperties()
            .get(AvailableSettings.CONNECTION_PROVIDER_DISABLES_AUTOCOMMIT);
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
