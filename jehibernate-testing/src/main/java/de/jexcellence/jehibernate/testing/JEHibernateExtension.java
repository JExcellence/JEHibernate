package de.jexcellence.jehibernate.testing;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.migration.MigrationConfig;
import de.jexcellence.jehibernate.migration.MigrationTool;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * JUnit 5 extension behind {@link JEHibernateTest}. Manages the database lifecycle (H2 in-memory or
 * a Testcontainers container), builds the JEHibernate instance, resets the database after each test,
 * and resolves {@link JEHibernate} / {@link EntityManagerFactory} test parameters.
 * <p>
 * Container-backed tests are skipped (not failed) when Docker is unavailable, so an H2-only
 * environment keeps a green build.
 *
 * @since 4.0
 */
public final class JEHibernateExtension
    implements BeforeAllCallback, AfterAllCallback, AfterEachCallback, ParameterResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(JEHibernateExtension.class);
    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(JEHibernateExtension.class);
    private static final String KEY_JE = "jehibernate";
    private static final String KEY_CONTAINER = "container";
    private static final String KEY_RESET = "reset";

    @Override
    public void beforeAll(ExtensionContext context) {
        JEHibernateTest annotation = findAnnotation(context);
        TestDatabase database = annotation.database();

        if (database.requiresContainer() && !DockerClientFactory.instance().isDockerAvailable()) {
            throw new TestAbortedException(
                "Docker is not available — skipping " + database + " test " + context.getDisplayName());
        }

        ExtensionContext.Store store = context.getStore(NAMESPACE);
        JdbcDatabaseContainer<?> container = null;
        String url;
        String username;
        String password;

        if (database.requiresContainer()) {
            container = createContainer(database);
            container.start();
            store.put(KEY_CONTAINER, container);
            url = container.getJdbcUrl();
            username = container.getUsername();
            password = container.getPassword();
        } else {
            url = "jdbc:h2:mem:jehtest_" + context.getUniqueId().hashCode() + ";DB_CLOSE_DELAY=-1";
            username = "sa";
            password = "";
        }

        JEHibernate jeHibernate = buildJEHibernate(annotation, database.databaseType(), url, username, password);
        store.put(KEY_JE, jeHibernate);
        store.put(KEY_RESET, annotation.reset());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        JEHibernate jeHibernate = store.get(KEY_JE, JEHibernate.class);
        DatabaseReset reset = store.get(KEY_RESET, DatabaseReset.class);
        if (jeHibernate == null || reset == null) {
            return;
        }
        applyReset(jeHibernate.getEntityManagerFactory(), reset);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        JEHibernate jeHibernate = store.get(KEY_JE, JEHibernate.class);
        if (jeHibernate != null) {
            jeHibernate.close();
        }
        JdbcDatabaseContainer<?> container = store.get(KEY_CONTAINER, JdbcDatabaseContainer.class);
        if (container != null) {
            container.stop();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        return type == JEHibernate.class || type == EntityManagerFactory.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        JEHibernate jeHibernate = extensionContext.getStore(NAMESPACE).get(KEY_JE, JEHibernate.class);
        Class<?> type = parameterContext.getParameter().getType();
        if (type == JEHibernate.class) {
            return jeHibernate;
        }
        return jeHibernate.getEntityManagerFactory();
    }

    private JEHibernate buildJEHibernate(
        JEHibernateTest annotation, DatabaseType type, String url, String username, String password) {
        MigrationConfig migration = annotation.migration()
            ? MigrationConfig.defaults()
            : new MigrationConfig(false, MigrationTool.NONE, "classpath:db/migration");

        var builder = JEHibernate.builder()
            .configuration(config -> config
                .database(type)
                .url(url)
                .credentials(username, password)
                .ddlAuto(annotation.ddlAuto())
                .migration(migration));

        if (annotation.scanPackages().length > 0) {
            builder.scanPackages(annotation.scanPackages());
        } else {
            builder.disableAutoScan();
        }
        return builder.build();
    }

    private void applyReset(EntityManagerFactory emf, DatabaseReset reset) {
        SessionFactory sessionFactory = emf.unwrap(SessionFactory.class);
        switch (reset) {
            case NONE -> {
                // Intentionally no reset between tests.
            }
            case TRUNCATE_ALL -> sessionFactory.getSchemaManager().truncateMappedObjects();
            case DROP_CREATE -> {
                sessionFactory.getSchemaManager().dropMappedObjects(true);
                sessionFactory.getSchemaManager().exportMappedObjects(true);
            }
            case ROLLBACK_TX -> {
                LOGGER.warn("ROLLBACK_TX is not supported with per-operation repositories; "
                    + "falling back to TRUNCATE_ALL");
                sessionFactory.getSchemaManager().truncateMappedObjects();
            }
            default -> throw new IllegalStateException("Unhandled reset strategy: " + reset);
        }
    }

    private JdbcDatabaseContainer<?> createContainer(TestDatabase database) {
        return switch (database) {
            case POSTGRES -> new PostgreSQLContainer<>("postgres:16-alpine");
            case MYSQL -> new MySQLContainer<>("mysql:8.4");
            case MARIADB -> new MariaDBContainer<>("mariadb:11.4");
            case MSSQL -> new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();
            case H2 -> throw new IllegalStateException("H2 does not use a container");
            default -> throw new IllegalStateException("Unhandled database: " + database);
        };
    }

    private JEHibernateTest findAnnotation(ExtensionContext context) {
        return context.getRequiredTestClass().getAnnotation(JEHibernateTest.class);
    }
}
