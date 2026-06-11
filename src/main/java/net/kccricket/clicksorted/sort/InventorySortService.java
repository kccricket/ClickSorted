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
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
     * Combine same-item stacks in the player's main inventory and pack the remaining partials into
     * existing bundles, without sorting. The current item order is preserved.
     *
     * <p>This is the back-end for the Ctrl+Q-on-bundle shortcut.
     *
     * @param player   the player whose inventory to pack
     * @param entryCap maximum distinct entries per bundle; ≤ 0 means weight-only limit
     * @return number of inventory slots freed (by combining and bundling), or -1 if denied
     */
    public int packOnly(Player player, int entryCap) {
        if (!Permissions.isAllowedTo(player, "clicksorted.sort.player")) {
            return -1;
        }

        Set<Integer> sortableSlots = playerSortableSlots(player);
        Set<Integer> locked = plugin.getSortingPrefs().getLockedSlots(player);
        var inv = player.getInventory();

        // Build a slot-aligned working copy (index ↔ slot), so positions are preserved: the
        // largest stack of each type keeps its slot and bundles stay put, rather than reflowing.
        // Locked slots are dropped by playerSortableSlots, so they are never read or written.
        List<Integer> slots = new java.util.ArrayList<>(sortableSlots);
        List<ItemStack> items = new java.util.ArrayList<>(slots.size());
        for (int slot : slots) {
            ItemStack item = inv.getItem(slot);
            items.add(item == null ? null : item.clone());
        }

        // Hotbar bundles (slots 0–8) are valid bins, but hotbar *items* are never touched. A bundle
        // in a locked hotbar slot is skipped.
        List<Integer> hotbarSlots = new java.util.ArrayList<>();
        List<ItemStack> hotbarBundles = new java.util.ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            if (locked.contains(slot)) continue;
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() == Material.BUNDLE) {
                hotbarSlots.add(slot);
                hotbarBundles.add(item.clone());
            }
        }

        // Dissolve every eligible item into a pool and repack from scratch (pure, idempotent).
        int freed = BundlePacker.repack(items, hotbarBundles, entryCap);

        // Write each main slot and each hotbar-bundle slot back from the mutated copies; emptied
        // slots are cleared. Refresh the viewer only if something actually changed.
        boolean changed = writeBack(inv, slots, items);
        changed |= writeBack(inv, hotbarSlots, hotbarBundles);

        if (changed) {
            player.updateInventory();
        }
        return freed;
    }

    /**
     * Write a slot-aligned working copy back to the inventory, clearing emptied slots. Returns true
     * if any slot changed.
     */
    private boolean writeBack(org.bukkit.inventory.PlayerInventory inv, List<Integer> slots, List<ItemStack> items) {
        boolean changed = false;
        for (int idx = 0; idx < slots.size(); idx++) {
            int slot = slots.get(idx);
            ItemStack before = inv.getItem(slot);
            ItemStack after = items.get(idx);
            if (after == null) {
                if (before != null) {
                    inv.clear(slot);
                    changed = true;
                }
            } else if (!after.equals(before)) {
                inv.setItem(slot, after);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Pack the player's bundles using their current entry-cap preference, then send the
     * feedback message. Shared entry point for {@code /clicksorted bundle} and the
     * Ctrl+Q-on-bundle shortcut so both stay in lockstep.
     */
    public void packBundles(Player player) {
        int entryCap = plugin.getSortingPrefs().getBundleCapEnabled(player)
                ? plugin.getConfigManager().main().getBundleEntryCap() : 0;
        int packed = packOnly(player, entryCap);
        if (packed >= 0) {
            MessageUtil.statusMessage(player,
                    plugin.getConfigManager().lang().getColoredMessage("bundlePacked",
                            Placeholder.unparsed("count", String.valueOf(packed))));
        }
    }

    // -------------------------------------------------------------------------
    // Target-inventory helpers
    // -------------------------------------------------------------------------

    /**
     * The player's main-storage slot set (config range minus their locked slots).
     */
    private Set<Integer> playerSortableSlots(Player player) {
        var mainCfg = plugin.getConfigManager().main();
        Set<Integer> slots = new TreeSet<>();
        for (int i = mainCfg.getPlayerSortMin(); i < mainCfg.getPlayerSortMax(); i++) {
            slots.add(i);
        }
        for (int locked : plugin.getSortingPrefs().getLockedSlots(player)) {
            slots.remove(locked);
        }
        return slots;
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
