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

import net.kccricket.clicksorted.model.SortKey;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * In-place consolidator for the "sorting off, bundle packing on" path.
 *
 * <p>Collapses same-material stacks within their own slots (full stacks first, remainder last,
 * trailing empties cleared) without ever placing an item in a slot that was empty before the
 * call. When {@code packEnabled} is {@code true}, eligible remainders are also packed into the
 * bundles that are already present in the sortable slot set, which stay in their slots.
 *
 * <p>The only time an item can appear as overflow is the rare case where unpacking a bundle
 * yields an item type whose slots are already full; those items are returned for the caller to
 * drop naturally. Non-stackable items and bundles are left in place (never moved, only mutated
 * when packing applies).
 *
 * <p>This class is pure (no plugin state); the caller performs all inventory writes.
 */
public final class InPlacePacker {

    private InPlacePacker() {}

    /**
     * The result of a consolidation pass: a map of slot→stack to write back, and any items that
     * could not fit into any existing slot (to be dropped as overflow).
     *
     * <p>Slots that appear in {@code sortableSlots} but are absent from {@code placement} should
     * be cleared by the caller ({@code inv.clear(slot)}).
     */
    public record Result(Map<Integer, ItemStack> placement, List<ItemStack> overflow) {}

    /**
     * Consolidate same-material stacks within their existing slots, optionally packing eligible
     * remainders into the bundles already present in {@code sortableSlots}.
     *
     * <p>Invariants:
     * <ul>
     *   <li>No item is placed in a slot that held {@code null} or {@link Material#AIR} before
     *       the call (absent from {@code placement} = clear it).</li>
     *   <li>Bundles keep their original slots; their contents may be mutated by the packer.</li>
     *   <li>Non-stackables keep their original slots; they are never merged or moved.</li>
     *   <li>Items whose total per-lane amount exceeds the lane's slot capacity are returned as
     *       overflow (only possible when bundle unpacking injects a new item type).</li>
     * </ul>
     *
     * @param contents      full inventory contents array (read-only)
     * @param sortableSlots eligible slots, post-exclusion (read in slot order)
     * @param packEnabled   whether to call {@link BundlePacker} on bundle-eligible remainders
     * @param stackLimit    max distinct entries per bundle (0 = weight-only); forwarded to packer
     * @param blacklist     materials/names excluded from packing; forwarded to packer
     * @return the placement map and any overflow stacks
     */
    public static Result consolidate(ItemStack[] contents, Set<Integer> sortableSlots,
                                     boolean packEnabled, int stackLimit, BundleBlacklist blacklist) {
        // Each Lane tracks the ordered slot list, sum total, and a sample stack for one item type.
        // Non-stackables (maxStackSize <= 1) get a private "lane" per slot (size-1 list, total=1)
        // so they stay put without triggering any merging — they are naturally left in place when
        // the lane distribution loop writes them back.
        Map<SortKey, Lane> lanes = new LinkedHashMap<>();
        // Bundles: slot → clone used as the packer bin (mutated in place by BundlePacker).
        LinkedHashMap<Integer, ItemStack> bundleSlots = new LinkedHashMap<>();
        List<ItemStack> overflow = new ArrayList<>();
        Map<Integer, ItemStack> placement = new LinkedHashMap<>();

        for (int slot : sortableSlots) {
            ItemStack is = contents[slot];
            if (is == null || is.getType() == Material.AIR) continue;
            if (BundlePacker.isBundle(is.getType())) {
                bundleSlots.put(slot, is.clone());
            } else {
                // Non-stackables use a unique key per slot so they are never merged across slots.
                SortKey key = SortKey.poolKey(is);
                // For non-stackables we still build a lane, but the per-slot amount is always 1 and
                // the total will equal the number of slots they occupy. The distribution step will
                // write each full stack of 1 back into its own slot — no movement occurs.
                lanes.computeIfAbsent(key, k -> new Lane(is)).addSlot(slot, is.getAmount());
            }
        }

        // ---- Bundle packing pass (only when bundles are present) ----
        if (packEnabled && !bundleSlots.isEmpty()) {
            Map<SortKey, Long> loosePool = new LinkedHashMap<>();
            Map<SortKey, ItemStack> samples = new LinkedHashMap<>();
            for (var entry : lanes.entrySet()) {
                Lane lane = entry.getValue();
                if (BundlePacker.canBundle(lane.sample, blacklist)) {
                    loosePool.put(entry.getKey(), lane.total);
                    samples.put(entry.getKey(), lane.sample);
                }
            }

            // The packer mutates the bundle clones in place; loosePool may also grow (bundle
            // contents from the bins are merged in by the packer).
            List<Integer> bundleSlotOrder = new ArrayList<>(bundleSlots.keySet());
            List<ItemStack> bundles = new ArrayList<>(bundleSlots.values()); // already clones
            List<ItemStack> leftover = BundlePacker.packIntoBundles(loosePool, samples, bundles, stackLimit, blacklist);

            // Write the (mutated) bundle clones back to their original slots.
            for (int i = 0; i < bundleSlotOrder.size(); i++) {
                placement.put(bundleSlotOrder.get(i), bundles.get(i));
            }

            // Sum leftover amounts per key.
            Map<SortKey, Long> leftoverAmounts = new LinkedHashMap<>();
            for (ItemStack ls : leftover) {
                SortKey key = SortKey.poolKey(ls);
                // Lambda, not Long::sum: a method ref binds the boxed map values straight to
                // primitive params, tripping JDT's "needs unchecked conversion" null warning.
                leftoverAmounts.merge(key, (long) ls.getAmount(), (a, b) -> Long.sum(a, b));
            }

            // Update lane totals for the keys that were sent to the packer.
            // Bundle-only keys (items unpacked from a bundle with no loose representation in
            // the sortable region) are not in `lanes`; skip them — the homeless-items loop
            // below routes their leftover to overflow instead.
            for (SortKey key : loosePool.keySet()) {
                Lane lane = lanes.get(key);
                if (lane != null) {
                    lane.total = leftoverAmounts.getOrDefault(key, 0L);
                }
            }

            // Detect homeless items: leftover keys with no lane (types unpacked from bundles that
            // had no loose representation in the sortable region).
            for (var entry : leftoverAmounts.entrySet()) {
                if (!lanes.containsKey(entry.getKey())) {
                    ItemStack sample = samples.get(entry.getKey());
                    if (sample != null) {
                        long remaining = entry.getValue();
                        int maxStack = sample.getMaxStackSize();
                        while (remaining > 0) {
                            int amt = (int) Math.min(remaining, maxStack);
                            ItemStack stack = sample.clone();
                            stack.setAmount(amt);
                            overflow.add(stack);
                            remaining -= amt;
                        }
                    }
                }
            }
        } else {
            // No packing (or no bundles present): write bundle clones back unchanged so they are
            // not cleared by the caller's write-back loop.
            placement.putAll(bundleSlots);
        }

        // ---- Per-lane in-place distribution ----
        // Each lane distributes its total across the slots it originally occupied (in order):
        // full maxStackSize stacks first, remainder in the last occupied slot, extras cleared.
        // Non-stackable lanes (maxStackSize = 1) just write amount=1 back into each slot, which
        // is a no-op with respect to position.
        for (Lane lane : lanes.values()) {
            long remaining = lane.total;
            int maxStack = lane.sample.getMaxStackSize();
            for (int slot : lane.slots) {
                if (remaining <= 0) {
                    // slot is absent from placement → caller will clear it
                    continue;
                }
                int amt = (int) Math.min(remaining, maxStack);
                ItemStack out = lane.sample.clone();
                out.setAmount(amt);
                placement.put(slot, out);
                remaining -= amt;
            }
            // Any amount that exceeds the lane's own slot capacity goes to overflow. This can only
            // happen when a bundle unpack injected extra items into an existing lane whose slots
            // are already fully packed.
            while (remaining > 0) {
                int amt = (int) Math.min(remaining, maxStack);
                ItemStack out = lane.sample.clone();
                out.setAmount(amt);
                overflow.add(out);
                remaining -= amt;
            }
        }

        return new Result(placement, overflow);
    }

    // ---- Internal ----

    private static final class Lane {
        final ItemStack sample;         // representative stack (cloned from the first seen item)
        final List<Integer> slots;      // ordered slot indices (insertion order = sortableSlots order)
        long total;                     // sum of all amounts in the lane (may decrease after packing)

        Lane(ItemStack sample) {
            this.sample = sample.clone();
            this.slots = new ArrayList<>();
        }

        void addSlot(int slot, int amount) {
            slots.add(slot);
            total += amount;
        }
    }
}
