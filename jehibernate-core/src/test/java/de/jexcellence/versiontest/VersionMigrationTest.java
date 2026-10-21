package de.jexcellence.versiontest;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.migration.MigrationConfig;
import de.jexcellence.jehibernate.migration.MigrationTool;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the optimistic-locking {@code @Version} column behavior on Hibernate 7.2+, where
 * {@code @Version} columns are {@code NOT NULL} unconditionally.
 * <p>
 * Two supported shapes:
 * <ul>
 *   <li><b>Fresh schema</b> — the column is created {@code NOT NULL DEFAULT 0} and versions start at 0.</li>
 *   <li><b>Existing populated table</b> — the column is added by a <em>migration</em>
 *       ({@code ALTER TABLE … ADD COLUMN version INTEGER NOT NULL DEFAULT 0}), not by
 *       {@code hbm2ddl.auto=update}. Hibernate 7.2+ cannot add a {@code NOT NULL} column to a populated
 *       table via {@code update} (the statement fails for existing rows and aborts the whole schema
 *       update), so schema evolution for this column belongs to Flyway/Liquibase. This test simulates
 *       the migrated table and boots with {@code ddl-auto=validate}.</li>
 * </ul>
 * Isolated package so {@code VersionedThing} does not leak into other suites' package scans.
 */
class VersionMigrationTest {

    private static final MigrationConfig MIGRATION_OFF =
        new MigrationConfig(false, MigrationTool.NONE, "classpath:db/migration");

    private static JEHibernate build(String url, String ddlAuto) {
        return JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url(url)
                .credentials("sa", "")
                .ddlAuto(ddlAuto)
                .migration(MIGRATION_OFF))
            .scanPackages("de.jexcellence.versiontest")
            .build();
    }

    private static void jdbc(String url, Consumer<Statement> work) {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            work.accept(statement);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String isNullable(String url, String table, String column) {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                 "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='"
                     + table + "' AND COLUMN_NAME='" + column + "'")) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void freshSchemaCreatesVersionNotNullStartingAtZero() {
        String url = "jdbc:h2:mem:versionfresh;DB_CLOSE_DELAY=-1";

        try (JEHibernate jeHibernate = build(url, "create-drop")) {
            var repo = jeHibernate.repositories().get(VersionedThingRepository.class);

            VersionedThing saved = repo.create(new VersionedThing("fresh"));
            assertThat(saved.getVersion()).isZero();                 // first persist starts at 0

            // Hibernate 7.2+ maps @Version as NOT NULL; @ColumnDefault("0") supplies the DDL default.
            assertThat(isNullable(url, "VERSIONED_THING", "VERSION")).isEqualTo("NO");
        }
    }

    @Test
    void optimisticLockingStillWorks() {
        String url = "jdbc:h2:mem:versionlock;DB_CLOSE_DELAY=-1";

        try (JEHibernate jeHibernate = build(url, "create-drop")) {
            var repo = jeHibernate.repositories().get(VersionedThingRepository.class);
            Long id = repo.create(new VersionedThing("x")).getId();

            EntityManagerFactory emf = jeHibernate.getEntityManagerFactory();
            EntityManager em1 = emf.createEntityManager();
            EntityManager em2 = emf.createEntityManager();
            try {
                VersionedThing v1 = em1.find(VersionedThing.class, id);
                VersionedThing v2 = em2.find(VersionedThing.class, id);

                em1.getTransaction().begin();
                v1.setName("one");
                em1.getTransaction().commit();                       // version 0 -> 1

                em2.getTransaction().begin();
                v2.setName("two");                                   // still holds version 0
                // The stale update matches 0 rows via "where id=? and version=?" and fails. Hibernate
                // may surface this as OptimisticLockException directly or wrapped in RollbackException.
                assertThatThrownBy(() -> em2.getTransaction().commit())
                    .isInstanceOfAny(OptimisticLockException.class, RollbackException.class);
            } finally {
                em1.close();
                em2.close();
            }

            assertThat(repo.findById(id)).get().extracting(VersionedThing::getVersion).isEqualTo(1);
        }
    }

    @Test
    void migratedPopulatedTableValidatesAndOperates() {
        String url = "jdbc:h2:mem:versionmigrated;DB_CLOSE_DELAY=-1";

        // The state AFTER a migration has run against a previously version-less, populated table:
        // version was added NOT NULL DEFAULT 0 (existing rows backfilled to 0), alongside the
        // timestamp columns BaseEntity maps. This is the supported replacement for hbm2ddl=update,
        // which cannot add a NOT NULL column to a populated table on Hibernate 7.2+.
        jdbc(url, s -> {
            try {
                s.execute("CREATE TABLE versioned_thing ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), "
                    + "version INTEGER NOT NULL DEFAULT 0, created_at TIMESTAMP, updated_at TIMESTAMP)");
                s.execute("INSERT INTO versioned_thing (name) VALUES ('legacy')");
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });

        // validate (not update): the migrated schema must already match the mapping exactly.
        try (JEHibernate jeHibernate = build(url, "validate")) {
            var repo = jeHibernate.repositories().get(VersionedThingRepository.class);

            assertThat(repo.count()).isEqualTo(1);
            assertThat(repo.findAll()).allMatch(t -> t.getVersion() == 0);   // backfilled rows read 0

            VersionedThing created = repo.create(new VersionedThing("new"));
            assertThat(created.getVersion()).isZero();
            assertThat(repo.count()).isEqualTo(2);
        }
    }
}
