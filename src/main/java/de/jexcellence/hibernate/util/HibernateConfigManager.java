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
import java.util.Locale;
import java.util.Properties;

/**
 * Loads Hibernate properties from disk and applies sensible defaults.
 */
public final class HibernateConfigManager {

    private static final String FALLBACK_PATH = "hibernate.properties";
    private static final Logger LOGGER = LoggerFactory.getLogger(HibernateConfigManager.class);

    /**
     * Loads configuration from {@code filePath} and returns a validated {@link Properties} instance.
     *
     * @param filePath absolute or relative path to the Hibernate properties file
     * @return validated configuration properties
     * @throws IOException when neither the provided file nor the fallback resource can be read
     */
    @NotNull
    public Properties loadAndValidateProperties(@NotNull final String filePath) throws IOException {
        final Properties properties = new Properties();
        this.loadProperties(filePath, properties);
        this.ensurePropertiesValidity(properties);
        return properties;
    }

    private void loadProperties(@NotNull final String filePath, @NotNull final Properties target) throws IOException {
        final Path path = Path.of(filePath);
        try (InputStream externalInputStream = Files.newInputStream(path)) {
            target.load(externalInputStream);
            LOGGER.info("Loaded Hibernate properties from {}", path.toAbsolutePath());
            return;
        } catch (final IOException exception) {
            LOGGER.warn("Failed to read properties from {}, attempting bundled fallback", path.toAbsolutePath());
        }

        try (InputStream internalInputStream = this.getClass().getClassLoader().getResourceAsStream(FALLBACK_PATH)) {
            if (internalInputStream == null) {
                throw new IOException("Fallback properties file '%s' is not available".formatted(FALLBACK_PATH));
            }
            target.load(internalInputStream);
            LOGGER.info("Loaded Hibernate properties from bundled resource {}", FALLBACK_PATH);
        }
    }

    private void ensurePropertiesValidity(@NotNull final Properties properties) {
        final DatabaseType databaseType = this.resolveDatabaseType(properties.getProperty("database.type"));

        properties.putIfAbsent(AvailableSettings.JAKARTA_JDBC_URL, this.buildConnectionString(properties, databaseType));

        if (databaseType == DatabaseType.H2) {
            this.applyDefaultH2Settings(properties);
        } else {
            this.checkNonH2Settings(properties);
        }
    }

    private void applyDefaultH2Settings(@NotNull final Properties properties) {
        properties.putIfAbsent(AvailableSettings.JAKARTA_JDBC_DRIVER, "org.h2.Driver");
        properties.putIfAbsent(AvailableSettings.JAKARTA_JDBC_USER, "sa");
        properties.putIfAbsent(AvailableSettings.JAKARTA_JDBC_PASSWORD, "");
    }

    private void checkNonH2Settings(@NotNull final Properties properties) {
        if (!properties.containsKey(AvailableSettings.JAKARTA_JDBC_USER) ||
            !properties.containsKey(AvailableSettings.JAKARTA_JDBC_PASSWORD)) {
            throw new IllegalArgumentException("Database credentials (user/password) must be provided for non-H2 databases.");
        }
    }

    @NotNull
    private DatabaseType resolveDatabaseType(@Nullable final String type) {
        if (type == null || type.isBlank()) {
            return DatabaseType.H2;
        }
        return DatabaseType.valueOf(type.toUpperCase(Locale.ROOT));
    }

    @NotNull
    private String buildConnectionString(@NotNull final Properties properties, @NotNull final DatabaseType databaseType) {
        if (databaseType == DatabaseType.H2) {
            final String databaseName = properties.getProperty("database.name", "testdb");
            return "jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1".formatted(databaseName);
        }

        final String databaseName = properties.getProperty("database.name", "testdb");
        final String databaseHost = properties.getProperty("database.host", "localhost");
        final String databasePort = properties.getProperty("database.port", "3306");
        return "jdbc:%s://%s:%s/%s".formatted(databaseType.name().toLowerCase(Locale.ROOT), databaseHost, databasePort, databaseName);
    }
}