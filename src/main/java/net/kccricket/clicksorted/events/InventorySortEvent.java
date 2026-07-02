package net.kccricket.clicksorted.events;

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

import net.kccricket.clicksorted.sort.MaterialNameSet;
import net.kccricket.clicksorted.sort.ProtectedItems;
import net.kccricket.clicksorted.text.ItemNames;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Fired by {@link net.kccricket.clicksorted.sort.InventorySortService} after a click matches a
 * sort trigger and before any writes to the inventory, so third-party plugins can inspect or
 * intervene. Cancelling the event aborts the sort entirely; {@link #excludeSlot(int)} and
 * {@link #excludeItem(Material)}/{@link #excludeItem(String)} narrow what gets touched without
 * aborting. A cancelling listener may call {@link #setCancelReason(Component)} to explain why —
 * shown to the player rate-limited (this event fires on every matching click); a cancellation with
 * no reason set is not reported to the player at all. Setting a reason has no effect unless the
 * event is also cancelled.
 *
 * <p>{@link #getInventory()} returns the <strong>live</strong> target inventory, captured before
 * any write — at dispatch time its contents are the pre-sort state, but it is not a snapshot: a
 * listener that reads it after the event returns (e.g. on a later tick) will see post-sort
 * contents. There is no post-sort inventory to hand out at dispatch, since the sort has not run
 * yet and the event is still cancellable.
 *
 * <h2>Slot classification</h2>
 * The sortable set is no longer a contiguous range — locks and admin rules can carve arbitrary
 * holes in it. {@link #getSlots()} and {@link #statusOf(int)} classify every slot in the clicked
 * inventory by {@link SlotStatus}, evaluated in this precedence when more than one reason
 * applies: {@code OUT_OF_RANGE} &gt; {@code ADMIN_LOCKED} &gt; {@code USER_LOCKED} &gt;
 * {@code SORTABLE}. This classification reflects range and lock structure only — item-based
 * exclusions (the admin "do not touch" list, and any {@code excludeItem} additions) are
 * content-dependent and are resolved against {@link #getSortableSlots()} after listeners run, so
 * they are intentionally not reflected in {@link #getSlots()}.
 */
public class InventorySortEvent extends InventoryInteractEvent {

    /**
     * Why a slot is or isn't sortable, from the perspective of range and lock structure.
     * When more than one reason applies to a slot, the earlier-declared constant wins:
     * {@code OUT_OF_RANGE} &gt; {@code ADMIN_LOCKED} &gt; {@code USER_LOCKED} &gt; {@code SORTABLE}.
     */
    public enum SlotStatus {
        /** Not part of the region targeted by this click (e.g. armor/offhand, or the other player-inventory band). */
        OUT_OF_RANGE,
        /** Excluded by a server-enforced lock (config {@code locked_slots.player} or a {@code clicksorted.lock.player.slot.<n>} permission). */
        ADMIN_LOCKED,
        /** Excluded because the sorting player locked this slot themselves (lock GUI). */
        USER_LOCKED,
        /** In range and not locked — eligible to be read, moved, or overwritten by the sort. */
        SORTABLE
    }

    private final Inventory sortInv;
    private final Set<Integer> regionSlots;
    private final Set<Integer> userLockedSlots;
    private final Set<Integer> adminLockedSlots;
    private final Set<Integer> sortableSlots = new TreeSet<>();
    private final ProtectedItems protectedItems;
    private final Set<Material> excludedMaterials = new LinkedHashSet<>();
    private final Set<String> excludedItemNames = new LinkedHashSet<>();
    private MaterialNameSet excludedItemsCache;
    private @Nullable Component cancelReason;

    /**
     * Primary constructor: the region a click targets, the locks that already exclude slots from
     * it, and the admin item-blacklist snapshot to expose as metadata.
     *
     * <p>{@link #getSortableSlots()} is initialized to {@code regionSlots} minus both lock sets —
     * i.e. it already reflects lock state at dispatch time.
     *
     * @param transaction      the inventory view the click occurred in
     * @param sortInv           the live target inventory (pre-sort at dispatch)
     * @param regionSlots       the full slot range targeted by this click (e.g. hotbar, player main, or container storage)
     * @param userLockedSlots   slots the sorting player has locked themselves; empty for non-player inventories
     * @param adminLockedSlots  slots excluded by server-enforced locks; empty for non-player inventories
     * @param protectedItems    the admin "do not touch" item snapshot in effect for this sort
     */
    public InventorySortEvent(InventoryView transaction, Inventory sortInv, Set<Integer> regionSlots,
                               Set<Integer> userLockedSlots, Set<Integer> adminLockedSlots,
                               ProtectedItems protectedItems) {
        super(transaction);
        this.sortInv = sortInv;
        this.regionSlots = Set.copyOf(regionSlots);
        this.userLockedSlots = Set.copyOf(userLockedSlots);
        this.adminLockedSlots = Set.copyOf(adminLockedSlots);
        this.protectedItems = protectedItems;

        sortableSlots.addAll(regionSlots);
        sortableSlots.removeAll(userLockedSlots);
        sortableSlots.removeAll(adminLockedSlots);
    }

    /**
     * @deprecated Materializes a contiguous {@code [min, max)} range as the region, with no lock
     * information and no admin item-blacklist metadata. Use
     * {@link #InventorySortEvent(InventoryView, Inventory, Set, Set, Set, ProtectedItems)} so
     * listeners see the true sortable set (locks and holes included).
     */
    @Deprecated
    public InventorySortEvent(InventoryView transaction, Inventory sortInv, int min, int max) {
        this(transaction, sortInv, rangeSet(min, max), Set.of(), Set.of(), ProtectedItems.EMPTY);
    }

    /** Materializes the contiguous {@code [min, max)} slot range as a set. */
    public static Set<Integer> rangeSet(int min, int max) {
        Set<Integer> range = new TreeSet<>();
        for (int i = min; i < max; i++) {
            range.add(i);
        }
        return range;
    }

    @Override
    public Inventory getInventory() {
        return sortInv;
    }

    /**
     * The reason a cancelling listener gave for blocking this sort, or {@code null} if none was
     * set (or the event wasn't cancelled).
     */
    public @Nullable Component getCancelReason() {
        return cancelReason;
    }

    /**
     * Lets a listener explain why it cancelled this sort. Only meaningful alongside
     * {@link #setCancelled(boolean) setCancelled(true)} — the reason is not surfaced otherwise, and
     * (since this event fires on every matching click) is shown to the player rate-limited rather
     * than on every cancelled click.
     */
    public void setCancelReason(@Nullable Component reason) {
        this.cancelReason = reason;
    }

    /**
     * The current mutable sortable set — slots that will actually be read/moved/overwritten by
     * the sort, unless further narrowed by {@link #excludeSlot(int)} or an item match against
     * {@link #matchesExcludedItem(ItemStack)}. Already reflects lock state at dispatch time.
     */
    public Set<Integer> getSortableSlots() {
        return sortableSlots;
    }

    /** Removes {@code slot} from {@link #getSortableSlots()}, protecting it from this sort. */
    public void excludeSlot(int slot) {
        sortableSlots.remove(slot);
    }

    /** The full slot range targeted by this click (before any lock or item exclusion). */
    public Set<Integer> getRegionSlots() {
        return regionSlots;
    }

    /** Slots the sorting player has locked themselves; always empty for non-player inventories. */
    public Set<Integer> getUserLockedSlots() {
        return userLockedSlots;
    }

    /** Slots excluded by server-enforced locks; always empty for non-player inventories. */
    public Set<Integer> getAdminLockedSlots() {
        return adminLockedSlots;
    }

    /**
     * The admin "do not touch" item-blacklist snapshot in effect for this sort (config
     * {@code blacklist.*} plus the sorting player's {@code clicksorted.blacklist.*} permission
     * nodes). Exposed as read-only metadata — combine with {@link #excludeItem} to add to it.
     */
    public ProtectedItems getProtectedItems() {
        return protectedItems;
    }

    /**
     * Classifies {@code slot} by range/lock structure. See the class Javadoc for precedence when
     * multiple reasons apply.
     */
    public SlotStatus statusOf(int slot) {
        if (!regionSlots.contains(slot)) return SlotStatus.OUT_OF_RANGE;
        if (adminLockedSlots.contains(slot)) return SlotStatus.ADMIN_LOCKED;
        if (userLockedSlots.contains(slot)) return SlotStatus.USER_LOCKED;
        return SlotStatus.SORTABLE;
    }

    /**
     * Classifies every slot in the clicked inventory (index {@code 0} through
     * {@code getInventory().getSize() - 1}) via {@link #statusOf(int)}.
     */
    public Map<Integer, SlotStatus> getSlots() {
        Map<Integer, SlotStatus> slots = new LinkedHashMap<>();
        int size = sortInv.getSize();
        for (int i = 0; i < size; i++) {
            slots.put(i, statusOf(i));
        }
        return Collections.unmodifiableMap(slots);
    }

    /**
     * Adds {@code material} to this event's listener-contributed item exclusions, so any slot
     * holding a matching item is excluded from the sort (in addition to the admin blacklist).
     */
    public void excludeItem(Material material) {
        excludedMaterials.add(material);
        excludedItemsCache = null;
    }

    /**
     * Adds {@code name} to this event's listener-contributed item exclusions, matched
     * case-insensitively against an item's resolved plain-text display name (see
     * {@link ItemNames#lookup(ItemStack)}), in addition to the admin blacklist.
     */
    public void excludeItem(String name) {
        excludedItemNames.add(name.toLowerCase(Locale.ROOT));
        excludedItemsCache = null;
    }

    /** Unmodifiable view of materials added via {@link #excludeItem(Material)} during this event. */
    public Set<Material> getExcludedMaterials() {
        return Collections.unmodifiableSet(excludedMaterials);
    }

    /** Unmodifiable view of display names (already lower-cased) added via {@link #excludeItem(String)} during this event. */
    public Set<String> getExcludedItemNames() {
        return Collections.unmodifiableSet(excludedItemNames);
    }

    /**
     * Returns {@code true} if {@code is} matches the admin "do not touch" blacklist
     * ({@link #getProtectedItems()}) or any listener-contributed exclusion added via
     * {@link #excludeItem}. This is the single item-exclusion oracle the sort service consults
     * against {@link #getSortableSlots()} after listeners run.
     */
    public boolean matchesExcludedItem(ItemStack is) {
        if (protectedItems.blocks(is)) return true;
        if (excludedMaterials.isEmpty() && excludedItemNames.isEmpty()) return false;
        if (excludedItemsCache == null) {
            excludedItemsCache = new MaterialNameSet(excludedMaterials, excludedItemNames);
        }
        return excludedItemsCache.contains(is);
    }
}
