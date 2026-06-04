package me.desht.clicksort.config;

import me.desht.clicksort.ClickMethod;
import me.desht.clicksort.SortingMethod;
import me.desht.dhutils.Debugger;
import me.desht.dhutils.MiscUtil;
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
 * {@code /clicksort reload} left the file absent.
 */
public class MainConfig implements ManagedConfig {

    private final me.desht.clicksort.ClickSortPlugin plugin;
    private List<InventoryType> sortableInventories = List.of();

    public MainConfig(me.desht.clicksort.ClickSortPlugin plugin) {
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
                List.of("See https://dev.bukkit.org/projects/clicksort/pages/configuration"));
        plugin.getConfig().options().copyDefaults(true);
        // Remove superseded / legacy keys so they do not accumulate in the file
        plugin.getConfig().set("log_level", null);
        plugin.getConfig().set("autosave_seconds", null);
    }

    private void applyToRuntime() {
        MiscUtil.setColouredConsole(plugin.getConfig().getBoolean("coloured_console"));
        Debugger.getInstance().setLevel(plugin.getConfig().getInt("debug_level"));

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
