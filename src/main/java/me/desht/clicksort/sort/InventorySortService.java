package me.desht.clicksort.sort;

/*
 * This file is part of ClickSort
 *
 * ClickSort is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * ClickSort is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with ClickSort. If not, see <http://www.gnu.org/licenses/>.
 */

import me.desht.clicksort.ClickSortPlugin;
import me.desht.clicksort.SortingMethod;
import me.desht.clicksort.events.InventorySortEvent;
import me.desht.dhutils.Log;
import me.desht.dhutils.MessageUtil;
import me.desht.dhutils.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * Handles target-inventory resolution, permission checks, the {@link InventorySortEvent}
 * lifecycle, item write-back, overflow dropping, and viewer refresh. Delegates the pure
 * sort/merge algorithm to {@link SortEngine}.
 */
public class InventorySortService {

    private final ClickSortPlugin plugin;

    public InventorySortService(ClickSortPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @return true if the clicked inventory in this event is one that should be sorted
     */
    public boolean isSortableTarget(InventoryClickEvent event) {
        return shouldSort(viewToClickedInventory(event.getView(), event.getRawSlot()));
    }

    /**
     * Perform a sort on the inventory targeted by the click event.
     *
     * @return true if the sort completed and the caller should cancel the originating event
     */
    public boolean sortInventory(final InventoryClickEvent event, final SortingMethod sortMethod) {
        Player p = (Player) event.getWhoClicked();
        int rawSlot = event.getRawSlot();
        int slot = event.getView().convertSlot(rawSlot);

        Inventory inv;
        if (slot == rawSlot) {
            // upper inv was clicked
            inv = event.getView().getTopInventory();
            if (slot >= inv.getSize()) {
                // is this a Bukkit bug? clicking a player inventory when the
                // crafting or dispenser view is up
                // seems to give rawSlot==localSlot, implying the upper
                // inventory (crafting/dispenser) has been clicked
                // when in fact the lower inventory (player) was clicked
                inv = event.getView().getBottomInventory();
            }
        } else {
            // lower inv was clicked
            inv = event.getView().getBottomInventory();
        }

        Log.debug("clicked inventory window " + inv.getType() + ", slot " + slot);
        int min, max; // slot range to sort
        InventoryType type = inv.getType();
        if (type == InventoryType.PLAYER) {
            if (slot < 9) {
                // hotbar
                if (!Permissions.isAllowedTo(p, "clicksort.sort.hotbar")) {
                    return false;
                }
                min = 0;
                max = 9;
            } else {
                if (!Permissions.isAllowedTo(p, "clicksort.sort.player")) {
                    return false;
                }
                // main player inventory
                min = plugin.getConfig().getInt("player_sort_min");
                // don't sort equipments and off-hand
                max = plugin.getConfig().getInt("player_sort_max");
            }
        } else if (plugin.getConfigManager().main().getSortableInventories().contains(type)) {
            if (!Permissions.isAllowedTo(p, "clicksort.sort.container")) {
                return false;
            }
            min = inv.getHolder() instanceof AbstractHorse ? 2 : 0;
            max = inv.getSize();
        } else {
            return false;
        }

        InventorySortEvent sortEvent = new InventorySortEvent(event.getView(), inv, min, max);
        Bukkit.getPluginManager().callEvent(sortEvent);
        if (sortEvent.isCancelled()) {
            return false;
        }

        Set<Integer> sortableSlots = sortEvent.getSortableSlots();
        List<ItemStack> sortedItems = SortEngine.sortAndMerge(inv.getContents(), sortableSlots, sortMethod);

        if (sortableSlots.size() < sortedItems.size() && !plugin.getConfig().getBoolean("drop_excess")) {
            MessageUtil.errorMessage(p, plugin.getConfigManager().lang().getColoredMessage("invOverFlow"));
            return false;
        }

        for (int i : sortableSlots) {
            if (!sortedItems.isEmpty()) {
                ItemStack newItem = sortedItems.remove(0);
                inv.setItem(i, newItem);
            } else {
                inv.clear(i);
            }
        }

        if (!sortedItems.isEmpty()) {
            // This *shouldn't* happen, but there is a possibility if some other plugin has been messing
            // with max stack sizes, and we end up with an overflowing inventory after merging stacks.
            MessageUtil.alertMessage(p, plugin.getConfigManager().lang().getColoredMessage("dropItems"));
            for (ItemStack item : sortedItems) {
                Log.debug("dropping " + item + " by player " + p.getName());
                p.getWorld().dropItemNaturally(p.getLocation(), item);
            }
        }

        for (HumanEntity he : event.getViewers()) {
            if (he instanceof Player viewer) {
                viewer.updateInventory();
            }
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Target-inventory helpers
    // -------------------------------------------------------------------------

    private Inventory viewToClickedInventory(InventoryView view, int rawSlot) {
        return rawSlot < 0 ? null
                : (rawSlot < view.getTopInventory().getSize() ? view.getTopInventory() : view.getBottomInventory());
    }

    private boolean shouldSort(Inventory clickedInventory) {
        return clickedInventory != null && !shouldIgnore(clickedInventory)
                && plugin.getConfigManager().main().getSortableInventories().contains(clickedInventory.getType());
    }

    private boolean shouldIgnore(Inventory inventory) {
        return plugin.getConfig().getBoolean("ignore_plugin_inventory") && !isVanillaInventoryHolder(inventory.getHolder());
    }

    private static boolean isVanillaInventoryHolder(InventoryHolder inventoryHolder) {
        return inventoryHolder != null && inventoryHolder.getClass().getPackageName().startsWith("org.bukkit.");
    }
}
