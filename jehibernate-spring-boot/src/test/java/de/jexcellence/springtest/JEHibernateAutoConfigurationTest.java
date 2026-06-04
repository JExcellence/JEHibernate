package de.jexcellence.springtest;

import de.jexcellence.jehibernate.core.JEHibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the JEHibernate Spring Boot auto-configuration end to end: Spring Boot creates the
 * DataSource, JEHibernate is auto-wired from it, and a repository round-trips. Runs on H2 — no
 * Docker required.
 */
@SpringBootTest(
    classes = JEHibernateAutoConfigurationTest.TestApp.class,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:springbootac;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jehibernate.database=H2",
        "jehibernate.ddl-auto=create-drop",
        "jehibernate.scan-packages=de.jexcellence.springtest",
        "jehibernate.migration-enabled=false"
    }
)
class JEHibernateAutoConfigurationTest {

    @Autowired
    private JEHibernate jeHibernate;

    @Autowired
    private DataSource dataSource;

    @Test
    void autoConfiguresJEHibernateReusingSpringDataSource() {
        assertThat(jeHibernate).isNotNull();
        // JEHibernate reuses the application's DataSource rather than creating its own.
        assertThat(jeHibernate.getDataSource()).isSameAs(dataSource);

        var repo = jeHibernate.repositories().get(SpringUserRepository.class);
        repo.create(new SpringUser("alice"));

        assertThat(repo.count()).isEqualTo(1L);
        assertThat(repo.findAll()).extracting(SpringUser::getName).containsExactly("alice");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
    }
}
