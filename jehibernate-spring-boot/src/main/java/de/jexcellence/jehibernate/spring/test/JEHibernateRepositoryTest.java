package de.jexcellence.jehibernate.spring.test;

import de.jexcellence.jehibernate.config.DatabaseType;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.boot.test.autoconfigure.properties.PropertyMapping;
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test slice for JEHibernate repositories — the analog of Spring Boot's {@code @DataJpaTest}.
 * <p>
 * Boots a minimal Spring context with only the {@code DataSource} auto-configuration (an embedded
 * database when one is on the test classpath, e.g. H2) and the JEHibernate auto-configuration —
 * full application auto-configuration is disabled. Annotation attributes map to
 * {@code jehibernate.*} properties.
 * <p>
 * <b>Example:</b>
 * <pre>{@code
 * @JEHibernateRepositoryTest(scanPackages = "com.example.domain")
 * class UserRepositoryTest {
 *     @Autowired JEHibernate jeHibernate;
 *
 *     @Test
 *     void persists() {
 *         var repo = jeHibernate.repositories().get(UserRepository.class);
 *         repo.create(new User("alice"));
 *         assertThat(repo.count()).isEqualTo(1);
 *     }
 * }
 * }</pre>
 *
 * @since 4.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@BootstrapWith(SpringBootTestContextBootstrapper.class)
@ExtendWith(SpringExtension.class)
@OverrideAutoConfiguration(enabled = false)
@ImportAutoConfiguration
@PropertyMapping("jehibernate")
public @interface JEHibernateRepositoryTest {

    /** Database type — selects the Hibernate dialect. Defaults to H2. */
    @PropertyMapping("database")
    DatabaseType database() default DatabaseType.H2;

    /** Hibernate {@code hbm2ddl.auto} mode. Defaults to {@code create-drop} for ephemeral schemas. */
    @PropertyMapping("ddl-auto")
    String ddlAuto() default "create-drop";

    /** Packages scanned for entities and repositories. */
    @PropertyMapping("scan-packages")
    String[] scanPackages() default {};

    /** Whether to run Flyway migrations at bootstrap. Off by default for slices. */
    @PropertyMapping("migration-enabled")
    boolean migrationEnabled() default false;
}
