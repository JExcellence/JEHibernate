// examples/spigot-plugin-demo — unchanged 3.x-style plugin API on the 4.0 core.
// Not part of the Gradle build; copy into your plugin.
package com.example.plugin;

import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.plugin.PluginPropertyLoader;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Properties;

public class DemoPlugin extends JavaPlugin {

    private JEHibernate jeHibernate;

    @Override
    public void onEnable() {
        // Plugin convenience now lives in jehibernate-plugin; the rest is the familiar API.
        Properties props = PluginPropertyLoader.fromPluginDataFolder(
            getDataFolder(), "database", "hibernate.properties");

        jeHibernate = JEHibernate.builder()
            .configuration(config -> config.fromProperties(props))
            .scanPackages("com.example.plugin.entities", "com.example.plugin.repositories")
            .build();

        var userRepo = jeHibernate.repositories().get(UserRepository.class);
        getLogger().info("Loaded " + userRepo.count() + " users");
    }

    @Override
    public void onDisable() {
        if (jeHibernate != null) {
            jeHibernate.close();  // closes the HikariCP pool cleanly on reload
        }
    }
}
