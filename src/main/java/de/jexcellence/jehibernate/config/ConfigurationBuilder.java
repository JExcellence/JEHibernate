package de.jexcellence.jehibernate.config;

import de.jexcellence.jehibernate.exception.ConfigurationException;
import de.jexcellence.jehibernate.naming.NamingStrategy;
import de.jexcellence.jehibernate.naming.SnakeCaseStrategy;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.SharedCacheMode;
import jakarta.persistence.ValidationMode;
import jakarta.persistence.spi.ClassTransformer;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.PersistenceUnitTransactionType;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.jpa.HibernatePersistenceProvider;

import javax.sql.DataSource;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Fluent builder for configuring and creating JPA {@link EntityManagerFactory} instances.
 * <p>
 * Provides sensible defaults for batch processing, connection management, and performance
 * while allowing full customization of Hibernate properties.
 * <p>
 * <b>Basic Example:</b>
 * <pre>{@code
 * var emf = ConfigurationBuilder.create()
 *     .database(DatabaseType.POSTGRESQL)
 *     .url("jdbc:postgresql://localhost:5432/mydb")
 *     .credentials("user", "pass")
 *     .ddlAuto("validate")
 *     .batchSize(50)
 *     .build();
 * }</pre>
 * <p>
 * <b>With Connection Pooling:</b>
 * <pre>{@code
 * var emf = ConfigurationBuilder.create()
 *     .database(DatabaseType.MYSQL)
 *     .url("jdbc:mysql://localhost:3306/mydb")
 *     .credentials("root", "secret")
 *     .connectionPool(5, 20)  // min 5, max 20 connections
 *     .build();
 * }</pre>
 *
 * @since 1.0
 * @see DatabaseType
 * @see DatabaseConfig
 */
public final class ConfigurationBuilder {

    private DatabaseConfig databaseConfig;
    private final Map<String, Object> properties = new HashMap<>();
    private NamingStrategy namingStrategy = new SnakeCaseStrategy();
    private final Set<Class<?>> entityClasses = new HashSet<>();

    private ConfigurationBuilder() {
    }

    public static ConfigurationBuilder create() {
        return new ConfigurationBuilder();
    }

    public ConfigurationBuilder database(DatabaseType type) {
        if (databaseConfig == null) {
            databaseConfig = DatabaseConfig.builder()
                .type(type)
                .url("jdbc:h2:mem:default")
                .build();
        } else {
            databaseConfig = DatabaseConfig.builder()
                .type(type)
                .url(databaseConfig.url())
                .username(databaseConfig.username())
                .password(databaseConfig.password())
                .driver(databaseConfig.driver())
                .dialect(databaseConfig.dialect())
                .build();
        }
        return this;
    }

    public ConfigurationBuilder url(String url) {
        if (databaseConfig == null) {
            throw new ConfigurationException("Database type must be set before URL");
        }
        databaseConfig = DatabaseConfig.builder()
            .type(databaseConfig.type())
            .url(url)
            .username(databaseConfig.username())
            .password(databaseConfig.password())
            .driver(databaseConfig.driver())
            .dialect(databaseConfig.dialect())
            .build();
        return this;
    }

    public ConfigurationBuilder credentials(String username, String password) {
        if (databaseConfig == null) {
            throw new ConfigurationException("Database type must be set before credentials");
        }
        databaseConfig = DatabaseConfig.builder()
            .type(databaseConfig.type())
            .url(databaseConfig.url())
            .username(username)
            .password(password)
            .driver(databaseConfig.driver())
            .dialect(databaseConfig.dialect())
            .build();
        return this;
    }

    public ConfigurationBuilder property(String key, Object value) {
        properties.put(key, value);
        return this;
    }

    public ConfigurationBuilder namingStrategy(NamingStrategy strategy) {
        this.namingStrategy = strategy;
        return this;
    }

    public ConfigurationBuilder registerEntity(Class<?> entityClass) {
        entityClasses.add(entityClass);
        return this;
    }

    public ConfigurationBuilder registerEntities(Set<Class<?>> entities) {
        entityClasses.addAll(entities);
        return this;
    }

    public ConfigurationBuilder showSql(boolean show) {
        return property(AvailableSettings.SHOW_SQL, show);
    }

    public ConfigurationBuilder formatSql(boolean format) {
        return property(AvailableSettings.FORMAT_SQL, format);
    }

    public ConfigurationBuilder ddlAuto(String mode) {
        return property(AvailableSettings.HBM2DDL_AUTO, mode);
    }

    public ConfigurationBuilder batchSize(int size) {
        property(AvailableSettings.STATEMENT_BATCH_SIZE, size);
        property(AvailableSettings.ORDER_INSERTS, true);
        property(AvailableSettings.ORDER_UPDATES, true);
        return this;
    }

    /**
     * Configures Hibernate's built-in connection pooling (Agroal).
     * Requires {@code hibernate-agroal} and {@code agroal-pool} on the classpath.
     * <p>
     * If these dependencies are not present, Hibernate falls back to its internal pool
     * with max-size configuration only.
     *
     * @param minSize minimum number of connections in the pool
     * @param maxSize maximum number of connections in the pool
     * @return this builder for chaining
     */
    public ConfigurationBuilder connectionPool(int minSize, int maxSize) {
        property("hibernate.agroal.minSize", minSize);
        property("hibernate.agroal.maxSize", maxSize);
        property("hibernate.agroal.acquisitionTimeout", "PT5S");
        property("hibernate.agroal.validationTimeout", "PT2S");
        return this;
    }

    /**
     * Enables Hibernate second-level cache with JCache provider.
     * Requires {@code hibernate-jcache} on the classpath.
     *
     * @return this builder for chaining
     */
    public ConfigurationBuilder enableSecondLevelCache() {
        property(AvailableSettings.USE_SECOND_LEVEL_CACHE, true);
        property(AvailableSettings.USE_QUERY_CACHE, true);
        property("hibernate.cache.region.factory_class", "org.hibernate.cache.jcache.JCacheRegionFactory");
        return this;
    }

    /**
     * Configures from a properties file. Reads database connection settings
     * using the prefix for the selected database type, and passes through all
     * {@code hibernate.*} properties directly to Hibernate.
     * <p>
     * <b>Recognized properties:</b>
     * <ul>
     *   <li>{@code database.type} — required, e.g., H2, MYSQL, POSTGRESQL</li>
     *   <li>{@code {prefix}.url} — JDBC URL (required)</li>
     *   <li>{@code {prefix}.username} — database username</li>
     *   <li>{@code {prefix}.password} — database password</li>
     *   <li>{@code {prefix}.driver} — JDBC driver override (optional, auto-detected from type)</li>
     *   <li>{@code {prefix}.dialect} — Hibernate dialect override (optional, auto-detected from type)</li>
     *   <li>{@code hibernate.*} — passed through directly to Hibernate</li>
     * </ul>
     *
     * @param props the properties to load
     * @return this builder for chaining
     */
    public ConfigurationBuilder fromProperties(Properties props) {
        String dbTypeStr = props.getProperty("database.type");
        if (dbTypeStr != null) {
            DatabaseType type = DatabaseType.valueOf(dbTypeStr.toUpperCase());
            String prefix = type.getPrefix();

            String url = props.getProperty(prefix + ".url");
            String username = props.getProperty(prefix + ".username");
            String password = props.getProperty(prefix + ".password");
            String driver = props.getProperty(prefix + ".driver");
            String dialect = props.getProperty(prefix + ".dialect");

            var builder = DatabaseConfig.builder()
                .type(type)
                .url(url != null ? url : "");

            if (username != null) builder.username(username);
            if (password != null) builder.password(password);
            if (driver != null) builder.driver(driver);
            if (dialect != null) builder.dialect(dialect);

            databaseConfig = builder.build();
        }

        props.forEach((key, value) -> {
            if (key.toString().startsWith("hibernate.")) {
                properties.put(key.toString(), value);
            }
        });

        return this;
    }

    public EntityManagerFactory build() {
        validate();
        Map<String, Object> config = buildConfiguration();
        PersistenceUnitInfo persistenceUnit = createPersistenceUnit();
        return new HibernatePersistenceProvider()
            .createContainerEntityManagerFactory(persistenceUnit, config);
    }

    private void validate() {
        if (databaseConfig == null) {
            throw new ConfigurationException("Database configuration is required");
        }
    }

    private Map<String, Object> buildConfiguration() {
        Map<String, Object> config = new HashMap<>(properties);

        config.put(AvailableSettings.JAKARTA_JDBC_URL, databaseConfig.url());
        config.put(AvailableSettings.JAKARTA_JDBC_DRIVER, databaseConfig.driver());
        config.put(AvailableSettings.DIALECT, databaseConfig.dialect());

        if (databaseConfig.username() != null) {
            config.put(AvailableSettings.JAKARTA_JDBC_USER, databaseConfig.username());
        }
        if (databaseConfig.password() != null) {
            config.put(AvailableSettings.JAKARTA_JDBC_PASSWORD, databaseConfig.password());
        }

        config.put(AvailableSettings.PHYSICAL_NAMING_STRATEGY, namingStrategy.getClass().getName());

        // Sensible defaults — user-provided values take precedence
        config.putIfAbsent(AvailableSettings.HBM2DDL_AUTO, "update");
        config.putIfAbsent(AvailableSettings.SHOW_SQL, false);
        config.putIfAbsent(AvailableSettings.STATEMENT_BATCH_SIZE, 25);
        config.putIfAbsent(AvailableSettings.ORDER_INSERTS, true);
        config.putIfAbsent(AvailableSettings.ORDER_UPDATES, true);
        config.putIfAbsent("hibernate.jdbc.batch_versioned_data", true);
        config.putIfAbsent(AvailableSettings.QUERY_PLAN_CACHE_MAX_SIZE, 2048);
        config.putIfAbsent(AvailableSettings.CONNECTION_PROVIDER_DISABLES_AUTOCOMMIT, true);

        return config;
    }

    private PersistenceUnitInfo createPersistenceUnit() {
        return new PersistenceUnitInfo() {
            @Override
            public String getPersistenceUnitName() {
                return "JEHibernatePersistenceUnit";
            }

            @Override
            public String getPersistenceProviderClassName() {
                return HibernatePersistenceProvider.class.getName();
            }

            @Override
            public String getScopeAnnotationName() {
                return null;
            }

            @Override
            public List<String> getQualifierAnnotationNames() {
                return Collections.emptyList();
            }

            @Override
            public PersistenceUnitTransactionType getTransactionType() {
                return PersistenceUnitTransactionType.RESOURCE_LOCAL;
            }

            @Override
            public DataSource getJtaDataSource() {
                return null;
            }

            @Override
            public DataSource getNonJtaDataSource() {
                return null;
            }

            @Override
            public List<String> getMappingFileNames() {
                return Collections.emptyList();
            }

            @Override
            public List<URL> getJarFileUrls() {
                return Collections.emptyList();
            }

            @Override
            public URL getPersistenceUnitRootUrl() {
                return null;
            }

            @Override
            public List<String> getManagedClassNames() {
                return entityClasses.stream()
                    .map(Class::getName)
                    .toList();
            }

            @Override
            public boolean excludeUnlistedClasses() {
                return !entityClasses.isEmpty();
            }

            @Override
            public SharedCacheMode getSharedCacheMode() {
                return SharedCacheMode.UNSPECIFIED;
            }

            @Override
            public ValidationMode getValidationMode() {
                return ValidationMode.AUTO;
            }

            @Override
            public Properties getProperties() {
                return new Properties();
            }

            @Override
            public String getPersistenceXMLSchemaVersion() {
                return "3.1";
            }

            @Override
            public ClassLoader getClassLoader() {
                return Thread.currentThread().getContextClassLoader();
            }

            @Override
            public void addTransformer(ClassTransformer transformer) {
            }

            @Override
            public ClassLoader getNewTempClassLoader() {
                return Thread.currentThread().getContextClassLoader();
            }
        };
    }
}
