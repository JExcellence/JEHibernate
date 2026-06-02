package de.jexcellence.jehibernate;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.migration.MigrationConfig;
import de.jexcellence.jehibernate.migration.MigrationTool;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TODO-2 acceptance: Flyway runs the {@code V*.sql} migrations before Hibernate boots, is a
 * no-op on a second start, and can be disabled. Liquibase activation is verified at the
 * configuration level.
 * <p>
 * Queries the migrated schema directly via JDBC (ddl-auto=none, no mapped entity) to keep the
 * assertions about <em>migration behaviour</em> independent of Hibernate schema validation.
 */
class MigrationIntegrationTest {

    private static long scalarLong(EntityManagerFactory emf, String sql) {
        EntityManager em = emf.createEntityManager();
        try {
            Object value = em.createNativeQuery(sql).getSingleResult();
            return ((Number) value).longValue();
        } finally {
            em.close();
        }
    }

    private static String scalarString(EntityManagerFactory emf, String sql) {
        EntityManager em = emf.createEntityManager();
        try {
            return (String) em.createNativeQuery(sql).getSingleResult();
        } finally {
            em.close();
        }
    }

    @Test
    void appliesAllMigrationsOnEmptyDatabase() {
        try (JEHibernate jeHibernate = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:mem:migempty;DB_CLOSE_DELAY=-1")
                .credentials("sa", "")
                .ddlAuto("none"))
            .disableAutoScan()
            .build()) {

            EntityManagerFactory emf = jeHibernate.getEntityManagerFactory();
            assertThat(scalarLong(emf, "SELECT COUNT(*) FROM app_setting")).isEqualTo(1L);
            assertThat(scalarString(emf, "SELECT setting_value FROM app_setting WHERE id = 1"))
                .isEqualTo("JEHibernate Demo");
        }
    }

    @Test
    void secondStartIsNoOp(@TempDir Path tempDir) {
        String url = "jdbc:h2:" + tempDir.resolve("migpersist").toAbsolutePath();

        // First start: Flyway applies V001 to the empty file database.
        try (JEHibernate first = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url(url)
                .credentials("sa", "")
                .ddlAuto("none"))
            .disableAutoScan()
            .build()) {

            assertThat(scalarLong(first.getEntityManagerFactory(), "SELECT COUNT(*) FROM app_setting"))
                .isEqualTo(1L);
        }

        // Second start on the same database: Flyway recognises the version and no-ops.
        try (JEHibernate second = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url(url)
                .credentials("sa", "")
                .ddlAuto("none"))
            .disableAutoScan()
            .build()) {

            EntityManagerFactory emf = second.getEntityManagerFactory();
            // No duplicate insert (would violate the PK if V001 re-ran) → still exactly one row.
            assertThat(scalarLong(emf, "SELECT COUNT(*) FROM app_setting")).isEqualTo(1L);
            // Exactly one successfully applied versioned migration recorded.
            assertThat(scalarLong(emf,
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\" = TRUE AND \"version\" = '001'"))
                .isEqualTo(1L);
        }
    }

    @Test
    void disabledMigrationSkipsSchemaCreation() {
        try (JEHibernate jeHibernate = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:mem:migdisabled;DB_CLOSE_DELAY=-1")
                .credentials("sa", "")
                .ddlAuto("none")
                .migration(new MigrationConfig(false, MigrationTool.FLYWAY, "classpath:db/migration")))
            .disableAutoScan()
            .build()) {

            EntityManagerFactory emf = jeHibernate.getEntityManagerFactory();
            // Migration disabled and ddl-auto=none → the table was never created.
            assertThatThrownBy(() -> scalarLong(emf, "SELECT COUNT(*) FROM app_setting"))
                .isInstanceOf(Exception.class);
        }
    }

    @Test
    void liquibaseActivationIsConfigurable() {
        Properties props = new Properties();
        props.setProperty("jehibernate.migration.tool", "liquibase");
        props.setProperty("jehibernate.migration.location", "db/changelog/db.changelog-master.xml");

        MigrationConfig config = MigrationConfig.fromProperties(props, MigrationConfig.defaults());

        assertThat(config.tool()).isEqualTo(MigrationTool.LIQUIBASE);
        assertThat(config.location()).isEqualTo("db/changelog/db.changelog-master.xml");
        assertThat(config.enabled()).isTrue();
    }
}
