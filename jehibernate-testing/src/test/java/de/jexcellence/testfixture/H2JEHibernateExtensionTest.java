package de.jexcellence.testfixture;

import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.testing.Fixtures;
import de.jexcellence.jehibernate.testing.JEHibernateTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runnable without Docker: proves the {@link JEHibernateTest} extension boots H2, injects
 * JEHibernate, and that the default TRUNCATE_ALL reset leaves each test a clean database
 * (every test asserts a zero starting count regardless of execution order).
 */
@JEHibernateTest(scanPackages = "de.jexcellence.testfixture")
class H2JEHibernateExtensionTest {

    @Test
    void startsCleanThenPersistsTwo(JEHibernate jeHibernate) {
        var repo = jeHibernate.repositories().get(FixtureUserRepository.class);
        assertThat(repo.count()).isZero();
        repo.create(new FixtureUser("alice"));
        repo.create(new FixtureUser("bob"));
        assertThat(repo.count()).isEqualTo(2);
    }

    @Test
    void startsCleanAgainAfterReset(JEHibernate jeHibernate) {
        var repo = jeHibernate.repositories().get(FixtureUserRepository.class);
        assertThat(repo.count()).isZero(); // reset cleared whatever the other test wrote
        repo.create(new FixtureUser("carol"));
        assertThat(repo.count()).isEqualTo(1);
    }

    @Test
    void fixturesBuilderPersists(JEHibernate jeHibernate) {
        Fixtures.persist(jeHibernate.getEntityManagerFactory(), new FixtureUser("dave"));
        var repo = jeHibernate.repositories().get(FixtureUserRepository.class);
        assertThat(repo.count()).isEqualTo(1);
    }
}
