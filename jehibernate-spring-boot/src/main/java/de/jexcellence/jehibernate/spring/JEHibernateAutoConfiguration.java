package de.jexcellence.jehibernate.spring;

import de.jexcellence.jehibernate.config.ConfigurationBuilder;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.migration.MigrationConfig;
import de.jexcellence.jehibernate.migration.MigrationTool;
import de.jexcellence.jehibernate.repository.manager.RepositoryRegistry;
import de.jexcellence.jehibernate.tenant.MultiTenancyConfig;
import de.jexcellence.jehibernate.tenant.MultiTenancyStrategy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Spring Boot auto-configuration for JEHibernate.
 * <p>
 * Activated when JEHibernate and a {@link DataSource} are on the classpath and a {@code DataSource}
 * bean exists. It builds a {@link JEHibernate} that <b>reuses the application's DataSource</b>
 * (Spring's connection pool) rather than creating its own, and never closes that DataSource — its
 * lifecycle stays with Spring. Driven entirely by {@link JEHibernateProperties}
 * ({@code jehibernate.*}).
 * <p>
 * Migration defaults to off (Spring Boot usually runs Flyway itself); enable via
 * {@code jehibernate.migration-enabled=true} if Spring's Flyway auto-configuration is absent.
 *
 * @since 4.0
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass({JEHibernate.class, DataSource.class})
@ConditionalOnProperty(prefix = "jehibernate", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JEHibernateProperties.class)
public class JEHibernateAutoConfiguration {

    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    /**
     * Builds the {@link JEHibernate} bean from the application {@link DataSource} and properties.
     * Closed on context shutdown; the DataSource is left to Spring.
     *
     * @param dataSource the application data source
     * @param properties the {@code jehibernate.*} configuration
     * @return the configured JEHibernate instance
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(JEHibernate.class)
    public JEHibernate jeHibernate(DataSource dataSource, JEHibernateProperties properties) {
        MigrationConfig migration = new MigrationConfig(
            properties.isMigrationEnabled(), MigrationTool.FLYWAY, MIGRATION_LOCATION);
        MultiTenancyConfig multiTenancy = buildMultiTenancy(properties.getMultiTenancy());

        JEHibernate.Builder builder = JEHibernate.builder()
            .configuration(config -> applyConfiguration(config, dataSource, properties, migration, multiTenancy));

        if (properties.getScanPackages().isEmpty()) {
            builder.disableAutoScan();
        } else {
            builder.scanPackages(properties.getScanPackages().toArray(new String[0]));
        }
        return builder.build();
    }

    /**
     * Exposes JEHibernate's repository registry as a bean for injection.
     *
     * @param jeHibernate the JEHibernate instance
     * @return the repository registry
     */
    @Bean
    @ConditionalOnMissingBean(RepositoryRegistry.class)
    public RepositoryRegistry jeHibernateRepositories(JEHibernate jeHibernate) {
        return jeHibernate.repositories();
    }

    private void applyConfiguration(
        ConfigurationBuilder config,
        DataSource dataSource,
        JEHibernateProperties properties,
        MigrationConfig migration,
        MultiTenancyConfig multiTenancy) {

        config.database(properties.getDatabase())
            .url(resolveUrl(properties))
            .dataSource(dataSource)
            .ddlAuto(properties.getDdlAuto())
            .migration(migration);

        if (properties.getUsername() != null) {
            config.credentials(properties.getUsername(), properties.getPassword());
        }
        if (multiTenancy.strategy() != MultiTenancyStrategy.NONE) {
            config.multiTenancy(multiTenancy);
        }
    }

    private String resolveUrl(JEHibernateProperties properties) {
        if (properties.getUrl() != null && !properties.getUrl().isBlank()) {
            return properties.getUrl();
        }
        // Connections come from the Spring DataSource; this inert placeholder only satisfies
        // DatabaseConfig validation.
        return "jdbc:" + properties.getDatabase().getPrefix() + ":spring-managed";
    }

    private MultiTenancyConfig buildMultiTenancy(MultiTenancyStrategy strategy) {
        return switch (strategy) {
            case NONE -> MultiTenancyConfig.disabled();
            case SCHEMA -> MultiTenancyConfig.schema(MultiTenancyConfig.DEFAULT_SCHEMA);
            case DISCRIMINATOR -> MultiTenancyConfig.discriminator();
            case DATABASE -> throw new IllegalStateException(
                "DATABASE multi-tenancy needs a tenant->DataSource map; configure JEHibernate "
                    + "programmatically instead of via jehibernate.multi-tenancy=DATABASE");
            default -> throw new IllegalStateException("Unsupported multi-tenancy strategy: " + strategy);
        };
    }
}
