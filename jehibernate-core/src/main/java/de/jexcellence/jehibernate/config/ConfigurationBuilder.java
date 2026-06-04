package de.jexcellence.jehibernate.config;

import de.jexcellence.jehibernate.exception.ConfigurationException;
import de.jexcellence.jehibernate.exception.JEHibernateException;
import de.jexcellence.jehibernate.migration.MigrationConfig;
import de.jexcellence.jehibernate.migration.MigrationSupport;
import de.jexcellence.jehibernate.naming.NamingStrategy;
import de.jexcellence.jehibernate.naming.SnakeCaseStrategy;
import de.jexcellence.jehibernate.pool.HikariDataSourceFactory;
import de.jexcellence.jehibernate.pool.PoolConfig;
import de.jexcellence.jehibernate.tenant.DatabaseMultiTenantConnectionProvider;
import de.jexcellence.jehibernate.tenant.MultiTenancyConfig;
import de.jexcellence.jehibernate.tenant.MultiTenancyStrategy;
import de.jexcellence.jehibernate.tenant.SchemaMultiTenantConnectionProvider;
import de.jexcellence.jehibernate.tenant.TenantContextResolver;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.MultiTenancySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationBuilder.class);

    private DatabaseConfig databaseConfig;
    private final Map<String, Object> properties = new HashMap<>();
    private NamingStrategy namingStrategy = new SnakeCaseStrategy();
    private final Set<Class<?>> entityClasses = new HashSet<>();

    private PoolConfig poolConfig = PoolConfig.defaults();
    private MigrationConfig migrationConfig = MigrationConfig.defaults();
    private MultiTenancyConfig multiTenancyConfig = MultiTenancyConfig.disabled();
    private DataSource externalDataSource;
    private DataSource managedDataSource;
    private boolean ownsDataSource;

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
     * Configures the HikariCP connection pool (the default pool since 4.0).
     * <p>
     * Convenience overload that sets only the idle/max bounds; all other knobs keep their
     * {@link PoolConfig#defaults() defaults}. For full control use {@link #pool(PoolConfig)}.
     * <p>
     * <b>Behavioural change from 3.x:</b> earlier versions delegated to Hibernate's Agroal
     * provider via {@code hibernate.agroal.*} properties. JEHibernate now owns a
     * {@link com.zaxxer.hikari.HikariDataSource} directly (see ADR-0003), which enables
     * {@code getPoolHealth()}, deterministic shutdown, and external-{@link DataSource} reuse.
     *
     * @param minIdle minimum number of idle connections
     * @param maxSize maximum number of connections in the pool
     * @return this builder for chaining
     */
    public ConfigurationBuilder connectionPool(int minIdle, int maxSize) {
        this.poolConfig = PoolConfig.builder()
            .minimumIdle(minIdle)
            .maximumPoolSize(maxSize)
            .build();
        return this;
    }

    /**
     * Sets the full HikariCP pool configuration.
     *
     * @param poolConfig the pool tuning parameters (must not be {@code null})
     * @return this builder for chaining
     */
    public ConfigurationBuilder pool(PoolConfig poolConfig) {
        if (poolConfig == null) {
            throw new ConfigurationException("PoolConfig must not be null");
        }
        this.poolConfig = poolConfig;
        return this;
    }

    /**
     * Supplies an externally managed {@link DataSource} (e.g. a Spring Boot {@code DataSource}
     * bean). When set, JEHibernate does <b>not</b> create its own HikariCP pool and does
     * <b>not</b> close the supplied data source on shutdown — its lifecycle stays with the
     * provider. {@link PoolConfig} is ignored in this case.
     *
     * @param dataSource the data source to use (must not be {@code null})
     * @return this builder for chaining
     */
    public ConfigurationBuilder dataSource(DataSource dataSource) {
        if (dataSource == null) {
            throw new ConfigurationException("DataSource must not be null");
        }
        this.externalDataSource = dataSource;
        return this;
    }

    /**
     * Sets the schema-migration configuration. By default migrations run with Flyway from
     * {@code classpath:db/migration} before the {@code SessionFactory} is built. See
     * {@link MigrationConfig} and {@link MigrationSupport}.
     *
     * @param migrationConfig the migration configuration (must not be {@code null})
     * @return this builder for chaining
     */
    public ConfigurationBuilder migration(MigrationConfig migrationConfig) {
        if (migrationConfig == null) {
            throw new ConfigurationException("MigrationConfig must not be null");
        }
        this.migrationConfig = migrationConfig;
        return this;
    }

    /**
     * Sets the multi-tenancy configuration. Default is {@link MultiTenancyConfig#disabled()}
     * (single-tenant). For SCHEMA/DATABASE a {@code MultiTenantConnectionProvider} is wired over the
     * resolved pool; for DISCRIMINATOR only the tenant resolver is wired (entities use
     * {@code @TenantId}). See the multi-tenancy guide.
     *
     * @param multiTenancyConfig the configuration (must not be {@code null})
     * @return this builder for chaining
     */
    public ConfigurationBuilder multiTenancy(MultiTenancyConfig multiTenancyConfig) {
        if (multiTenancyConfig == null) {
            throw new ConfigurationException("MultiTenancyConfig must not be null");
        }
        this.multiTenancyConfig = multiTenancyConfig;
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

        // Pool tuning: merge jehibernate.pool.* over the current pool config.
        this.poolConfig = PoolConfig.fromProperties(props, this.poolConfig);
        // Migration: merge jehibernate.migration.* over the current migration config.
        this.migrationConfig = MigrationConfig.fromProperties(props, this.migrationConfig);

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
        resolveDataSource();

        try {
            // Migrations own the schema and run before Hibernate validates it. No-op when the
            // selected tool is absent or migration is disabled (see MigrationSupport).
            MigrationSupport.run(managedDataSource, migrationConfig);
        } catch (RuntimeException e) {
            closeManagedDataSourceQuietly();
            throw e;
        }

        final ClassLoader pluginClassLoader = ConfigurationBuilder.class.getClassLoader();

        BootstrapServiceRegistry bsr = new BootstrapServiceRegistryBuilder()
            .applyClassLoader(pluginClassLoader)
            .build();

        try {
            return buildWithRegistry(bsr);
        } catch (RuntimeException e) {
            bsr.close();
            closeManagedDataSourceQuietly();
            throw e;
        }
    }

    /**
     * The {@link DataSource} JEHibernate will use — either the externally supplied one or a
     * freshly created HikariCP pool. Valid only after {@link #build()} has been invoked.
     *
     * @return the resolved data source, or {@code null} if {@link #build()} has not run yet
     */
    public DataSource getManagedDataSource() {
        return managedDataSource;
    }

    /**
     * Whether JEHibernate owns (and must therefore close) the resolved {@link DataSource}.
     * {@code false} when an external data source was supplied via {@link #dataSource(DataSource)}.
     *
     * @return {@code true} if the data source must be closed on shutdown
     */
    public boolean ownsDataSource() {
        return ownsDataSource;
    }

    private void resolveDataSource() {
        if (externalDataSource != null) {
            this.managedDataSource = externalDataSource;
            this.ownsDataSource = false;
            LOGGER.info("Using externally supplied DataSource — JEHibernate will not manage its lifecycle");
        } else {
            this.managedDataSource = HikariDataSourceFactory.create(databaseConfig, poolConfig);
            this.ownsDataSource = true;
            LOGGER.info("Created HikariCP pool [max={}, minIdle={}] for {}",
                poolConfig.maximumPoolSize(), poolConfig.minimumIdle(), databaseConfig.type());
        }
    }

    private void closeManagedDataSourceQuietly() {
        if (ownsDataSource && managedDataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                LOGGER.warn("Failed to close HikariCP pool after bootstrap failure", e);
            }
        }
    }

    private EntityManagerFactory buildWithRegistry(BootstrapServiceRegistry bsr) {
        StandardServiceRegistry ssr = new StandardServiceRegistryBuilder(bsr)
            .applySettings(buildConfiguration())
            .build();

        try {
            MetadataSources sources = new MetadataSources(ssr);
            entityClasses.forEach(sources::addAnnotatedClass);

            Metadata metadata = sources.getMetadataBuilder()
                .applyPhysicalNamingStrategy(namingStrategy)
                .build();

            return metadata.getSessionFactoryBuilder().build();

        } catch (Exception e) {
            StandardServiceRegistryBuilder.destroy(ssr);
            if (e instanceof RuntimeException re) throw re;
            throw new JEHibernateException("Failed to build EntityManagerFactory", e);
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
     * Wires Hibernate's native multi-tenancy settings according to {@link #multiTenancyConfig}.
     * NONE leaves single-tenant behaviour untouched. SCHEMA/DATABASE register a
     * {@code MultiTenantConnectionProvider} over the resolved pool; DISCRIMINATOR registers only the
     * tenant resolver ({@code @TenantId} on entities does the filtering).
     */
    private void applyMultiTenancy(Map<String, Object> config) {
        MultiTenancyStrategy strategy = multiTenancyConfig.strategy();
        if (strategy == MultiTenancyStrategy.NONE) {
            return;
        }

        config.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, new TenantContextResolver(
            multiTenancyConfig.resolver(),
            multiTenancyConfig.strict(),
            multiTenancyConfig.defaultTenantId()
        ));

        switch (strategy) {
            case SCHEMA -> config.put(
                MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER,
                new SchemaMultiTenantConnectionProvider(managedDataSource, multiTenancyConfig.defaultSchema()));
            case DATABASE -> config.put(
                MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER,
                new DatabaseMultiTenantConnectionProvider(multiTenancyConfig.tenantDataSources(), managedDataSource));
            case DISCRIMINATOR -> LOGGER.info("Multi-tenancy: DISCRIMINATOR - entities use @TenantId, resolver wired");
            default -> throw new ConfigurationException("Unsupported multi-tenancy strategy: " + strategy);
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

        // Connections are served by the JEHibernate-owned DataSource (HikariCP by default, or an
        // externally supplied DataSource). Hibernate uses DatasourceConnectionProviderImpl for it.
        // SCHEMA/DATABASE multi-tenancy instead routes connections through a
        // MultiTenantConnectionProvider, so the plain data source slot is omitted in those cases.
        MultiTenancyStrategy strategy = multiTenancyConfig.strategy();
        boolean usesConnectionProvider =
            strategy == MultiTenancyStrategy.SCHEMA || strategy == MultiTenancyStrategy.DATABASE;
        if (!usesConnectionProvider) {
            config.put(AvailableSettings.JAKARTA_NON_JTA_DATASOURCE, managedDataSource);
        }
        config.put(AvailableSettings.DIALECT, databaseConfig.dialect());
        applyMultiTenancy(config);

        // Sensible defaults — caller-supplied values already in `properties` take precedence
        // because we copied them into `config` above before these putIfAbsent calls.
        // ddl-auto defaults to "validate" since 4.0 (was "update"): migrations now own the
        // schema (see ADR-0002). Callers that still want Hibernate to manage DDL set
        // ddlAuto("update") explicitly.
        config.putIfAbsent(AvailableSettings.HBM2DDL_AUTO, "validate");
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
