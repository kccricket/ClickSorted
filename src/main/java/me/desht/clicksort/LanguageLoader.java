package me.desht.clicksort;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import xyz.chengzi.clicksort.util.ResourceUpdater;

public class LanguageLoader {
    private static final String FILE_NAME = "lang.yml";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static ClickSortPlugin plugin;
    private static FileConfiguration config;

    public static void init(ClickSortPlugin plugin) {
        LanguageLoader.plugin = plugin;
        load();
    }

    public static void reload() {
        init(plugin);
    }

    public static void load() {
        config = ResourceUpdater.update(plugin, FILE_NAME);
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
