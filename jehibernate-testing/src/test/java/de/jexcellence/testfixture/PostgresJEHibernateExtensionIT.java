package de.jexcellence.testfixture;

import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.testing.JEHibernateTest;
import de.jexcellence.jehibernate.testing.TestDatabase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test against a real PostgreSQL via Testcontainers. Skipped (not failed) when Docker
 * is unavailable — see {@code JEHibernateExtension.beforeAll}. With Docker present, the first run
 * starts within ~15s of the image pull.
 */
@JEHibernateTest(database = TestDatabase.POSTGRES, scanPackages = "de.jexcellence.testfixture")
class PostgresJEHibernateExtensionIT {

    @Test
    void persistsAndCountsAgainstRealPostgres(JEHibernate jeHibernate) {
        var repo = jeHibernate.repositories().get(FixtureUserRepository.class);
        repo.create(new FixtureUser("postgres-user"));
        assertThat(repo.count()).isEqualTo(1L);
    }
}
