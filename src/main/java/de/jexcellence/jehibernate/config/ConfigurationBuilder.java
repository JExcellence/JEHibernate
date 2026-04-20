package de.jexcellence.jehibernate.config;

import de.jexcellence.jehibernate.exception.ConfigurationException;
import de.jexcellence.jehibernate.exception.JEHibernateException;
import de.jexcellence.jehibernate.naming.NamingStrategy;
import de.jexcellence.jehibernate.naming.SnakeCaseStrategy;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Fluent builder for configuring and creating JPA {@link EntityManagerFactory} instances.
 * <p>
 * Provides sensible defaults for batch processing, connection management, and performance
 * while allowing full customization of Hibernate properties.
 * <p>
 * <b>Why native Hibernate bootstrapping?</b>
 * Going through {@code HibernatePersistenceProvider.createContainerEntityManagerFactory} has
 * the physical naming strategy pass through {@code StrategySelector.resolveStrategy()}.  When
 * JEHibernate is downloaded at runtime by JEDependency and injected into the plugin classloader
 * while Hibernate core lives in a separate classloader, {@code PhysicalNamingStrategy.class} from
 * Hibernate's classloader and the same interface as seen from the plugin classloader are two
 * distinct {@code Class} objects.  The {@code instanceof} check in {@code StrategySelector}
 * therefore fails, the selector falls back to {@code value.getClass().getName()}, and Hibernate
 * raises {@code StrategySelectionException: Unable to resolve name [SnakeCaseStrategy]}.
 * <p>
 * Using {@code MetadataBuilder.applyPhysicalNamingStrategy(instance)} bypasses
 * {@code StrategySelector} entirely — the instance is stored directly without any name or type
 * resolution step.  Registering the plugin classloader with {@code BootstrapServiceRegistryBuilder}
 * additionally ensures that Hibernate's aggregated {@code ClassLoaderService} can find all
 * plugin-side classes (entities, converters, listeners) during metadata processing.
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
            String k = key.toString();
            // Pass through all hibernate.* properties except physical_naming_strategy —
            // that is always set as a live instance via MetadataBuilder to avoid
            // StrategySelector classloader resolution issues (see class-level Javadoc).
            if (k.startsWith("hibernate.") && !k.equals(AvailableSettings.PHYSICAL_NAMING_STRATEGY)) {
                properties.put(k, value);
            }
        });

        return this;
    }

    /**
     * Builds the {@link EntityManagerFactory} using Hibernate's native bootstrap API.
     * <p>
     * The plugin classloader is registered with {@link BootstrapServiceRegistryBuilder} so
     * Hibernate's aggregated {@code ClassLoaderService} can find all plugin-side types.
     * The physical naming strategy is applied via
     * {@code MetadataBuilder.applyPhysicalNamingStrategy(instance)}, which stores the instance
     * directly and bypasses {@code StrategySelector} entirely.
     *
     * @return a fully initialised {@link EntityManagerFactory} (backed by a Hibernate
     *         {@code SessionFactory}, which implements that interface)
     * @throws ConfigurationException   if no database configuration has been set
     * @throws JEHibernateException     if Hibernate bootstrapping fails
     */
    public EntityManagerFactory build() {
        validate();

        // The defining classloader of ConfigurationBuilder is the plugin classloader — it can
        // see the plugin JAR (entities, converters, naming strategies) as well as the
        // JEDependency-injected Hibernate JARs. Passing it to BootstrapServiceRegistryBuilder
        // registers it with Hibernate's AggregatedClassLoader so that all service lookups,
        // proxy generation, and annotation scanning can find plugin-side classes.
        final ClassLoader pluginClassLoader = ConfigurationBuilder.class.getClassLoader();

        Map<String, Object> config = buildConfiguration();

        BootstrapServiceRegistry bsr = new BootstrapServiceRegistryBuilder()
            .applyClassLoader(pluginClassLoader)
            .build();

        try {
            StandardServiceRegistry ssr = new StandardServiceRegistryBuilder(bsr)
                .applySettings(config)
                .build();

            try {
                MetadataSources sources = new MetadataSources(ssr);
                entityClasses.forEach(sources::addAnnotatedClass);

                // applyPhysicalNamingStrategy stores the instance in a field on
                // MetadataBuilderImpl — no StrategySelector, no class-name resolution,
                // no classloader lookup.  This is the canonical fix for the
                // StrategySelectionException that occurs when the strategy class is visible
                // to the plugin classloader but not to Hibernate's internal ClassLoaderService.
                Metadata metadata = sources.getMetadataBuilder()
                    .applyPhysicalNamingStrategy(namingStrategy)
                    .build();

                // SessionFactory extends EntityManagerFactory in Hibernate 7 — safe upcast.
                return metadata.getSessionFactoryBuilder().build();

            } catch (Exception e) {
                // Destroy SSR (and its child resources) on failure so connections are released.
                StandardServiceRegistryBuilder.destroy(ssr);
                if (e instanceof RuntimeException re) throw re;
                throw new JEHibernateException("Failed to build EntityManagerFactory", e);
            }
        } catch (RuntimeException e) {
            // BSR holds no DB resources but close it for tidiness on any build failure.
            bsr.close();
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void validate() {
        if (databaseConfig == null) {
            throw new ConfigurationException("Database configuration is required");
        }
    }

    /**
     * Assembles the settings map passed to {@link StandardServiceRegistryBuilder}.
     * <p>
     * {@code PHYSICAL_NAMING_STRATEGY} is intentionally omitted — it is applied as a live
     * instance via {@code MetadataBuilder.applyPhysicalNamingStrategy()} in {@link #build()}.
     * Including a string or instance value here would route through {@code StrategySelector}
     * and trigger the classloader-mismatch failure this class is designed to prevent.
     */
    private Map<String, Object> buildConfiguration() {
        Map<String, Object> config = new HashMap<>(properties);

        // JDBC connection — Hibernate 7 processes jakarta.persistence.jdbc.* in both the
        // JPA provider path and the native StandardServiceRegistry path (DriverManager
        // connection provider tries the jakarta key first, then hibernate.connection.*).
        config.put(AvailableSettings.JAKARTA_JDBC_URL, databaseConfig.url());
        config.put(AvailableSettings.JAKARTA_JDBC_DRIVER, databaseConfig.driver());
        config.put(AvailableSettings.DIALECT, databaseConfig.dialect());

        if (databaseConfig.username() != null) {
            config.put(AvailableSettings.JAKARTA_JDBC_USER, databaseConfig.username());
        }
        if (databaseConfig.password() != null) {
            config.put(AvailableSettings.JAKARTA_JDBC_PASSWORD, databaseConfig.password());
        }

        // Sensible defaults — caller-supplied values already in `properties` take precedence
        // because we copied them into `config` above before these putIfAbsent calls.
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
}
