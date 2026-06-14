package net.kccricket.clicksorted.config;

import net.kccricket.clicksorted.logging.Log;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Manages {@code items.yml}: a persistent store of material-name → display-name mappings.
 * Lookups for unmapped materials fall back to the material name itself.
 */
public class ItemsConfig implements ManagedConfig {

    private static final String FILE_NAME = "items.yml";

    private final Plugin plugin;
    // Reassigned on reload; read by getItemName on Folia region threads, so publish via volatile.
    private volatile FileConfiguration config;
    private File file;

    public ItemsConfig(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String fileName() {
        return FILE_NAME;
    }

    @Override
    public void load() {
        file = new File(plugin.getDataFolder(), FILE_NAME);
        config = ResourceUpdater.update(plugin, FILE_NAME);
    }

    @Override
    public void save() {
        if (config != null && file != null) {
            try {
                config.save(file);
            } catch (IOException e) {
                Log.warning("Failed to save " + FILE_NAME + ": " + e.getMessage(), e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Item name lookups
    // -------------------------------------------------------------------------

    public String getItemName(ItemStack i) {
        if (i.hasItemMeta() && i.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(i.getItemMeta().displayName());
        }
        return getItemName(getItemType(i));
    }

    public String getItemName(String iname) {
        if (config == null) {
            return iname;
        }
        return config.getString(iname, iname);
    }

    public String getItemType(ItemStack i) {
        return i.getType().name().toUpperCase(Locale.ROOT);
    }
}
