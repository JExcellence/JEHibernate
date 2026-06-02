package de.jexcellence.jehibernate.plugin;

import de.jexcellence.jehibernate.config.PropertyLoader;

import java.io.File;
import java.util.Properties;

/**
 * Spigot/Paper convenience for loading properties from a plugin's data folder.
 * <p>
 * This is the plugin-facing entry point that keeps the {@code File}-based, data-folder lookup out
 * of the plugin-agnostic core. It delegates to {@link PropertyLoader}.
 * <p>
 * <b>Example (Bukkit/Paper plugin):</b>
 * <pre>{@code
 * Properties props = PluginPropertyLoader.fromPluginDataFolder(
 *     getDataFolder(), "database", "hibernate.properties");
 * // -> plugins/MyPlugin/database/hibernate.properties
 * }</pre>
 *
 * @since 4.0
 */
public final class PluginPropertyLoader {

    private PluginPropertyLoader() {
    }

    /**
     * Loads properties from a file resolved inside a plugin's data folder.
     *
     * @param dataFolder   the plugin data folder (e.g. {@code plugin.getDataFolder()})
     * @param subPathParts path segments relative to {@code dataFolder}
     * @return the loaded properties
     */
    public static Properties fromPluginDataFolder(File dataFolder, String... subPathParts) {
        return PropertyLoader.load(dataFolder, subPathParts);
    }

    /**
     * Loads properties from a plugin data folder, returning empty if the file is absent.
     *
     * @param dataFolder   the plugin data folder
     * @param subPathParts path segments relative to {@code dataFolder}
     * @return the loaded properties, or empty if not found
     */
    public static Properties fromPluginDataFolderOrEmpty(File dataFolder, String... subPathParts) {
        return PropertyLoader.loadOrEmpty(dataFolder, subPathParts);
    }
}
