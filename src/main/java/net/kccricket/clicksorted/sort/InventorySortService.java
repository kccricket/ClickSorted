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
import net.kccricket.kcmclib.logging.Log;
import net.kccricket.clicksorted.model.FillAxis;
import net.kccricket.clicksorted.model.PlayerSortingPrefs;
import net.kccricket.clicksorted.model.SortKey;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.model.StartCorner;
import net.kccricket.clicksorted.security.Permissions;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles target-inventory resolution, permission checks, the {@link InventorySortEvent}
 * lifecycle, item write-back, overflow dropping, and viewer refresh. Delegates the pure
 * sort/merge algorithm to {@link SortEngine} and in-place consolidation to {@link InPlacePacker}.
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>Sorting on</b> – the full sort/pack pipeline: merge, optional bundle packing, sort,
 *       re-layout. Items may move to any sortable slot.</li>
 *   <li><b>Sorting off, packing on</b> – in-place consolidation via {@link InPlacePacker}:
 *       same-material stacks consolidate within their own slots; bundle-eligible remainders pack
 *       into the bundles already in the inventory. Items displaced from bundles or exceeding lane
 *       capacity fill free/freed slots; drops occur only when the region is genuinely full.</li>
 * </ul>
 *
 * <p>When both modes are disabled for the player and target, this service returns {@code false}
 * immediately (a complete no-op). The caller ({@link InventoryClickListener}) pre-screens via
 * {@code checkWork} to avoid unnecessarily entering the throttle/cancel path.
 */
public class InventorySortService {

    // -------------------------------------------------------------------------
    // Region — which inventory zone was clicked
    // -------------------------------------------------------------------------

    /**
     * The logical region within an inventory that determines which permissions apply and whether
     * bundle packing is allowed.
     */
    private enum Region {
        /** Hotbar slots (0–8) of the player inventory. */
        HOTBAR,
        /** Main storage slots (9–{@link MainConfig#PLAYER_STORAGE_END}) of the player inventory. */
        PLAYER_MAIN,
        /** A sortable container (chest, barrel, shulker box, etc.). */
        CONTAINER
    }

    /**
     * Resolved target for a click event: the inventory, its type, the sortable slot range, and
     * which {@link Region} the click landed in.
     */
    record Target(Inventory inv, InventoryType type, int min, int max, Region region) {}

    // -------------------------------------------------------------------------

    private final ClickSortedPlugin plugin;

    public InventorySortService(ClickSortedPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Resolves the click target and checks whether there is work to do for this player.
     * Returns the resolved {@link Target} if at least one of sorting or bundle packing is enabled
     * and permitted, or {@code null} if the click should be ignored entirely.
     *
     * <p>The returned target is passed directly to {@link #sortInventory} to avoid re-resolving.
     */
    Target checkWork(InventoryClickEvent event, Player player) {
        Target target = resolve(event);
        if (target == null) return null;
        var prefs = plugin.getSortingPrefs();
        return (prefs.getEnabled(player) && sortAllowed(target.region(), player))
                || packAllowed(target.region(), player, prefs) ? target : null;
    }

    /**
     * Perform a sort (or in-place consolidation) on the inventory targeted by the click event.
     * The {@code target} must be the value returned by a prior {@link #checkWork} call for the
     * same event.
     *
     * @return true if the operation completed and the caller should cancel the originating event
     */
    public boolean sortInventory(Target target, final InventoryClickEvent event, final SortingMethod sortMethod) {
        // No cursor-state guard here: the only cursor-empty requirement belongs to SINGLE_CLICK (so a
        // held item can still be placed), and ClickMethod.matchesSortTrigger already enforces that before
        // we are ever called. Other methods may sort with a held cursor item — the event is cancelled and
        // the cursor stack is left untouched.
        Player p = (Player) event.getWhoClicked();

        Log.debug("clicked inventory window " + target.type() + ", slot " + event.getSlot());

        var prefs = plugin.getSortingPrefs();
        boolean sortEnabled = prefs.getEnabled(p) && sortAllowed(target.region(), p);
        boolean packEnabled = packAllowed(target.region(), p, prefs);
        if (!sortEnabled && !packEnabled) {
            return false;
        }

        Inventory inv = target.inv();
        int slot = event.getSlot();

        // DOUBLE_CLICK gesture repair: a double-click on an occupied slot has vanilla lift that
        // slot's stack onto the cursor before the event we see fires, leaving the slot empty; the
        // listener cancels the event to suppress the vanilla gather, which would otherwise strand
        // that stack on the cursor. Put it back into its origin slot (only when that slot is empty —
        // the expected post-lift state) so the sort below folds it in and the cursor ends empty. A
        // double-click on an already-empty slot arrives with an empty cursor and no lifted stack, so
        // this is a no-op there — no pickup step is required to trigger a sort. Done here, past the
        // permission/target checks, so it never fires for a click that wouldn't actually sort.
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            ItemStack cursor = event.getCursor();
            ItemStack atSlot = inv.getItem(slot);
            if (cursor != null && cursor.getType() != Material.AIR
                    && (atSlot == null || atSlot.getType() == Material.AIR)) {
                inv.setItem(slot, cursor.clone());
                p.setItemOnCursor(null);
            }
        }

        var mainCfg = plugin.getConfigManager().main();
        Set<Integer> regionSlots = InventorySortEvent.rangeSet(target.min(), target.max());
        Set<Integer> userLockedSlots = Set.of();
        Set<Integer> adminLockedSlots = Set.of();
        if (target.type() == InventoryType.PLAYER) {
            // Per-player locked slots (player-controlled via /clicksorted set lock).
            userLockedSlots = prefs.getLockedSlots(p);
            // Admin-enforced slot locks (config locked_slots.player and clicksorted.lock.player.slot.N).
            ProtectedSlots protectedSlots = ProtectedSlots.forSort(p, mainCfg);
            if (!protectedSlots.isEmpty()) {
                adminLockedSlots = regionSlots.stream().filter(protectedSlots::blocks).collect(Collectors.toUnmodifiableSet());
            }
        }
        ProtectedItems protectedItems = ProtectedItems.forSort(p, mainCfg);

        InventorySortEvent sortEvent = new InventorySortEvent(event.getView(), inv, regionSlots,
                userLockedSlots, adminLockedSlots, protectedItems);
        Bukkit.getPluginManager().callEvent(sortEvent);
        if (sortEvent.isCancelled()) {
            // Unlike PlayerPreferenceChangeEvent, this fires on every matching click, so there's no
            // generic "blocked" fallback here — a per-click message with no listener-supplied reason
            // would just be noise. A reason, if set, is still worth showing, but rate-limited so a
            // cancelling listener can't spam chat on rapid clicking.
            var reason = sortEvent.getCancelReason();
            if (reason != null) {
                plugin.messages().to(p).status().throttle("sortCancelReason", 3).send(reason);
            }
            return false;
        }

        Set<Integer> sortableSlots = sortEvent.getSortableSlots();

        // Exclude slots whose items match the admin "do not touch" blacklist or any exclusion a
        // listener added via excludeItem. Applies to player and container inventories alike.
        // Uses the same excludeSlot mechanism as locks: excluded slots are never read, sorted,
        // packed, or overwritten.
        ItemStack[] slotContents = inv.getContents();
        for (int s : List.copyOf(sortableSlots)) {
            ItemStack is = slotContents[s];
            if (is != null && is.getType() != Material.AIR && sortEvent.matchesExcludedItem(is)) {
                sortEvent.excludeSlot(s);
            }
        }

        if (sortEnabled) {
            return sortWithLayout(event, p, inv, target, sortableSlots, sortMethod, packEnabled, prefs);
        } else {
            return consolidateInPlace(p, inv, sortableSlots, prefs);
        }
    }

    // -------------------------------------------------------------------------
    // Sort-with-layout path (sorting on)
    // -------------------------------------------------------------------------

    /**
     * The full sort/pack path: items are merged, optionally packed into bundles, sorted, and
     * written back into the inventory according to start-corner / fill-axis preferences.
     */
    private boolean sortWithLayout(InventoryClickEvent event, Player p, Inventory inv,
                                   Target target, Set<Integer> sortableSlots, SortingMethod sortMethod,
                                   boolean packEnabled, PlayerSortingPrefs prefs) {
        BundleBlacklist blacklist = packEnabled
                ? new BundleBlacklist(new MaterialNameSet(prefs.getBundleBlacklist(p),
                        prefs.getBundleBlacklistNames(p).stream()
                                .map(n -> n.toLowerCase(Locale.ROOT))
                                .collect(Collectors.toUnmodifiableSet())))
                : BundleBlacklist.EMPTY;
        List<ItemStack> sortedItems = packEnabled
                ? packAndSort(inv, sortableSlots, sortMethod, prefs.getBundleStackLimit(p), blacklist)
                : SortEngine.sortAndMerge(inv.getContents(), sortableSlots, sortMethod);

        if (sortableSlots.size() < sortedItems.size() && !plugin.getConfig().getBoolean("drop_excess")) {
            plugin.messages().to(p).error().send("invOverFlow");
            return false;
        }

        GridGeometry grid = GridGeometry.of(target.type(), inv.getHolder(), target.min(), target.max());
        List<ItemStack> overflow = sortMethod.isTreemap()
                ? writeTreemap(inv, sortableSlots, sortedItems, grid.base(), grid.width(), grid.rows(), prefs.getStartCorner(p), prefs.getFillAxis(p))
                : writeLinear(inv, sortableSlots, sortedItems, grid.base(), grid.width(), prefs.getStartCorner(p), prefs.getFillAxis(p));

        dropOverflow(p, overflow);
        refreshViewers(event.getViewers());
        return true;
    }

    // -------------------------------------------------------------------------
    // In-place consolidation path (sorting off, packing on)
    // -------------------------------------------------------------------------

    /**
     * The in-place path: consolidates same-material stacks within their existing slots and
     * packs eligible remainders into bundles already in the inventory. Items displaced from
     * bundles or exceeding lane capacity fill free/freed slots; drops only when region is full.
     */
    private boolean consolidateInPlace(Player p, Inventory inv, Set<Integer> sortableSlots,
                                       PlayerSortingPrefs prefs) {
        BundleBlacklist blacklist = new BundleBlacklist(new MaterialNameSet(prefs.getBundleBlacklist(p),
                prefs.getBundleBlacklistNames(p).stream()
                        .map(n -> n.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet())));
        InPlacePacker.Result result = InPlacePacker.consolidate(
                inv.getContents(), sortableSlots, true, prefs.getBundleStackLimit(p), blacklist);

        for (int s : sortableSlots) {
            ItemStack it = result.placement().get(s);
            if (it != null) {
                inv.setItem(s, it);
            } else {
                inv.clear(s);
            }
        }

        dropOverflow(p, result.overflow());
        refreshViewers(inv.getViewers());
        return true;
    }

    // -------------------------------------------------------------------------
    // Target resolution and permission helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the clicked inventory and click slot to a {@link Target}, or returns {@code null}
     * when the click is not in a sortable region (armor/offhand, non-sortable type, etc.).
     *
     * <p>This method does <em>not</em> check sort or bundle permissions — those are the caller's
     * responsibility via {@link #sortAllowed} and {@link #packAllowed}.
     */
    private Target resolve(InventoryClickEvent event) {
        Inventory inv = event.getClickedInventory();
        if (inv == null || !shouldSort(inv)) return null;
        if (FOLIA && isUnsafeSharedInventory(inv)) return null;

        int slot = event.getSlot();
        InventoryType type = inv.getType();

        if (type == InventoryType.PLAYER) {
            if (slot < 9) {
                return new Target(inv, type, 0, 9, Region.HOTBAR);
            } else if (slot < MainConfig.PLAYER_STORAGE_END) {
                return new Target(inv, type, 9, MainConfig.PLAYER_STORAGE_END, Region.PLAYER_MAIN);
            } else {
                // Armor / offhand slots — never sort or pack
                return null;
            }
        } else {
            int min = GridGeometry.storageOffset(inv.getHolder());
            int max = inv.getSize();
            return new Target(inv, type, min, max, Region.CONTAINER);
        }
    }

    /**
     * Returns {@code true} when sorting is permitted for the given {@link Region}: checks the
     * umbrella {@code clicksorted.sort} node first, then the region-specific child.
     */
    private boolean sortAllowed(Region region, Player player) {
        if (!Permissions.isAllowedTo(player, "clicksorted.sort")) return false;
        return switch (region) {
            case HOTBAR -> Permissions.isAllowedTo(player, "clicksorted.sort.hotbar");
            case PLAYER_MAIN -> Permissions.isAllowedTo(player, "clicksorted.sort.player");
            case CONTAINER -> Permissions.isAllowedTo(player, "clicksorted.sort.container");
        };
    }

    /**
     * Returns {@code true} when bundle packing is both preferred by the player and permitted
     * for the given {@link Region}. Packing never applies to the hotbar.
     */
    private boolean packAllowed(Region region, Player player, PlayerSortingPrefs prefs) {
        return switch (region) {
            case HOTBAR -> false;
            case PLAYER_MAIN -> prefs.getBundlePackInInventory(player)
                    && Permissions.isAllowedTo(player, "clicksorted.bundle.inventory");
            case CONTAINER -> prefs.getBundlePackInContainers(player)
                    && Permissions.isAllowedTo(player, "clicksorted.bundle.container");
        };
    }

    // -------------------------------------------------------------------------
    // Write-back helpers (sort-with-layout path)
    // -------------------------------------------------------------------------

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
    // Shared helpers
    // -------------------------------------------------------------------------

    private void dropOverflow(Player p, List<ItemStack> overflow) {
        if (!overflow.isEmpty()) {
            // This *shouldn't* happen, but there is a possibility if some other plugin has been messing
            // with max stack sizes, and we end up with an overflowing inventory after merging stacks.
            plugin.messages().to(p).alert().send("dropItems");
            for (ItemStack item : overflow) {
                Log.debug("dropping " + item + " by player " + p.getName());
                p.getWorld().dropItemNaturally(p.getLocation(), item);
            }
        }
    }

    private static void refreshViewers(List<HumanEntity> viewers) {
        for (HumanEntity he : viewers) {
            if (he instanceof Player viewer) {
                viewer.updateInventory();
            }
        }
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

    /**
     * A plugin-created (non-vanilla-held) inventory with more than one current viewer has no
     * single owning region thread on Folia, so two viewers' clicks can concurrently read-modify-write
     * the same backing array. This is the honest, cheap proxy for "unsafe to sort here" — see
     * CLAUDE.md's Folia section for why this is refused rather than serialized with a lock.
     */
    static boolean isUnsafeSharedInventory(Inventory inventory) {
        return inventory.getViewers().size() > 1 && !isVanillaInventoryHolder(inventory.getHolder());
    }

    private static final boolean FOLIA = detectFolia();

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
