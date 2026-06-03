package de.jexcellence.jehibernate.migration;

import java.util.Properties;

/**
 * Immutable migration configuration.
 * <p>
 * Defaults: migration <b>enabled</b>, tool {@link MigrationTool#FLYWAY}, location
 * {@code classpath:db/migration} (the Flyway convention). For Liquibase the {@code location}
 * is interpreted as the changelog path (e.g. {@code db/changelog/db.changelog-master.xml}).
 * <p>
 * Recognised properties (read by {@link #fromProperties(Properties, MigrationConfig)}):
 * <ul>
 *   <li>{@code jehibernate.migration.enabled} — {@code true}/{@code false}</li>
 *   <li>{@code jehibernate.migration.tool} — {@code flyway} | {@code liquibase} | {@code none}</li>
 *   <li>{@code jehibernate.migration.location} — migration/changelog location</li>
 * </ul>
 *
 * @param enabled  whether JEHibernate runs migrations at bootstrap
 * @param tool     the migration tool to use
 * @param location Flyway migration location, or Liquibase changelog path
 * @since 4.0
 */
public record MigrationConfig(
    boolean enabled,
    MigrationTool tool,
    String location
) {

    private static final String KEY_ENABLED = "jehibernate.migration.enabled";
    private static final String KEY_TOOL = "jehibernate.migration.tool";
    private static final String KEY_LOCATION = "jehibernate.migration.location";

    private static final String DEFAULT_FLYWAY_LOCATION = "classpath:db/migration";

    public MigrationConfig {
        if (tool == null) {
            throw new IllegalArgumentException("MigrationTool must not be null");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Migration location must not be null or blank");
        }
    }

    /**
     * Returns the default migration configuration: enabled, Flyway, {@code classpath:db/migration}.
     *
     * @return the default configuration
     */
    public static MigrationConfig defaults() {
        return new MigrationConfig(true, MigrationTool.FLYWAY, DEFAULT_FLYWAY_LOCATION);
    }

    /**
     * Merges {@code jehibernate.migration.*} properties over a base configuration.
     *
     * @param props the properties to read
     * @param base  fallback values for absent keys (e.g. {@link #defaults()})
     * @return the merged configuration
     */
    public static MigrationConfig fromProperties(Properties props, MigrationConfig base) {
        boolean enabled = boolProp(props, KEY_ENABLED, base.enabled);
        MigrationTool tool = toolProp(props, base.tool);
        String location = props.getProperty(KEY_LOCATION, base.location);
        return new MigrationConfig(enabled, tool, location);
    }

    private static boolean boolProp(Properties props, String key, boolean fallback) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static MigrationTool toolProp(Properties props, MigrationTool fallback) {
        String value = props.getProperty(KEY_TOOL);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return MigrationTool.valueOf(value.trim().toUpperCase());
    }
}
