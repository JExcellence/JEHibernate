package de.jexcellence.jehibernate.config;

import de.jexcellence.jehibernate.exception.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Utility class for loading properties files from filesystem or classpath.
 * <p>
 * This class provides methods to load Java properties files with fallback mechanisms,
 * attempting to load from the filesystem first, then from the classpath.
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Load required properties (throws exception if not found)
 * Properties dbProps = PropertyLoader.load("database.properties");
 * String url = dbProps.getProperty("db.url");
 * 
 * // Load optional properties (returns empty if not found)
 * Properties cacheProps = PropertyLoader.loadOrEmpty("cache.properties");
 * String cacheSize = cacheProps.getProperty("cache.size", "100");
 * }</pre>
 * 
 * <h2>Loading Strategy:</h2>
 * <ol>
 *   <li>Attempts to load from filesystem path</li>
 *   <li>Falls back to classpath resource if file not found</li>
 *   <li>Throws {@link ConfigurationException} if neither location exists (for {@code load})</li>
 *   <li>Returns empty Properties if not found (for {@code loadOrEmpty})</li>
 * </ol>
 * 
 * @since 1.0
 * @see Properties
 * @see ConfigurationException
 */
public final class PropertyLoader {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyLoader.class);
    
    private PropertyLoader() {
    }
    
    /**
     * Loads properties from the specified file path.
     * <p>
     * Attempts to load from filesystem first, then falls back to classpath.
     * Throws an exception if the file cannot be found in either location.
     * 
     * @param filePath the path to the properties file (relative or absolute)
     * @return the loaded properties
     * @throws ConfigurationException if the properties file cannot be found or loaded
     */
    public static Properties load(String filePath) {
        Properties properties = new Properties();
        
        Path path = Path.of(filePath);
        if (Files.exists(path)) {
            try (InputStream stream = Files.newInputStream(path)) {
                properties.load(stream);
                LOGGER.info("Loaded properties from file: {}", path.toAbsolutePath());
                return properties;
            } catch (IOException e) {
                LOGGER.warn("Failed to load properties from file: {}", path.toAbsolutePath());
            }
        }
        
        try (InputStream stream = PropertyLoader.class.getClassLoader().getResourceAsStream(filePath)) {
            if (stream != null) {
                properties.load(stream);
                LOGGER.info("Loaded properties from classpath: {}", filePath);
                return properties;
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to load properties from classpath: {}", filePath);
        }
        
        throw new ConfigurationException("Properties file not found: " + filePath);
    }
    
    /**
     * Loads properties from the specified file path, returning empty properties if not found.
     * <p>
     * This method is useful for loading optional configuration files that may not exist.
     * Unlike {@link #load(String)}, this method does not throw an exception if the file
     * is not found.
     * 
     * @param filePath the path to the properties file (relative or absolute)
     * @return the loaded properties, or an empty Properties object if the file is not found
     */
    public static Properties loadOrEmpty(String filePath) {
        try {
            return load(filePath);
        } catch (ConfigurationException e) {
            LOGGER.debug("Properties file not found, returning empty properties: {}", filePath);
            return new Properties();
        }
    }

    /**
     * Loads properties from a file within a base directory.
     * Ideal for Bukkit/Spigot plugins where the base directory is the plugin data folder.
     * <p>
     * <b>Example (Bukkit plugin):</b>
     * <pre>{@code
     * Properties props = PropertyLoader.load(getDataFolder(), "database", "hibernate.properties");
     * // Loads from: plugins/MyPlugin/database/hibernate.properties
     * }</pre>
     *
     * @param baseDir       the base directory (e.g., plugin.getDataFolder())
     * @param subPathParts  path segments relative to baseDir
     * @return the loaded properties
     * @throws ConfigurationException if the file cannot be found or loaded
     */
    public static Properties load(File baseDir, String... subPathParts) {
        Path path = baseDir.toPath();
        for (String part : subPathParts) {
            path = path.resolve(part);
        }
        return load(path.toString());
    }

    /**
     * Loads properties from a file within a base directory, returning empty if not found.
     *
     * @param baseDir      the base directory
     * @param subPathParts path segments relative to baseDir
     * @return the loaded properties, or empty if not found
     */
    public static Properties loadOrEmpty(File baseDir, String... subPathParts) {
        try {
            return load(baseDir, subPathParts);
        } catch (ConfigurationException e) {
            LOGGER.debug("Properties file not found in {}, returning empty", baseDir);
            return new Properties();
        }
    }
}
