package net.kccricket.clicksort.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Manages {@code lang.yml}: all user-facing messages, rendered via MiniMessage.
 */
public class LangConfig implements ManagedConfig {

    private static final String FILE_NAME = "lang.yml";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private FileConfiguration config;

    public LangConfig(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String fileName() {
        return FILE_NAME;
    }

    @Override
    public void load() {
        config = ResourceUpdater.update(plugin, FILE_NAME);
    }

    public String getMessage(String path) {
        return config.getString(path);
    }

    public String getMessage(String path, String def) {
        String msg = config.getString(path);
        return msg != null ? msg : def;
    }

    public Component getColoredMessage(String path, TagResolver... resolvers) {
        return MM.deserialize(getMessage(path), resolvers);
    }
}
