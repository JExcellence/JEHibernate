package de.jexcellence.testfixture;

import de.jexcellence.jehibernate.core.JEHibernate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared CRUD + query scenario run against each supported database by the concrete subclasses
 * ({@code H2CrudIT}, {@code PostgresCrudIT}, {@code MySqlCrudIT}, {@code MariaDbCrudIT}).
 * <p>
 * Abstract → not executed on its own. Container-backed subclasses skip when Docker is unavailable;
 * the H2 subclass always runs.
 */
abstract class AbstractCrudAcrossDatabasesIT {

    @Test
    void fullCrudLifecycle(JEHibernate jeHibernate) {
        var repo = jeHibernate.repositories().get(FixtureUserRepository.class);

        assertThat(repo.count()).isZero();

        FixtureUser created = repo.create(new FixtureUser("alice"));
        assertThat(created.getId()).isNotNull();           // IDENTITY generation on this DB
        assertThat(repo.findById(created.getId())).isPresent();
        assertThat(repo.count()).isEqualTo(1);

        var byName = repo.query().and("name", "alice").first();
        assertThat(byName).isPresent();
        assertThat(byName.get().getName()).isEqualTo("alice");

        repo.delete(created.getId());
        assertThat(repo.findById(created.getId())).isEmpty();
    }

    @Test
    void batchInsertAndCount(JEHibernate jeHibernate) {
        var repo = jeHibernate.repositories().get(FixtureUserRepository.class);

        repo.createAll(List.of(new FixtureUser("a"), new FixtureUser("b"), new FixtureUser("c")));
        assertThat(repo.count()).isEqualTo(3);

        var matches = repo.query().like("name", "%").list();
        assertThat(matches).hasSize(3);
    }
}
