package net.kccricket.clicksort.model;

/*
 This file is part of ClickSort

 ClickSort is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 ClickSort is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with ClickSort.  If not, see <http://www.gnu.org/licenses/>.
 */

import net.kccricket.clicksort.ClickSortPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public class PlayerSortingPrefs {
    private final ClickSortPlugin plugin;
    private final NamespacedKey sortKey;
    private final NamespacedKey clickKey;
    private final NamespacedKey shiftClickKey;

    public PlayerSortingPrefs(ClickSortPlugin plugin) {
        this.plugin = plugin;
        this.sortKey = new NamespacedKey(plugin, "sort");
        this.clickKey = new NamespacedKey(plugin, "click");
        this.shiftClickKey = new NamespacedKey(plugin, "shift_click");
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

}
