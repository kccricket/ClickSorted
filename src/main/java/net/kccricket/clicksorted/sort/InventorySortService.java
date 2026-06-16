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
import net.kccricket.clicksorted.model.FillAxis;
import net.kccricket.clicksorted.model.SortKey;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.model.StartCorner;
import net.kccricket.clicksorted.security.Permissions;
import net.kccricket.clicksorted.text.MessageUtil;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        if (!event.getCursor().isEmpty()) {
            // Prevent sorting when the player is holding an item with the cursor, to avoid accidental sorts and potential dupes.
            return false;
        }

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
        boolean playerMainStorage = false; // packing applies here (not the hotbar)
        boolean container = false;
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
                playerMainStorage = true;
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
            container = true;
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

        var prefs = plugin.getSortingPrefs();
        boolean packEnabled = (playerMainStorage && prefs.getBundlePackInventory(p))
                || (container && prefs.getBundlePackOthers(p));
        List<ItemStack> sortedItems = packEnabled
                ? packAndSort(inv, sortableSlots, sortMethod, prefs.getBundleStackLimit(p))
                : SortEngine.sortAndMerge(inv.getContents(), sortableSlots, sortMethod);

        if (sortableSlots.size() < sortedItems.size() && !plugin.getConfig().getBoolean("drop_excess")) {
            MessageUtil.errorMessage(p, plugin.getConfigManager().lang().getColoredMessage("invOverFlow"));
            return false;
        }

        int width = SlotOrder.widthFor(type);
        int rows = Math.max(1, (max - min + width - 1) / width);
        List<ItemStack> overflow = sortMethod.isTreemap()
                ? writeTreemap(inv, sortableSlots, sortedItems, min, width, rows, prefs.getStartCorner(p))
                : writeLinear(inv, sortableSlots, sortedItems, min, width, prefs.getStartCorner(p), prefs.getFillAxis(p));

        if (!overflow.isEmpty()) {
            // This *shouldn't* happen, but there is a possibility if some other plugin has been messing
            // with max stack sizes, and we end up with an overflowing inventory after merging stacks.
            MessageUtil.alertMessage(p, plugin.getConfigManager().lang().getColoredMessage("dropItems"));
            for (ItemStack item : overflow) {
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
     * Writes the sorted sequence linearly: order the slots by start-corner/fill-axis, then write the
     * i-th sorted stack to the i-th slot, clearing any slot past the end of the sequence.
     *
     * @return the stacks that did not fit (to be dropped); empty in the normal case
     */
    private List<ItemStack> writeLinear(Inventory inv, Set<Integer> sortableSlots, List<ItemStack> sortedItems,
                                        int base, int width, StartCorner startCorner, FillAxis fillAxis) {
        List<Integer> fillOrder = SlotOrder.order(sortableSlots, base, width, startCorner, fillAxis);
        for (int i : fillOrder) {
            if (!sortedItems.isEmpty()) {
                inv.setItem(i, sortedItems.remove(0));
            } else {
                inv.clear(i);
            }
        }
        return sortedItems;
    }

    /**
     * Writes the {@code TREEMAP} placement: {@link TreemapPacker} lays each item type out as a
     * proportional block packed to fill the container; we write the resulting slot→stack map and
     * clear every other sortable slot.
     *
     * @return the stacks that did not fit (to be dropped); empty in the normal case
     */
    private List<ItemStack> writeTreemap(Inventory inv, Set<Integer> sortableSlots, List<ItemStack> sortedItems,
                                         int base, int width, int rows, StartCorner startCorner) {
        Map<Integer, ItemStack> placement = TreemapPacker.pack(sortedItems, sortableSlots, base, width, rows, startCorner);
        Set<ItemStack> placed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i : sortableSlots) {
            ItemStack item = placement.get(i);
            if (item != null) {
                inv.setItem(i, item);
                placed.add(item);
            } else {
                inv.clear(i);
            }
        }
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack item : sortedItems) {
            if (!placed.contains(item)) {
                overflow.add(item);
            }
        }
        return overflow;
    }

    /**
     * The unified pack-and-sort step: pool the eligible loose items across {@code sortableSlots}, pack
     * their bundleable remainders into the bundles in that same region (mutated in place), then sort
     * the leftover loose stacks together with the bundles and any ineligible items.
     *
     * @return the sorted, stack-merged list ready to be written back into {@code sortableSlots}
     */
    private List<ItemStack> packAndSort(Inventory inv, Set<Integer> sortableSlots,
                                        SortingMethod sortMethod, int stackLimit) {
        Map<SortKey, Long> loosePool = new LinkedHashMap<>();
        Map<SortKey, ItemStack> samples = new LinkedHashMap<>();
        List<ItemStack> bundles = new ArrayList<>();       // bins (mutated by the packer)
        List<ItemStack> toSort = new ArrayList<>();         // ineligible passthrough + leftovers + bundles

        ItemStack[] contents = inv.getContents();
        for (int slot : sortableSlots) {
            ItemStack is = contents[slot];
            if (is == null) {
                continue;
            }
            if (is.getType() == Material.BUNDLE) {
                bundles.add(is.clone());
            } else if (BundlePacker.canBundle(is)) {
                SortKey key = new SortKey(is, SortingMethod.NAME);
                loosePool.merge(key, (long) is.getAmount(), (a, b) -> Long.sum(a, b));
                samples.putIfAbsent(key, is);
            } else {
                toSort.add(is.clone());
            }
        }

        List<ItemStack> leftover = BundlePacker.packIntoBundles(loosePool, samples, bundles, stackLimit);
        toSort.addAll(leftover);
        toSort.addAll(bundles);

        return SortEngine.sortAndMerge(toSort, sortMethod);
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
