package de.jexcellence.hibernate.util;

import de.jexcellence.hibernate.type.DatabaseType;
import org.hibernate.cfg.AvailableSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Loads and validates Hibernate configuration properties.
 *
 * <p>Supports database-specific prefixed properties:
 * <ul>
 *   <li>{@code h2.*}, {@code mysql.*}, {@code mariadb.*}, {@code postgresql.*}</li>
 *   <li>{@code oracle.*}, {@code mssql.*}, {@code sqlite.*}, {@code hsqldb.*}</li>
 *   <li>{@code hibernate.*} - Hibernate ORM settings</li>
 * </ul>
 *
 * @author JExcellence
 * @version 1.0
 * @since 1.0
 */
public final class ConfigLoader {

    private static final String FALLBACK_PATH = "hibernate.properties";
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigLoader.class);

    @NotNull
    public Properties loadAndValidateProperties(@NotNull final String filePath) throws IOException {
        var rawProperties = new Properties();
        this.loadProperties(filePath, rawProperties);
        return this.buildFinalProperties(rawProperties);
    }

    private void loadProperties(@NotNull final String filePath, @NotNull final Properties target) throws IOException {
        var path = Path.of(filePath);
        try (var stream = Files.newInputStream(path)) {
            target.load(stream);
            LOGGER.info("Loaded Hibernate properties from {}", path.toAbsolutePath());
            return;
        } catch (final IOException e) {
            LOGGER.warn("Failed to read properties from {}, attempting bundled fallback", path.toAbsolutePath());
        }

        try (var stream = this.getClass().getClassLoader().getResourceAsStream(FALLBACK_PATH)) {
            if (stream == null) {
                throw new IOException("Fallback properties file '%s' is not available".formatted(FALLBACK_PATH));
            }
            target.load(stream);
            LOGGER.info("Loaded Hibernate properties from bundled resource {}", FALLBACK_PATH);
        }
    }

    @NotNull
    private Properties buildFinalProperties(@NotNull final Properties rawProperties) {
        var finalProperties = new Properties();
        var databaseType = this.resolveDatabaseType(rawProperties.getProperty("database.type"));
        var prefix = this.getDatabasePrefix(databaseType);

        LOGGER.info("Using database type: {}", databaseType);

        this.applyDatabaseProperties(rawProperties, finalProperties, prefix, databaseType);
        this.applyHibernateSettings(rawProperties, finalProperties);

        return finalProperties;
    }

    private void applyDatabaseProperties(
        @NotNull final Properties source,
        @NotNull final Properties target,
        @NotNull final String prefix,
        @NotNull final DatabaseType databaseType
    ) {
        var url = source.getProperty(prefix + ".url");
        if (url != null && !url.isBlank()) {
            target.setProperty(AvailableSettings.JAKARTA_JDBC_URL, url);
        } else if (databaseType == DatabaseType.H2) {
            target.setProperty(AvailableSettings.JAKARTA_JDBC_URL, "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        }

        var driver = source.getProperty(prefix + ".driver");
        target.setProperty(AvailableSettings.JAKARTA_JDBC_DRIVER,
            driver != null && !driver.isBlank() ? driver : this.getDefaultDriver(databaseType));

        var dialect = source.getProperty(prefix + ".dialect");
        target.setProperty(AvailableSettings.DIALECT,
            dialect != null && !dialect.isBlank() ? dialect : this.getDefaultDialect(databaseType));

        this.applyCredentials(source, target, prefix, databaseType);
    }

    private void applyCredentials(
        @NotNull final Properties source,
        @NotNull final Properties target,
        @NotNull final String prefix,
        @NotNull final DatabaseType databaseType
    ) {
        var username = source.getProperty(prefix + ".username");
        var password = source.getProperty(prefix + ".password");

        if (databaseType == DatabaseType.H2) {
            target.setProperty(AvailableSettings.JAKARTA_JDBC_USER, username != null ? username : "sa");
            target.setProperty(AvailableSettings.JAKARTA_JDBC_PASSWORD, password != null ? password : "");
        } else {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException(
                    "Database username is required for %s. Set '%s.username' in configuration.".formatted(databaseType, prefix));
            }
            if (password == null) {
                throw new IllegalArgumentException(
                    "Database password is required for %s. Set '%s.password' in configuration.".formatted(databaseType, prefix));
            }
            target.setProperty(AvailableSettings.JAKARTA_JDBC_USER, username);
            target.setProperty(AvailableSettings.JAKARTA_JDBC_PASSWORD, password);
        }
    }

    private void applyHibernateSettings(@NotNull final Properties source, @NotNull final Properties target) {
        var hbm2ddl = source.getProperty("hibernate.hbm2ddl.auto");
        if (hbm2ddl != null) {
            target.setProperty(AvailableSettings.HBM2DDL_AUTO, hbm2ddl);
        }

        this.copyIfPresent(source, target, "hibernate.show_sql", AvailableSettings.SHOW_SQL);
        this.copyIfPresent(source, target, "hibernate.format_sql", AvailableSettings.FORMAT_SQL);
        this.copyIfPresent(source, target, "hibernate.use_sql_comments", AvailableSettings.USE_SQL_COMMENTS);
        this.copyIfPresent(source, target, "hibernate.highlight_sql", AvailableSettings.HIGHLIGHT_SQL);
        this.copyIfPresent(source, target, "hibernate.jdbc.batch_size", AvailableSettings.STATEMENT_BATCH_SIZE);
        this.copyIfPresent(source, target, "hibernate.order_inserts", AvailableSettings.ORDER_INSERTS);
        this.copyIfPresent(source, target, "hibernate.order_updates", AvailableSettings.ORDER_UPDATES);
        this.copyIfPresent(source, target, "hibernate.generate_statistics", AvailableSettings.GENERATE_STATISTICS);
    }

    private void copyIfPresent(
        @NotNull final Properties source,
        @NotNull final Properties target,
        @NotNull final String sourceKey,
        @NotNull final String targetKey
    ) {
        var value = source.getProperty(sourceKey);
        if (value != null) {
            target.setProperty(targetKey, value);
        }
    }

    @NotNull
    private String getDatabasePrefix(@NotNull final DatabaseType databaseType) {
        return switch (databaseType) {
            case H2 -> "h2";
            case MYSQL -> "mysql";
            case MARIADB -> "mariadb";
            case POSTGRESQL -> "postgresql";
            case ORACLE -> "oracle";
            case MSSQL_SERVER -> "mssql";
            case SQLITE -> "sqlite";
            case HSQLDB -> "hsqldb";
        };
    }

    @NotNull
    private String getDefaultDriver(@NotNull final DatabaseType databaseType) {
        return switch (databaseType) {
            case H2 -> "org.h2.Driver";
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
            case MARIADB -> "org.mariadb.jdbc.Driver";
            case POSTGRESQL -> "org.postgresql.Driver";
            case ORACLE -> "oracle.jdbc.OracleDriver";
            case MSSQL_SERVER -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case SQLITE -> "org.sqlite.JDBC";
            case HSQLDB -> "org.hsqldb.jdbc.JDBCDriver";
        };
    }

    @NotNull
    private String getDefaultDialect(@NotNull final DatabaseType databaseType) {
        return switch (databaseType) {
            case H2 -> "org.hibernate.dialect.H2Dialect";
            case MYSQL -> "org.hibernate.dialect.MySQLDialect";
            case MARIADB -> "org.hibernate.dialect.MariaDBDialect";
            case POSTGRESQL -> "org.hibernate.dialect.PostgreSQLDialect";
            case ORACLE -> "org.hibernate.dialect.OracleDialect";
            case MSSQL_SERVER -> "org.hibernate.dialect.SQLServerDialect";
            case SQLITE -> "org.hibernate.community.dialect.SQLiteDialect";
            case HSQLDB -> "org.hibernate.dialect.HSQLDialect";
        };
    }

    @NotNull
    private DatabaseType resolveDatabaseType(@Nullable final String type) {
        if (type == null || type.isBlank()) {
            LOGGER.info("No database.type specified, defaulting to H2");
            return DatabaseType.H2;
        }

        var normalizedType = type.toUpperCase(Locale.ROOT).replace("-", "_");

        try {
            return DatabaseType.valueOf(normalizedType);
        } catch (final IllegalArgumentException e) {
            var supportedTypes = Arrays.stream(DatabaseType.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                "Unsupported database type '%s'. Supported types: %s".formatted(type, supportedTypes));
        }
    }
}
