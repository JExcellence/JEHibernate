package de.jexcellence.jehibernate.spring;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.tenant.MultiTenancyStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the JEHibernate Spring Boot auto-configuration, bound from the
 * {@code jehibernate.*} namespace.
 * <p>
 * Example {@code application.yml}:
 * <pre>{@code
 * jehibernate:
 *   database: POSTGRESQL
 *   ddl-auto: validate
 *   scan-packages: [com.example.domain]
 *   migration-enabled: false      # let Spring Boot's own Flyway run migrations
 *   multi-tenancy: NONE
 * }</pre>
 *
 * @since 4.0
 */
@ConfigurationProperties(prefix = "jehibernate")
public class JEHibernateProperties {

    /** Whether the auto-configuration is active. */
    private boolean enabled = true;

    /** Database type — selects the Hibernate dialect. Defaults to H2. */
    private DatabaseType database = DatabaseType.H2;

    /**
     * Optional JDBC URL. Connections are served by the Spring {@code DataSource}; this is only used
     * to satisfy configuration validation and is otherwise inert. A placeholder is synthesized when
     * absent.
     */
    private String url;

    /** Optional username (ignored when the Spring DataSource serves connections). */
    private String username;

    /** Optional password (ignored when the Spring DataSource serves connections). */
    private String password;

    /** Packages scanned for entities and repositories. Empty disables auto-scan. */
    private List<String> scanPackages = new ArrayList<>();

    /** Hibernate {@code hbm2ddl.auto} mode. Defaults to {@code validate} (migrations own the schema). */
    private String ddlAuto = "validate";

    /**
     * Whether JEHibernate runs Flyway at bootstrap. Defaults to {@code false} because Spring Boot
     * typically runs Flyway itself; enable only if Spring's Flyway auto-configuration is absent.
     */
    private boolean migrationEnabled = false;

    /** Multi-tenancy strategy. {@code DATABASE} must be configured programmatically (needs a tenant→DataSource map). */
    private MultiTenancyStrategy multiTenancy = MultiTenancyStrategy.NONE;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public DatabaseType getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseType database) {
        this.database = database;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getScanPackages() {
        return scanPackages;
    }

    public void setScanPackages(List<String> scanPackages) {
        this.scanPackages = scanPackages;
    }

    public String getDdlAuto() {
        return ddlAuto;
    }

    public void setDdlAuto(String ddlAuto) {
        this.ddlAuto = ddlAuto;
    }

    public boolean isMigrationEnabled() {
        return migrationEnabled;
    }

    public void setMigrationEnabled(boolean migrationEnabled) {
        this.migrationEnabled = migrationEnabled;
    }

    public MultiTenancyStrategy getMultiTenancy() {
        return multiTenancy;
    }

    public void setMultiTenancy(MultiTenancyStrategy multiTenancy) {
        this.multiTenancy = multiTenancy;
    }
}
