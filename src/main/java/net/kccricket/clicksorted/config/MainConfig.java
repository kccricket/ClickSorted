package net.kccricket.clicksorted.config;

import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kccricket.clicksorted.logging.DebugLevel;
import net.kccricket.clicksorted.logging.Log;
import net.kccricket.clicksorted.model.ClickMethod;
import net.kccricket.clicksorted.model.SortingMethod;
import org.bukkit.event.inventory.InventoryType;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Wraps {@code config.yml} via Bukkit's built-in {@code JavaPlugin} config machinery.
 * <p>
 * On every {@link #load()} / {@link #reload()} the file is guaranteed to exist on disk:
 * defaults are applied and {@code saveConfig()} is called before the config is read.
 * This closes the bug where deleting {@code config.yml} mid-session and running
 * {@code /clicksorted reload} left the file absent.
 */
public class MainConfig implements ManagedConfig {

    private final ClickSortedPlugin plugin;
    private List<InventoryType> sortableInventories = List.of();

    public MainConfig(ClickSortedPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String fileName() {
        return "config.yml";
    }

    @Override
    public void load() {
        // On first load the config is already in memory (from disk or from MockBukkit in tests).
        // Just ensure defaults are applied and the file is written, then parse the values.
        applyDefaults();
        plugin.saveConfig();
        applyToRuntime();
    }

    @Override
    public void reload() {
        // On reload, discard the in-memory config and re-read from disk first.
        // If the file was deleted mid-session, reloadConfig() loads an empty config; saveConfig()
        // then recreates the file with bundled defaults (via copyDefaults applied in applyDefaults).
        plugin.reloadConfig();
        load();
    }

    private void applyDefaults() {
        plugin.getConfig().options().setHeader(
                List.of("See https://github.com/kccricket/clicksorted"));
        plugin.getConfig().options().copyDefaults(true);
    }

    private void applyToRuntime() {
        Log.setDebugLevel(DebugLevel.parse(plugin.getConfig().getString("debug_level"), DebugLevel.OFF));

        sortableInventories = plugin.getConfig().getStringList("sortable_inventories").stream()
                .map(s -> {
                    try {
                        return InventoryType.valueOf(s);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public List<InventoryType> getSortableInventories() {
        return sortableInventories;
    }

    public SortingMethod getDefaultSortingMethod() {
        return SortingMethod.parse(plugin.getConfig().getString("defaults.sort_mode"), SortingMethod.DEFAULT);
    }

    public ClickMethod getDefaultClickMethod() {
        return ClickMethod.parse(plugin.getConfig().getString("defaults.click_mode"), ClickMethod.DEFAULT);
    }

    public boolean getDefaultShiftClick() {
        return plugin.getConfig().getBoolean("defaults.shift_click");
    }
}
