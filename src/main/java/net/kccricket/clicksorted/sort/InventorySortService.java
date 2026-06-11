package net.kccricket.clicksorted.sort;

/*
 * This file is part of ClickSorted
 *
 * ClickSorted is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * ClickSorted is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with ClickSorted. If not, see <http://www.gnu.org/licenses/>.
 */

import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kccricket.clicksorted.events.InventorySortEvent;
import net.kccricket.clicksorted.logging.Log;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.security.Permissions;
import net.kccricket.clicksorted.text.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Handles target-inventory resolution, permission checks, the {@link InventorySortEvent}
 * lifecycle, item write-back, overflow dropping, and viewer refresh. Delegates the pure
 * sort/merge algorithm to {@link SortEngine}.
 */
public class InventorySortService {

    private final ClickSortedPlugin plugin;

    public InventorySortService(ClickSortedPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @return true if the clicked inventory in this event is one that should be sorted
     */
    public boolean isSortableTarget(InventoryClickEvent event) {
        return shouldSort(event.getClickedInventory());
    }

    /**
     * Perform a sort on the inventory targeted by the click event.
     *
     * @return true if the sort completed and the caller should cancel the originating event
     */
    public boolean sortInventory(final InventoryClickEvent event, final SortingMethod sortMethod) {
        Player p = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        Inventory inv = event.getClickedInventory();
        if (inv == null) {
            return false;
        }

        Log.debug("clicked inventory window " + inv.getType() + ", slot " + slot);
        int min, max; // slot range to sort
        InventoryType type = inv.getType();
        var mainCfg = plugin.getConfigManager().main();
        if (type == InventoryType.PLAYER) {
            if (slot < 9) {
                // hotbar
                if (!Permissions.isAllowedTo(p, "clicksorted.sort.hotbar")) {
                    return false;
                }
                min = 0;
                max = 9;
            } else if (slot < mainCfg.getPlayerSortMax()) {
                if (!Permissions.isAllowedTo(p, "clicksorted.sort.player")) {
                    return false;
                }
                // main player inventory
                min = mainCfg.getPlayerSortMin();
                max = mainCfg.getPlayerSortMax();
            } else {
                // armor / offhand slots — never sort
                return false;
            }
        } else if (plugin.getConfigManager().main().getSortableInventories().contains(type)) {
            if (!Permissions.isAllowedTo(p, "clicksorted.sort.container")) {
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
        if (type == InventoryType.PLAYER) {
            for (int locked : plugin.getSortingPrefs().getLockedSlots(p)) {
                sortEvent.excludeSlot(locked);
            }
        }
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

    /**
     * Sort the player's main inventory and then pack partial stacks into bundles.
     * Operates on the main-storage slot range from the server config, excluding locked slots.
     *
     * <p>This is the back-end for {@code /clicksorted bundle}. It does not require a click event.
     *
     * @param player     the player whose inventory to sort and pack
     * @param sortMethod the ordering strategy
     * @param entryCap   maximum distinct entries per bundle; ≤ 0 means weight-only limit
     * @return number of partial stacks absorbed into bundles, or -1 if the sort was aborted
     */
    public int sortAndPack(Player player, SortingMethod sortMethod, int entryCap) {
        if (!Permissions.isAllowedTo(player, "clicksorted.sort.player")) {
            return -1;
        }

        var mainCfg = plugin.getConfigManager().main();
        int min = mainCfg.getPlayerSortMin();
        int max = mainCfg.getPlayerSortMax();

        Set<Integer> sortableSlots = new TreeSet<>();
        for (int i = min; i < max; i++) {
            sortableSlots.add(i);
        }
        for (int locked : plugin.getSortingPrefs().getLockedSlots(player)) {
            sortableSlots.remove(locked);
        }

        var inv = player.getInventory();
        List<ItemStack> sortedItems = SortEngine.sortAndMerge(inv.getContents(), sortableSlots, sortMethod);

        // Run bundle-packing before the overflow check — packing can only reduce the list size
        int beforePack = sortedItems.size();
        sortedItems = BundlePacker.pack(sortedItems, entryCap);
        int packed = beforePack - sortedItems.size();

        if (sortableSlots.size() < sortedItems.size() && !plugin.getConfig().getBoolean("drop_excess")) {
            MessageUtil.errorMessage(player, plugin.getConfigManager().lang().getColoredMessage("invOverFlow"));
            return -1;
        }

        for (int i : sortableSlots) {
            if (!sortedItems.isEmpty()) {
                inv.setItem(i, sortedItems.remove(0));
            } else {
                inv.clear(i);
            }
        }

        if (!sortedItems.isEmpty()) {
            MessageUtil.alertMessage(player, plugin.getConfigManager().lang().getColoredMessage("dropItems"));
            for (ItemStack item : sortedItems) {
                Log.debug("dropping " + item + " by player " + player.getName());
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }

        player.updateInventory();
        return packed;
    }

    // -------------------------------------------------------------------------
    // Target-inventory helpers
    // -------------------------------------------------------------------------

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
