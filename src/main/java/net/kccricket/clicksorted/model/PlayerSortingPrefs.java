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
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PlayerSortingPrefs {
    private final ClickSortedPlugin plugin;
    private final NamespacedKey sortKey;
    private final NamespacedKey clickKey;
    private final NamespacedKey shiftClickKey;
    private final NamespacedKey lockedSlotsKey;
    private final NamespacedKey bundleInventoryKey;
    private final NamespacedKey bundleOthersKey;
    private final NamespacedKey bundleStackLimitKey;

    public PlayerSortingPrefs(ClickSortedPlugin plugin) {
        this.plugin = plugin;
        this.sortKey = new NamespacedKey(plugin, "sort");
        this.clickKey = new NamespacedKey(plugin, "click");
        this.shiftClickKey = new NamespacedKey(plugin, "shift_click");
        this.lockedSlotsKey = new NamespacedKey(plugin, "locked_slots");
        this.bundleInventoryKey = new NamespacedKey(plugin, "bundle_inventory");
        this.bundleOthersKey = new NamespacedKey(plugin, "bundle_others");
        this.bundleStackLimitKey = new NamespacedKey(plugin, "bundle_stack_limit");
    }

    public SortingMethod getSortingMethod(Player player) {
        String stored = player.getPersistentDataContainer().get(sortKey, PersistentDataType.STRING);
        return stored != null ? SortingMethod.parse(stored) : plugin.getConfigManager().main().getDefaultSortingMethod();
    }

    public void setSortingMethod(Player player, SortingMethod sortMethod) {
        player.getPersistentDataContainer().set(sortKey, PersistentDataType.STRING, sortMethod.name());
    }

    public ClickMethod getClickMethod(Player player) {
        String stored = player.getPersistentDataContainer().get(clickKey, PersistentDataType.STRING);
        return stored != null ? ClickMethod.parse(stored) : plugin.getConfigManager().main().getDefaultClickMethod();
    }

    public void setClickMethod(Player player, ClickMethod clickMethod) {
        player.getPersistentDataContainer().set(clickKey, PersistentDataType.STRING, clickMethod.name());
    }

    public boolean getShiftClickAllowed(Player player) {
        Byte stored = player.getPersistentDataContainer().get(shiftClickKey, PersistentDataType.BYTE);
        return stored != null ? stored != 0 : plugin.getConfigManager().main().getDefaultShiftClick();
    }

    public void setShiftClickAllowed(Player player, boolean allow) {
        player.getPersistentDataContainer().set(shiftClickKey, PersistentDataType.BYTE, allow ? (byte) 1 : (byte) 0);
    }

    /**
     * Returns true if bundle packing is enabled for the player's own inventory.
     * Falls back to the server default when the player has no stored preference.
     */
    public boolean getBundlePackInventory(Player player) {
        Byte stored = player.getPersistentDataContainer().get(bundleInventoryKey, PersistentDataType.BYTE);
        return stored != null ? stored != 0 : plugin.getConfigManager().main().getDefaultBundlePackInventory();
    }

    public void setBundlePackInventory(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(bundleInventoryKey, PersistentDataType.BYTE, enabled ? (byte) 1 : (byte) 0);
    }

    /**
     * Returns true if bundle packing is enabled for other (container) inventories the player sorts.
     * Falls back to the server default when the player has no stored preference.
     */
    public boolean getBundlePackOthers(Player player) {
        Byte stored = player.getPersistentDataContainer().get(bundleOthersKey, PersistentDataType.BYTE);
        return stored != null ? stored != 0 : plugin.getConfigManager().main().getDefaultBundlePackOthers();
    }

    public void setBundlePackOthers(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(bundleOthersKey, PersistentDataType.BYTE, enabled ? (byte) 1 : (byte) 0);
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

    public Set<Integer> getLockedSlots(Player player) {
        int[] stored = player.getPersistentDataContainer().get(lockedSlotsKey, PersistentDataType.INTEGER_ARRAY);
        if (stored == null || stored.length == 0) {
            return Set.of();
        }
        Set<Integer> result = new HashSet<>(stored.length);
        for (int slot : stored) {
            result.add(slot);
        }
        return Collections.unmodifiableSet(result);
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

    public boolean isSlotLocked(Player player, int slot) {
        return getLockedSlots(player).contains(slot);
    }


}
