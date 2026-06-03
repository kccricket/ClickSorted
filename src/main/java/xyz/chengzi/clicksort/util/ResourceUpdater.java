package xyz.chengzi.clicksort.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class ResourceUpdater {

    private ResourceUpdater() {}

    /**
     * Add-only merge of a bundled resource into the plugin data folder.
     * Keys already present in the user file are never modified or removed.
     * Keys present in the bundled default but absent from the user file are added and saved to disk.
     */
    public static YamlConfiguration update(Plugin plugin, String resourceName) {
        File userFile = new File(plugin.getDataFolder(), resourceName);

        if (!userFile.exists()) {
            plugin.saveResource(resourceName, false);
        }

        YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(userFile);

        InputStream defaultStream = plugin.getResource(resourceName);
        if (defaultStream == null) {
            return userConfig;
        }

        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
        userConfig.setDefaults(defaultConfig);
        userConfig.options().copyDefaults(true);

        try {
            userConfig.save(userFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save updated " + resourceName + ": " + e.getMessage());
        }

        return userConfig;
    }
}
