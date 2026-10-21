package de.jexcellence.testfixture;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.migration.MigrationConfig;
import de.jexcellence.jehibernate.migration.MigrationTool;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRUD and rollback through an externally supplied HikariCP {@code DataSource}, the path the
 * Spring Boot starter takes. Spring Boot leaves HikariCP at {@code autoCommit=true}; up to 4.2.0
 * JEHibernate still told Hibernate autocommit was already off, so PostgreSQL and MySQL/MariaDB
 * rejected every commit while each statement had already auto-committed.
 * <p>
 * Abstract → not executed on its own. Subclasses supply a Testcontainers database and are skipped
 * when Docker is unavailable.
 */
abstract class AbstractExternalDataSourceIT {

    private static final MigrationConfig MIGRATION_OFF =
        new MigrationConfig(false, MigrationTool.NONE, "classpath:db/migration");

    private HikariDataSource dataSource;
    private JEHibernate jeHibernate;

    protected abstract JdbcDatabaseContainer<?> container();

    protected abstract DatabaseType databaseType();

    @AfterEach
    void tearDown() {
        if (jeHibernate != null) {
            jeHibernate.close();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void crudWorksWithAutoCommitEnabled() {
        var repo = start(true).repositories().get(FixtureUserRepository.class);

        FixtureUser created = repo.create(new FixtureUser("alice"));
        assertThat(repo.findById(created.getId())).isPresent();
        assertThat(repo.count()).isEqualTo(1);

        repo.delete(created.getId());
        assertThat(repo.count()).isZero();
    }

    @Test
    void rolledBackTransactionLeavesNoRowWithAutoCommitEnabled() {
        JEHibernate je = start(true);
        var repo = je.repositories().get(FixtureUserRepository.class);

        try (EntityManager em = je.getEntityManagerFactory().createEntityManager()) {
            em.getTransaction().begin();
            em.persist(new FixtureUser("bob"));
            em.flush();
            em.getTransaction().rollback();
        }

        // Only a real transaction undoes the flushed insert; autocommit would have kept it.
        assertThat(repo.count()).isZero();
    }

    @Test
    void crudWorksWithAutoCommitDisabled() {
        var repo = start(false).repositories().get(FixtureUserRepository.class);

        FixtureUser created = repo.create(new FixtureUser("carol"));
        assertThat(repo.findById(created.getId())).isPresent();
    }

    private JEHibernate start(boolean autoCommit) {
        JdbcDatabaseContainer<?> db = container();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(db.getJdbcUrl());
        config.setUsername(db.getUsername());
        config.setPassword(db.getPassword());
        config.setAutoCommit(autoCommit);
        config.setMaximumPoolSize(4);
        dataSource = new HikariDataSource(config);

        jeHibernate = JEHibernate.builder()
            .configuration(c -> c
                .database(databaseType())
                .url(db.getJdbcUrl())
                .dataSource(dataSource)
                .ddlAuto("create-drop")
                .migration(MIGRATION_OFF))
            .scanPackages("de.jexcellence.testfixture")
            .build();
        return jeHibernate;
    }
}
