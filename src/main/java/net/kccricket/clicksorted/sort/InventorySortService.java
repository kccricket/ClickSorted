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
import net.kccricket.clicksorted.config.MainConfig;
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
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
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
        // No cursor-state guard here: the only cursor-empty requirement belongs to SINGLE_CLICK (so a
        // held item can still be placed), and ClickMethod.matchesSortTrigger already enforces that before
        // we are ever called. Other methods may sort with a held cursor item — the event is cancelled and
        // the cursor stack is left untouched.
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
            } else if (slot < MainConfig.PLAYER_STORAGE_END) {
                if (!Permissions.isAllowedTo(p, "clicksorted.sort.player")) {
                    return false;
                }
                // main player inventory
                min = 9;
                max = MainConfig.PLAYER_STORAGE_END;
                playerMainStorage = true;
            } else {
                // armor / offhand slots — never sort
                return false;
            }
        } else if (mainCfg.getSortableInventories().contains(type)) {
            if (!Permissions.isAllowedTo(p, "clicksorted.sort.container")) {
                return false;
            }
            min = GridGeometry.storageOffset(inv.getHolder());
            max = inv.getSize();
            container = true;
        } else {
            return false;
        }

        // DOUBLE_CLICK gesture repair: the first click of the double-click already lifted the clicked
        // stack onto the cursor and emptied the slot; the listener cancels the event to suppress the
        // vanilla gather, which would otherwise strand that stack on the cursor. Put it back into its
        // origin slot (only when that slot is empty — the expected post-first-click state) so the sort
        // below folds it in and the cursor ends empty. Done here, past the permission/target checks, so
        // it never fires for a click that wouldn't actually sort.
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            ItemStack cursor = event.getCursor();
            ItemStack atSlot = inv.getItem(slot);
            if (cursor != null && cursor.getType() != Material.AIR
                    && (atSlot == null || atSlot.getType() == Material.AIR)) {
                inv.setItem(slot, cursor.clone());
                p.setItemOnCursor(null);
            }
        }

        InventorySortEvent sortEvent = new InventorySortEvent(event.getView(), inv, min, max);
        Bukkit.getPluginManager().callEvent(sortEvent);
        if (sortEvent.isCancelled()) {
            return false;
        }

        Set<Integer> sortableSlots = sortEvent.getSortableSlots();
        if (type == InventoryType.PLAYER) {
            // Per-player locked slots (player-controlled via /clicksorted set lock).
            for (int locked : plugin.getSortingPrefs().getLockedSlots(p)) {
                sortEvent.excludeSlot(locked);
            }
            // Admin-enforced slot locks (config locked_slots.player and clicksorted.lock.player.slot.N).
            ProtectedSlots protectedSlots = ProtectedSlots.forSort(p, mainCfg);
            if (!protectedSlots.isEmpty()) {
                for (int s : List.copyOf(sortableSlots)) {
                    if (protectedSlots.blocks(s)) {
                        sortEvent.excludeSlot(s);
                    }
                }
            }
        }

        // Exclude slots whose items are on the admin-enforced "do not touch" blacklist.
        // Applies to player and container inventories alike. Uses the same excludeSlot mechanism as
        // locked slots: excluded slots are never read, sorted, packed, or overwritten.
        ProtectedItems protectedItems = ProtectedItems.forSort(p, mainCfg);
        if (!protectedItems.isEmpty()) {
            ItemStack[] slotContents = inv.getContents();
            for (int s : List.copyOf(sortableSlots)) {
                ItemStack is = slotContents[s];
                if (is != null && is.getType() != Material.AIR && protectedItems.blocks(is)) {
                    sortEvent.excludeSlot(s);
                }
            }
        }

        var prefs = plugin.getSortingPrefs();
        boolean packEnabled =
                (playerMainStorage && prefs.getBundlePackInInventory(p)
                        && Permissions.isAllowedTo(p, "clicksorted.bundle.inventory"))
                || (container && prefs.getBundlePackInContainers(p)
                        && Permissions.isAllowedTo(p, "clicksorted.bundle.container"));
        BundleBlacklist blacklist = packEnabled
                ? new BundleBlacklist(prefs.getBundleBlacklist(p), prefs.getBundleBlacklistNames(p))
                : BundleBlacklist.EMPTY;
        List<ItemStack> sortedItems = packEnabled
                ? packAndSort(inv, sortableSlots, sortMethod, prefs.getBundleStackLimit(p), blacklist)
                : SortEngine.sortAndMerge(inv.getContents(), sortableSlots, sortMethod);

        if (sortableSlots.size() < sortedItems.size() && !plugin.getConfig().getBoolean("drop_excess")) {
            MessageUtil.errorMessage(p, plugin.getConfigManager().lang().getColoredMessage("invOverFlow"));
            return false;
        }

        GridGeometry grid = GridGeometry.of(type, inv.getHolder(), min, max);
        List<ItemStack> overflow = sortMethod.isTreemap()
                ? writeTreemap(inv, sortableSlots, sortedItems, grid.base(), grid.width(), grid.rows(), prefs.getStartCorner(p), prefs.getFillAxis(p))
                : writeLinear(inv, sortableSlots, sortedItems, grid.base(), grid.width(), prefs.getStartCorner(p), prefs.getFillAxis(p));

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
        int next = 0;
        for (int i : fillOrder) {
            if (next < sortedItems.size()) {
                inv.setItem(i, sortedItems.get(next++));
            } else {
                inv.clear(i);
            }
        }
        return next < sortedItems.size() ? new ArrayList<>(sortedItems.subList(next, sortedItems.size())) : List.of();
    }

    /**
     * Writes the {@code TREEMAP} placement: {@link TreemapPacker} lays each item type out as a
     * proportional block packed to fill the container; we write the resulting slot→stack map and
     * clear every other sortable slot.
     *
     * @return the stacks that did not fit (to be dropped); empty in the normal case
     */
    private List<ItemStack> writeTreemap(Inventory inv, Set<Integer> sortableSlots, List<ItemStack> sortedItems,
                                         int base, int width, int rows, StartCorner startCorner, FillAxis fillAxis) {
        Map<Integer, ItemStack> placement = TreemapPacker.pack(sortedItems, sortableSlots, base, width, rows, startCorner, fillAxis);
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
                                        SortingMethod sortMethod, int stackLimit,
                                        BundleBlacklist blacklist) {
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
            if (BundlePacker.isBundle(is.getType())) {
                bundles.add(is.clone());
            } else if (BundlePacker.canBundle(is, blacklist)) {
                SortKey key = SortKey.poolKey(is);
                // Lambda, not Long::sum: a method ref binds the boxed map values straight to
                // primitive params, tripping JDT's "needs unchecked conversion" null warning.
                loosePool.merge(key, (long) is.getAmount(), (a, b) -> Long.sum(a, b));
                samples.putIfAbsent(key, is);
            } else {
                toSort.add(is.clone());
            }
        }

        List<ItemStack> leftover = BundlePacker.packIntoBundles(loosePool, samples, bundles, stackLimit, blacklist);
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
