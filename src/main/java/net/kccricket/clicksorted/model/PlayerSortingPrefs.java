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

import java.util.HashSet;
import java.util.Set;

public class PlayerSortingPrefs {
    private final ClickSortedPlugin plugin;
    private final NamespacedKey sortKey;
    private final NamespacedKey clickKey;
    private final NamespacedKey shiftClickKey;
    private final NamespacedKey lockedSlotsKey;

    public PlayerSortingPrefs(ClickSortedPlugin plugin) {
        this.plugin = plugin;
        this.sortKey = new NamespacedKey(plugin, "sort");
        this.clickKey = new NamespacedKey(plugin, "click");
        this.shiftClickKey = new NamespacedKey(plugin, "shift_click");
        this.lockedSlotsKey = new NamespacedKey(plugin, "locked_slots");
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

    public Set<Integer> getLockedSlots(Player player) {
        int[] stored = player.getPersistentDataContainer().get(lockedSlotsKey, PersistentDataType.INTEGER_ARRAY);
        if (stored == null) {
            return new HashSet<>();
        }
        Set<Integer> result = new HashSet<>(stored.length);
        for (int slot : stored) {
            result.add(slot);
        }
        return result;
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

    public void setSlotLocked(Player player, int slot, boolean locked) {
        Set<Integer> slots = getLockedSlots(player);
        if (locked) {
            slots.add(slot);
        } else {
            slots.remove(slot);
        }
        setLockedSlots(player, slots);
    }

}
