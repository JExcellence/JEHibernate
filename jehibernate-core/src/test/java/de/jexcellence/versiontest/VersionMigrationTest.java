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
 * Verifies that the {@code @Version} column is nullable so {@code hbm2ddl.auto=update} can add it to
 * an already-populated table (the primitive {@code int} → {@code NOT NULL} DDL would otherwise fail
 * the H2 table rebuild), while optimistic locking and fresh-schema creation keep working.
 * <p>
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
    void updateAddsNullableVersionColumnToPopulatedTable() {
        String url = "jdbc:h2:mem:versionmig;DB_CLOSE_DELAY=-1";

        // A pre-existing, populated table WITHOUT the version column (as an older schema would have).
        jdbc(url, s -> {
            try {
                s.execute("CREATE TABLE versioned_thing ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255))");
                s.execute("INSERT INTO versioned_thing (name) VALUES ('alice')");
                s.execute("INSERT INTO versioned_thing (name) VALUES ('bob')");
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });

        try (JEHibernate jeHibernate = build(url, "update")) {
            var repo = jeHibernate.repositories().get(VersionedThingRepository.class);

            // The ALTER succeeded (no silent DDL warning), the two existing rows survived, and they
            // read version 0 (the DB value is null; the null-safe getter returns 0).
            var all = repo.findAll();
            assertThat(all).hasSize(2);
            assertThat(all).allMatch(t -> t.getVersion() == 0);

            // The column exists and is nullable.
            assertThat(isNullable(url, "VERSIONED_THING", "VERSION")).isEqualTo("YES");
        }
    }

    @Test
    void freshSchemaCreatesNullableVersionStartingAtZero() {
        String url = "jdbc:h2:mem:versionfresh;DB_CLOSE_DELAY=-1";

        try (JEHibernate jeHibernate = build(url, "create-drop")) {
            var repo = jeHibernate.repositories().get(VersionedThingRepository.class);

            VersionedThing saved = repo.create(new VersionedThing("fresh"));
            assertThat(saved.getVersion()).isZero();                 // first persist starts at 0
            assertThat(isNullable(url, "VERSIONED_THING", "VERSION")).isEqualTo("YES");
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
    void stricterExistingNotNullVersionColumnStillWorks() {
        String url = "jdbc:h2:mem:versionstrict;DB_CLOSE_DELAY=-1";

        // A DB where the column already exists as NOT NULL DEFAULT 0 — Hibernate never writes null on
        // persist, so a stricter existing column is harmless.
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

        try (JEHibernate jeHibernate = build(url, "update")) {
            var repo = jeHibernate.repositories().get(VersionedThingRepository.class);
            assertThat(repo.count()).isEqualTo(1);

            VersionedThing created = repo.create(new VersionedThing("new"));
            assertThat(created.getVersion()).isZero();
            assertThat(repo.count()).isEqualTo(2);
        }
    }
}
