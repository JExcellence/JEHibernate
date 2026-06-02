package de.jexcellence.jehibernate.testing;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Boots a JEHibernate instance against a test database for the annotated test class.
 * <p>
 * The {@link JEHibernateExtension} starts the database ({@link TestDatabase#H2} in-memory, or a
 * Testcontainers container), builds the schema (Hibernate {@code ddl-auto} and/or Flyway), and
 * resolves {@code JEHibernate} / {@code EntityManagerFactory} test parameters. The configured
 * {@link DatabaseReset} runs after every test method.
 * <p>
 * <b>Example:</b>
 * <pre>{@code
 * @JEHibernateTest(database = TestDatabase.POSTGRES, migration = true, scanPackages = "com.example")
 * class OrderRepositoryIT {
 *     @Test
 *     void persistsOrder(JEHibernate je) {
 *         var repo = je.repositories().get(OrderRepository.class);
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * @since 4.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(JEHibernateExtension.class)
public @interface JEHibernateTest {

    /** The test database target. Default: in-memory H2 (no Docker). */
    TestDatabase database() default TestDatabase.H2;

    /** Reset strategy applied after each test method. Default: {@link DatabaseReset#TRUNCATE_ALL}. */
    DatabaseReset reset() default DatabaseReset.TRUNCATE_ALL;

    /** Packages scanned for entities and repositories. */
    String[] scanPackages() default {};

    /** Whether to run Flyway migrations at bootstrap (requires flyway-core on the classpath). */
    boolean migration() default false;

    /** Hibernate {@code hbm2ddl.auto} mode. Default {@code create-drop} for ephemeral test schemas. */
    String ddlAuto() default "create-drop";
}
