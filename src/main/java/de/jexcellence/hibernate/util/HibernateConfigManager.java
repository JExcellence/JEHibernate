package de.jexcellence.hibernate.util;

import de.jexcellence.hibernate.type.DatabaseType;
import org.hibernate.cfg.AvailableSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Factory class for loading and validating Hibernate properties.
 */
public class HibernateConfigManager {

    private static final String FALLBACK_PATH = "hibernate.properties";
    private static final Logger logger = LoggerFactory.getLogger(HibernateConfigManager.class);
    private final Properties properties;

    /**
     * Constructs a new {@code HibernateConfigManager} with an empty {@link Properties} instance.
     */
    public HibernateConfigManager() {
        this.properties = new Properties();
    }

    /**
     * Loads and validates properties from the specified file path.
     *
     * @param filePath the path to the properties file
     * @return the loaded and validated {@link Properties} instance
     * @throws IOException if an error occurs while loading properties
     */
    public Properties loadAndValidateProperties(final String filePath) throws IOException {
        this.loadProperties(filePath);
        this.ensurePropertiesValidity();
        return this.properties;
    }

    /**
     * Loads properties from the specified file, using a fallback if necessary.
     *
     * @param filePath the path to the properties file
     * @throws IOException if an error occurs while loading properties
     */
    private void loadProperties(final String filePath) throws IOException {
        try (InputStream externalInputStream = Files.newInputStream(Paths.get(filePath))) {
            this.properties.load(externalInputStream);
            logger.info("Properties loaded from external file: {}", filePath);
        } catch (IOException exception) {
            logger.warn("Failed to load properties from external file: {}, attempting fallback", filePath);
            try (InputStream internalInputStream = getClass().getClassLoader().getResourceAsStream(FALLBACK_PATH)) {
                if (internalInputStream != null) {
                    this.properties.load(internalInputStream);
                    logger.info("Properties loaded from fallback file: {}", FALLBACK_PATH);
                } else {
                    logger.error("Fallback property file not found: {}", FALLBACK_PATH);
                    throw new IOException("Property file not found: " + filePath + ", using fallback file: " + FALLBACK_PATH);
                }
            }
        }
    }

    /**
     * Validates the loaded properties and sets default values where necessary.
     */
    private void ensurePropertiesValidity() {
        DatabaseType databaseType = DatabaseType.valueOf(this.properties.getOrDefault("database.type", "H2").toString().toUpperCase());

        if (this.properties.get(AvailableSettings.JAKARTA_JDBC_URL) == null) {
            this.properties.put(AvailableSettings.JAKARTA_JDBC_URL, this.buildConnectionString(databaseType));
        }

        if (databaseType == DatabaseType.H2) {
            this.applyDefaultH2Settings();
        } else {
            this.checkNonH2Settings();
        }
    }

    /**
     * Sets default properties for H2 database configuration.
     */
    private void applyDefaultH2Settings() {
        this.properties.putIfAbsent(AvailableSettings.JAKARTA_JDBC_DRIVER, "org.h2.Driver");
        this.properties.putIfAbsent(AvailableSettings.JAKARTA_JDBC_USER, "sa");
        this.properties.putIfAbsent(AvailableSettings.JAKARTA_JDBC_PASSWORD, "");
    }

    /**
     * Validates properties for non-H2 database configurations.
     *
     * @throws IllegalArgumentException if required properties are missing
     */
    private void checkNonH2Settings() {
        if (!this.properties.containsKey(AvailableSettings.JAKARTA_JDBC_URL) ||
                !this.properties.containsKey(AvailableSettings.JAKARTA_JDBC_USER) ||
                !this.properties.containsKey(AvailableSettings.JAKARTA_JDBC_PASSWORD)) {
            throw new IllegalArgumentException("Missing required properties for non-H2 database configuration.");
        }
    }

    /**
     * Constructs a connection string based on the database type and properties.
     *
     * @param databaseType the type of the database
     * @return the constructed connection string
     */
    private String buildConnectionString(final DatabaseType databaseType) {
        if (databaseType == DatabaseType.H2) {
            return createH2ConnectionString();
        }
        return createStandardConnectionString(databaseType);
    }

    private String createH2ConnectionString() {
        String databaseName = this.properties.getProperty("database.name", "testdb");
        return "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1";
    }

    private String createStandardConnectionString(final DatabaseType databaseType) {
        String databaseName = this.properties.getProperty("database.name", "testdb");
        String databaseHost = this.properties.getProperty("database.host", "localhost");
        String databasePort = this.properties.getProperty("database.port", "3306");
        return "jdbc:" + databaseType.name().toLowerCase() + "://" + databaseHost + ":" + databasePort + "/" + databaseName;
    }
}