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
        plugin.getConfig().options().setHeader(List.of(
                "ClickSorted configuration",
                "See https://github.com/kccricket/ClickSorted for full documentation."));
        plugin.getConfig().options().copyDefaults(true);
        applyComments();
    }

    private void applyComments() {
        var cfg = plugin.getConfig();
        cfg.setComments("enable_metrics", List.of(
                "Send anonymous usage statistics to bStats (https://bstats.org).",
                "Set to false to opt out."));
        cfg.setComments("debug_level", List.of(
                "Logging verbosity for the plugin.",
                "Values: OFF (no debug output), DEBUG (high-level flow), TRACE (per-item verbose)"));
        cfg.setComments("drop_excess", List.of(
                "What to do when merging stacks produces more items than fit in the sortable slots.",
                "true  = overflow items are dropped on the ground.",
                "false = the sort is aborted and the player sees the invOverFlow message."));
        cfg.setComments("default_group_name", List.of(
                "Fallback group name (from groups.yml) assigned to any item not explicitly listed there.",
                "Groups sort alphabetically, so the numeric prefix controls where unlisted items land."));
        cfg.setComments("ignore_plugin_inventory", List.of(
                "When true, only vanilla container inventories are sortable; custom plugin GUIs are skipped.",
                "When false (default), any inventory whose type appears in sortable_inventories can be sorted."));
        cfg.setComments("defaults", List.of(
                "Default preferences applied to new players (or any player whose PDC entry is missing)."));
        cfg.setComments("defaults.click_mode", List.of(
                "How a player triggers a sort.",
                "Values: SWAP (press the swap-offhand key over a slot), SINGLE (left-click an empty slot),",
                "        DOUBLE (double-click), NONE (click-sorting disabled)"));
        cfg.setComments("defaults.sort_mode", List.of(
                "Algorithm used to order items.",
                "Values: NAME (alphabetical by display name), GROUP (by group defined in groups.yml).",
                "GROUP requires at least one group to be configured in groups.yml."));
        cfg.setComments("defaults.shift_click", List.of(
                "When true, shift-clicking an empty slot cycles through sort/click modes.",
                "Players can toggle this per-session with /clicksorted shiftclick."));
        cfg.setComments("player_sort_min", List.of(
                "First inventory slot included when sorting a player's main inventory (inclusive).",
                "Slot 9 is the first row of main storage (slots 0-8 are the hotbar)."));
        cfg.setComments("player_sort_max", List.of(
                "Last inventory slot included when sorting a player's main inventory (inclusive).",
                "Slot 35 is the last main-storage slot; slots 36+ are armor and off-hand."));
        cfg.setComments("sortable_inventories", List.of(
                "Inventory types that players are allowed to sort.",
                "Values must be valid Bukkit InventoryType names (case-sensitive).",
                "See https://jd.papermc.io/paper/1.21.5/org/bukkit/event/inventory/InventoryType.html",
                "Unrecognized names are silently ignored."));
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
