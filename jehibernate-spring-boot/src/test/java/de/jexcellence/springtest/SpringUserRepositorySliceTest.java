package de.jexcellence.springtest;

import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.spring.test.JEHibernateRepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@link JEHibernateRepositoryTest} slice: a minimal context with an embedded H2
 * DataSource and the JEHibernate auto-configuration, no application auto-configuration. Runs
 * without Docker and without any {@code spring.datasource.*} properties (embedded DB).
 */
@JEHibernateRepositoryTest(scanPackages = "de.jexcellence.springtest")
class SpringUserRepositorySliceTest {

    @Autowired
    private JEHibernate jeHibernate;

    @Test
    void sliceBootsRepositoryAndRoundTrips() {
        var repo = jeHibernate.repositories().get(SpringUserRepository.class);
        repo.create(new SpringUser("bob"));

        assertThat(repo.count()).isEqualTo(1L);
        assertThat(repo.findAll()).extracting(SpringUser::getName).containsExactly("bob");
    }
}
