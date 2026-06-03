package me.desht.clicksort;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

public class LanguageLoader {
    private static final String FILE_NAME = "lang.yml";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static ClickSortPlugin plugin;
    private static File configFile;
    private static FileConfiguration config;

    public static void init(ClickSortPlugin plugin) {
        LanguageLoader.plugin = plugin;
        configFile = new File(plugin.getDataFolder(), FILE_NAME);
        saveDefault();
        load();
    }

    public static void reload() {
        init(plugin);
    }

    public static void saveDefault() {
        if (!configFile.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
    }

    public static void load() {
        config = YamlConfiguration.loadConfiguration(configFile);
        InputStream defaultConfigStream = plugin.getResource(FILE_NAME);
        if (defaultConfigStream != null) {
            InputStreamReader configReader = new InputStreamReader(defaultConfigStream);
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(configReader);
            config.setDefaults(defaultConfig);
        }
    }

    public static String getMessage(String path) {
        return config.getString(path);
    }

    public static String getMessage(String path, String def) {
        String msg = config.getString(path);
        return msg != null ? msg : def;
    }

    public static Component getColoredMessage(String path, TagResolver... resolvers) {
        return MM.deserialize(getMessage(path), resolvers);
    }
}
