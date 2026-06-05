package net.kccricket.clicksort.config;

import net.kccricket.clicksort.logging.Log;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Manages {@code items.yml}: a persistent store of material-name → display-name mappings.
 * Unknown item names encountered at runtime are added to the in-memory config and flushed
 * to disk on {@link #save()} (called during {@code onDisable}).
 */
public class ItemsConfig implements ManagedConfig {

    private static final String FILE_NAME = "items.yml";

    private final Plugin plugin;
    private FileConfiguration config;
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

    public String getItemFullName(ItemStack i) {
        String name = getItemName(getItemType(i));
        if (i.hasItemMeta() && i.getItemMeta().hasDisplayName()) {
            return name + " (" + PlainTextComponentSerializer.plainText().serialize(
                    i.getItemMeta().displayName()) + ")";
        }
        return name;
    }

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
        String aname = config.getString(iname);
        if (aname == null) {
            aname = iname;
            config.set(iname, iname);
        }
        return aname;
    }

    public String getItemType(ItemStack i) {
        return i.getType().name().toUpperCase(Locale.ROOT);
    }
}
