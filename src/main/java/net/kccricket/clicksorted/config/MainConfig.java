package net.kccricket.clicksorted.config;

import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kccricket.clicksorted.logging.DebugLevel;
import net.kccricket.clicksorted.logging.Log;
import net.kccricket.clicksorted.model.ClickMethod;
import net.kccricket.clicksorted.model.FillAxis;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.model.StartCorner;
import org.bukkit.event.inventory.InventoryType;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Wraps {@code config.yml} via Bukkit's built-in {@code JavaPlugin} config machinery.
 * <p>
 * On every {@link #load()} / {@link #reload()} the file is guaranteed to exist on disk:
 * defaults are applied and {@code saveConfig()} is called before the config is read.
 * This closes the bug where deleting {@code config.yml} mid-session and running
 * {@code /clicksorted reload} left the file absent.
 */
public class MainConfig implements ManagedConfig {

    /** One past the last sortable player slot: slots 36+ are armor and off-hand, never sorted. */
    private static final int PLAYER_STORAGE_END = 36;

    private final ClickSortedPlugin plugin;
    // Reassigned on reload; read on Folia region threads, so publish via volatile.
    // EnumSet for O(1) membership tests on the per-click sort path.
    private volatile Set<InventoryType> sortableInventories = Set.of();

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
        normalizeValues();
        plugin.getMigrations().migrate(plugin.getConfig());
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
        cfg.setComments("check_for_updates", List.of(
                "Check Modrinth for a newer release on startup and reload, logging a notice to the",
                "console when one is available. Set to false to disable. No data beyond the request",
                "itself is sent."));
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
                "Values: SWAP (press the swap-offhand key over a slot), SINGLE_CLICK (left-click an empty slot),",
                "        DOUBLE_CLICK (double-click), CONTROL_DROP (Ctrl+Q over a slot),",
                "        SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK, NONE (click-sorting disabled)"));
        cfg.setComments("defaults.sort_mode", List.of(
                "Algorithm used to order and lay out items.",
                "Values: NAME (alphabetical by display name), GROUP (by group defined in groups.yml),",
                "        TREEMAP (group by type and lay each type out as a proportional block sized to its",
                "        stack count, packed to fill the container with empty space pooled in one corner;",
                "        the most-numerous type anchors start_corner. Ignores fill_axis).",
                "GROUP requires at least one group to be configured in groups.yml."));
        cfg.setComments("defaults.start_corner", List.of(
                "The grid corner where a sorted layout begins.",
                "Values: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT.",
                "Players can change this with /clicksorted set start-corner <corner>."));
        cfg.setComments("defaults.fill_axis", List.of(
                "The direction a sorted layout flows from the start corner (NAME/GROUP sort modes only).",
                "Values: HORIZONTAL (fill rows), VERTICAL (fill columns).",
                "Players can change this with /clicksorted set fill-axis <axis>."));
        cfg.setComments("defaults.sort_over_items", List.of(
                "When true, the configured click method sorts even while hovering an occupied slot;",
                "when false, sorting only fires on an empty slot.",
                "Players can toggle this per-session with /clicksorted hover."));
        cfg.setComments("defaults.bundle_inventory", List.of(
                "When true, sorting a player's own inventory also packs partial stacks into any bundles",
                "present there. Players can toggle this with /clicksorted bundle inventory on|off."));
        cfg.setComments("defaults.bundle_others", List.of(
                "When true, sorting a container (chest, barrel, …) also packs partial stacks into any",
                "bundles it holds. Players can toggle this with /clicksorted bundle others on|off."));
        cfg.setComments("defaults.bundle_stack_limit", List.of(
                "Default max number of distinct item entries per bundle when packing.",
                "12 = tooltip-preview limit (bundles show the 12 most-recently-added items).",
                "0 disables the entry limit (weight-only limit applies instead).",
                "Players can change this with /clicksorted bundle stacklimit <n|off>."));
        cfg.setComments("player_sort_min", List.of(
                "First inventory slot included when sorting a player's main inventory (inclusive).",
                "Slot 9 is the first row of main storage (slots 0-8 are the hotbar)."));
        cfg.setComments("player_sort_max", List.of(
                "One past the last inventory slot included when sorting a player's main inventory (exclusive).",
                "Slot 35 is the last main-storage slot, so 36 sorts all of main storage;",
                "slots 36+ are armor and off-hand. Values are clamped to the 0..36 range."));
        cfg.setComments("action_cooldown_ms", List.of(
                "Minimum milliseconds between successive ClickSorted actions per player",
                "(sorting, bundle-packing, in-inventory mode cycling, lock-GUI toggles, commands).",
                "Caps how fast a scripted client can spam these; players with the",
                "clicksorted.throttle.bypass permission (default op) are exempt.",
                "Lower values feel snappier but may clip rapid legitimate lock-GUI clicking.",
                "Set to 0 to disable throttling entirely."));
        cfg.setComments("sortable_inventories", List.of(
                "Inventory types that players are allowed to sort.",
                "Values must be valid Bukkit InventoryType names (case-sensitive).",
                "See https://jd.papermc.io/paper/1.21.5/org/bukkit/event/inventory/InventoryType.html",
                "Unrecognized names are silently ignored."));
    }

    private void normalizeValues() {
        // YAML 1.1 parses unquoted OFF as boolean false; silently correct it so the saved file is valid.
        if (plugin.getConfig().isBoolean("debug_level") && !plugin.getConfig().getBoolean("debug_level")) {
            plugin.getConfig().set("debug_level", "OFF");
        }
    }

    private void applyToRuntime() {
        Log.setDebugLevel(DebugLevel.parse(plugin.getConfig().getString("debug_level"), DebugLevel.OFF));

        Set<InventoryType> parsed = EnumSet.noneOf(InventoryType.class);
        for (String s : plugin.getConfig().getStringList("sortable_inventories")) {
            try {
                parsed.add(InventoryType.valueOf(s));
            } catch (IllegalArgumentException ignored) {
                // unrecognized type name — silently skipped (documented in config comments)
            }
        }
        sortableInventories = parsed;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public Set<InventoryType> getSortableInventories() {
        return sortableInventories;
    }

    public boolean getCheckForUpdates() {
        return plugin.getConfig().getBoolean("check_for_updates", true);
    }

    public SortingMethod getDefaultSortingMethod() {
        return SortingMethod.parse(plugin.getConfig().getString("defaults.sort_mode"), SortingMethod.DEFAULT);
    }

    public ClickMethod getDefaultClickMethod() {
        return ClickMethod.parse(plugin.getConfig().getString("defaults.click_mode"), ClickMethod.DEFAULT);
    }

    public StartCorner getDefaultStartCorner() {
        return StartCorner.parse(plugin.getConfig().getString("defaults.start_corner"), StartCorner.DEFAULT);
    }

    public FillAxis getDefaultFillAxis() {
        return FillAxis.parse(plugin.getConfig().getString("defaults.fill_axis"), FillAxis.DEFAULT);
    }

    public boolean getDefaultSortOverItems() {
        return plugin.getConfig().getBoolean("defaults.sort_over_items");
    }

    public boolean getDefaultBundlePackInventory() {
        return plugin.getConfig().getBoolean("defaults.bundle_inventory");
    }

    public boolean getDefaultBundlePackOthers() {
        return plugin.getConfig().getBoolean("defaults.bundle_others");
    }

    /**
     * Default max distinct entries per bundle when packing (0 = weight-only limit).
     */
    public int getDefaultBundleStackLimit() {
        return plugin.getConfig().getInt("defaults.bundle_stack_limit");
    }

    public int getPlayerSortMin() {
        return Math.max(0, Math.min(plugin.getConfig().getInt("player_sort_min"), PLAYER_STORAGE_END));
    }

    /**
     * Minimum milliseconds between successive per-player actions ({@code action_cooldown_ms}).
     * A value ≤ 0 disables the {@link net.kccricket.clicksorted.security.ActionThrottle}.
     */
    public int getActionCooldownMs() {
        return plugin.getConfig().getInt("action_cooldown_ms", 150);
    }

    public int getPlayerSortMax() {
        return Math.max(0, Math.min(plugin.getConfig().getInt("player_sort_max"), PLAYER_STORAGE_END));
    }

    /** Returns true if the given player inventory slot falls within the sortable range. */
    public boolean isPlayerSlotSortable(int invSlot) {
        if (invSlot < 0) return false;
        if (invSlot < 9) return true; // hotbar: Bukkit slots 0-8
        return invSlot >= getPlayerSortMin() && invSlot < getPlayerSortMax();
    }
}
