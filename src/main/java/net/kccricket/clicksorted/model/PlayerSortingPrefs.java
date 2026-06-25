package net.kccricket.clicksorted.model;

/*
 This file is part of ClickSorted

 ClickSorted is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 ClickSorted is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with ClickSorted.  If not, see <http://www.gnu.org/licenses/>.
 */

import net.kccricket.clicksorted.ClickSortedPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class PlayerSortingPrefs {
    private final ClickSortedPlugin plugin;
    private final NamespacedKey sortKey;
    private final NamespacedKey clickKey;
    private final NamespacedKey enabledKey;
    private final NamespacedKey sortOverItemsKey;
    private final NamespacedKey lockedSlotsKey;
    private final NamespacedKey bundleInventoryKey;
    private final NamespacedKey bundleOthersKey;
    private final NamespacedKey bundleStackLimitKey;
    private final NamespacedKey bundleBlacklistKey;
    private final NamespacedKey bundleBlacklistNamesKey;
    private final NamespacedKey startCornerKey;
    private final NamespacedKey fillAxisKey;

    public PlayerSortingPrefs(ClickSortedPlugin plugin) {
        this.plugin = plugin;
        this.sortKey = new NamespacedKey(plugin, "sort_mode");
        this.clickKey = new NamespacedKey(plugin, "click_mode");
        this.enabledKey = new NamespacedKey(plugin, "enabled");
        this.sortOverItemsKey = new NamespacedKey(plugin, "sort_over_items");
        this.lockedSlotsKey = new NamespacedKey(plugin, "locked_slots");
        this.bundleInventoryKey = new NamespacedKey(plugin, "bundle_inventory");
        this.bundleOthersKey = new NamespacedKey(plugin, "bundle_others");
        this.bundleStackLimitKey = new NamespacedKey(plugin, "bundle_stack_limit");
        this.bundleBlacklistKey = new NamespacedKey(plugin, "bundle_blacklist");
        this.bundleBlacklistNamesKey = new NamespacedKey(plugin, "bundle_blacklist_names");
        this.startCornerKey = new NamespacedKey(plugin, "start_corner");
        this.fillAxisKey = new NamespacedKey(plugin, "fill_axis");
    }

    public SortingMethod getSortingMethod(Player player) {
        String stored = player.getPersistentDataContainer().get(sortKey, PersistentDataType.STRING);
        return SortingMethod.parse(stored, plugin.getConfigManager().main().getDefaultSortingMethod());
    }

    public void setSortingMethod(Player player, SortingMethod sortMethod) {
        player.getPersistentDataContainer().set(sortKey, PersistentDataType.STRING, sortMethod.name());
    }

    /**
     * Returns true if click-sorting is enabled for this player.
     * Falls back to the server default when the player has no stored preference.
     */
    public boolean getEnabled(Player player) {
        return getBool(player, enabledKey, plugin.getConfigManager().main()::getDefaultEnabled);
    }

    public void setEnabled(Player player, boolean enabled) {
        setBool(player, enabledKey, enabled);
    }

    public ClickMethod getClickMethod(Player player) {
        String stored = player.getPersistentDataContainer().get(clickKey, PersistentDataType.STRING);
        return ClickMethod.parse(stored, plugin.getConfigManager().main().getDefaultClickMethod());
    }

    public void setClickMethod(Player player, ClickMethod clickMethod) {
        player.getPersistentDataContainer().set(clickKey, PersistentDataType.STRING, clickMethod.name());
    }

    public StartCorner getStartCorner(Player player) {
        String stored = player.getPersistentDataContainer().get(startCornerKey, PersistentDataType.STRING);
        return StartCorner.parse(stored, plugin.getConfigManager().main().getDefaultStartCorner());
    }

    public void setStartCorner(Player player, StartCorner corner) {
        player.getPersistentDataContainer().set(startCornerKey, PersistentDataType.STRING, corner.name());
    }

    public FillAxis getFillAxis(Player player) {
        String stored = player.getPersistentDataContainer().get(fillAxisKey, PersistentDataType.STRING);
        return FillAxis.parse(stored, plugin.getConfigManager().main().getDefaultFillAxis());
    }

    public void setFillAxis(Player player, FillAxis axis) {
        player.getPersistentDataContainer().set(fillAxisKey, PersistentDataType.STRING, axis.name());
    }

    /**
     * Returns true if sorting fires even while the player is hovering an occupied slot.
     * Falls back to the server default when the player has no stored preference.
     */
    public boolean getSortOverItems(Player player) {
        return getBool(player, sortOverItemsKey, plugin.getConfigManager().main()::getDefaultSortOverItems);
    }

    public void setSortOverItems(Player player, boolean enabled) {
        setBool(player, sortOverItemsKey, enabled);
    }

    /**
     * Returns true if bundle packing is enabled for the player's own inventory.
     * Falls back to the server default when the player has no stored preference.
     */
    public boolean getBundlePackInventory(Player player) {
        return getBool(player, bundleInventoryKey, plugin.getConfigManager().main()::getDefaultBundlePackInventory);
    }

    public void setBundlePackInventory(Player player, boolean enabled) {
        setBool(player, bundleInventoryKey, enabled);
    }

    /**
     * Returns true if bundle packing is enabled for other (container) inventories the player sorts.
     * Falls back to the server default when the player has no stored preference.
     */
    public boolean getBundlePackOthers(Player player) {
        return getBool(player, bundleOthersKey, plugin.getConfigManager().main()::getDefaultBundlePackOthers);
    }

    public void setBundlePackOthers(Player player, boolean enabled) {
        setBool(player, bundleOthersKey, enabled);
    }

    /** Reads a boolean preference stored as a byte, falling back to {@code def} when unset. */
    private static boolean getBool(Player player, NamespacedKey key, java.util.function.BooleanSupplier def) {
        Byte stored = player.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return stored != null ? stored != 0 : def.getAsBoolean();
    }

    /** Writes a boolean preference as a byte. */
    private static void setBool(Player player, NamespacedKey key, boolean enabled) {
        player.getPersistentDataContainer().set(key, PersistentDataType.BYTE, enabled ? (byte) 1 : (byte) 0);
    }

    /**
     * Returns the per-player bundle stack limit (max distinct entries per bundle); 0 means weight-only.
     * Falls back to the server default when the player has no stored preference.
     */
    public int getBundleStackLimit(Player player) {
        Integer stored = player.getPersistentDataContainer().get(bundleStackLimitKey, PersistentDataType.INTEGER);
        return stored != null ? stored : plugin.getConfigManager().main().getDefaultBundleStackLimit();
    }

    public void setBundleStackLimit(Player player, int limit) {
        player.getPersistentDataContainer().set(bundleStackLimitKey, PersistentDataType.INTEGER, Math.max(0, limit));
    }

    /**
     * Returns the player's bundle blacklist: materials that will not be packed into or unpacked from
     * bundles during sorting. Unknown tokens (e.g. from a removed material) are silently dropped.
     * Returns an empty, unmodifiable set when no blacklist has been stored.
     */
    public Set<Material> getBundleBlacklist(Player player) {
        String stored = player.getPersistentDataContainer().get(bundleBlacklistKey, PersistentDataType.STRING);
        if (stored == null || stored.isBlank()) {
            return Set.of();
        }
        Set<Material> result = EnumSet.noneOf(Material.class);
        for (String token : stored.split(",")) {
            Material mat = Material.matchMaterial(token);
            if (mat != null) {
                result.add(mat);
            }
        }
        return result.isEmpty() ? Set.of() : Set.copyOf(result);
    }

    /**
     * Adds a material to the player's bundle blacklist.
     *
     * @return {@code true} if the material was newly added, {@code false} if it was already present
     */
    public boolean addToBundleBlacklist(Player player, Material material) {
        Set<Material> current = new HashSet<>(getBundleBlacklist(player));
        boolean added = current.add(material);
        if (added) {
            setBundleBlacklist(player, current);
        }
        return added;
    }

    /**
     * Removes a material from the player's bundle blacklist.
     *
     * @return {@code true} if the material was removed, {@code false} if it was not present
     */
    public boolean removeFromBundleBlacklist(Player player, Material material) {
        Set<Material> current = new HashSet<>(getBundleBlacklist(player));
        boolean removed = current.remove(material);
        if (removed) {
            setBundleBlacklist(player, current);
        }
        return removed;
    }

    /** Clears all entries (materials and display names) from the player's bundle blacklist. */
    public void clearBundleBlacklist(Player player) {
        player.getPersistentDataContainer().remove(bundleBlacklistKey);
        player.getPersistentDataContainer().remove(bundleBlacklistNamesKey);
    }

    private void setBundleBlacklist(Player player, Set<Material> materials) {
        if (materials.isEmpty()) {
            player.getPersistentDataContainer().remove(bundleBlacklistKey);
        } else {
            String value = materials.stream().map(Material::name).collect(Collectors.joining(","));
            player.getPersistentDataContainer().set(bundleBlacklistKey, PersistentDataType.STRING, value);
        }
    }

    /**
     * Returns the player's display-name bundle blacklist: plain-text display names that will not be
     * packed into or unpacked from bundles during sorting. Blank tokens are silently dropped.
     * Returns an empty, unmodifiable set when no name blacklist has been stored.
     */
    public Set<String> getBundleBlacklistNames(Player player) {
        String stored = player.getPersistentDataContainer().get(bundleBlacklistNamesKey, PersistentDataType.STRING);
        if (stored == null || stored.isBlank()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : stored.split("\n")) {
            if (!token.isBlank()) {
                result.add(token);
            }
        }
        return result.isEmpty() ? Set.of() : Set.copyOf(result);
    }

    /**
     * Adds a display name to the player's bundle name blacklist.
     *
     * @return {@code true} if the name was newly added, {@code false} if it was already present
     */
    public boolean addToBundleBlacklistName(Player player, String name) {
        Set<String> current = new LinkedHashSet<>(getBundleBlacklistNames(player));
        boolean added = current.add(name);
        if (added) {
            setBundleBlacklistNames(player, current);
        }
        return added;
    }

    /**
     * Removes a display name from the player's bundle name blacklist.
     *
     * @return {@code true} if the name was removed, {@code false} if it was not present
     */
    public boolean removeFromBundleBlacklistName(Player player, String name) {
        Set<String> current = new LinkedHashSet<>(getBundleBlacklistNames(player));
        boolean removed = current.remove(name);
        if (removed) {
            setBundleBlacklistNames(player, current);
        }
        return removed;
    }

    private void setBundleBlacklistNames(Player player, Set<String> names) {
        if (names.isEmpty()) {
            player.getPersistentDataContainer().remove(bundleBlacklistNamesKey);
        } else {
            String value = String.join("\n", names);
            player.getPersistentDataContainer().set(bundleBlacklistNamesKey, PersistentDataType.STRING, value);
        }
    }

    public Set<Integer> getLockedSlots(Player player) {
        int[] stored = player.getPersistentDataContainer().get(lockedSlotsKey, PersistentDataType.INTEGER_ARRAY);
        if (stored == null || stored.length == 0) {
            return Set.of();
        }
        return Arrays.stream(stored).boxed().collect(Collectors.toUnmodifiableSet());
    }

    public boolean toggleSlotLocked(Player player, int slot) {
        Set<Integer> slots = new HashSet<>(getLockedSlots(player));
        boolean nowLocked = slots.add(slot);
        if (!nowLocked) slots.remove(slot);
        setLockedSlots(player, slots);
        return nowLocked;
    }

    public void setLockedSlots(Player player, Set<Integer> slots) {
        if (slots.isEmpty()) {
            player.getPersistentDataContainer().remove(lockedSlotsKey);
        } else {
            player.getPersistentDataContainer().set(lockedSlotsKey, PersistentDataType.INTEGER_ARRAY,
                    slots.stream().mapToInt(Integer::intValue).toArray());
        }
    }

}
