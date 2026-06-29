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
 * <p>Loose same-material stacks consolidate within their existing slots: within each per-type lane,
 * the total amount redistributes toward the lowest-indexed occupied slot (full stacks first,
 * remainder last, trailing slots cleared). Bundles keep their original slots; when
 * {@code packEnabled} is {@code true}, eligible remainders are packed into the bundles already
 * present in the sortable region.
 *
 * <p>Items displaced from bundles (types with no loose lane, whose leftover the packer cannot
 * return to a bundle) and items that exceed their lane's own slot capacity are placed into free
 * slots — slots empty before the click, or freed because their contents merged away or were packed
 * into a bundle. Items are only returned as overflow when no free slot remains.
 *
 * <p>Non-stackable items keep their original slots; they are never merged or moved.
 *
 * <p>This class is pure (no plugin state); the caller performs all inventory writes.
 */
public final class InPlacePacker {

    private InPlacePacker() {}

    /**
     * The result of a consolidation pass: a map of slot→stack to write back, and any items that
     * could not fit into any existing or freed slot (to be dropped as overflow).
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
     *   <li>Loose stacks are anchored to their lane's slots; they never move to an unrelated slot.</li>
     *   <li>Bundles keep their original slots; their contents may be mutated by the packer.</li>
     *   <li>Non-stackables keep their original slots; they are never merged or moved.</li>
     *   <li>Items displaced from bundles or exceeding lane capacity fill free/freed slots in
     *       ascending slot order.</li>
     *   <li>Items appear as overflow only when the sortable region has no free slot remaining.</li>
     * </ul>
     *
     * @param contents      full inventory contents array (read-only)
     * @param sortableSlots eligible slots, post-exclusion (a {@link TreeSet} — ascending order)
     * @param packEnabled   whether to call {@link BundlePacker} on bundle-eligible remainders
     * @param stackLimit    max distinct entries per bundle (0 = weight-only); forwarded to packer
     * @param blacklist     materials/names excluded from packing; forwarded to packer
     * @return the placement map and any overflow stacks
     */
    public static Result consolidate(ItemStack[] contents, Set<Integer> sortableSlots,
                                     boolean packEnabled, int stackLimit, BundleBlacklist blacklist) {
        Map<SortKey, Lane> lanes = new LinkedHashMap<>();
        LinkedHashMap<Integer, ItemStack> bundleSlots = new LinkedHashMap<>();
        classify(contents, sortableSlots, lanes, bundleSlots);

        Map<Integer, ItemStack> placement = new LinkedHashMap<>();
        List<ItemStack> displaced = new ArrayList<>();

        if (packEnabled && !bundleSlots.isEmpty()) {
            displaced.addAll(runBundlePass(lanes, bundleSlots, placement, stackLimit, blacklist));
        } else {
            placement.putAll(bundleSlots);
        }
        displaced.addAll(distributeLanes(lanes, placement));
        List<ItemStack> overflow = fillFreeSlots(placement, sortableSlots, displaced);
        return new Result(placement, overflow);
    }

    // ---- Private helpers ----

    /** Split {@code sortableSlots} into per-type loose lanes and bundle slots. */
    private static void classify(ItemStack[] contents, Set<Integer> sortableSlots,
                                  Map<SortKey, Lane> lanes,
                                  LinkedHashMap<Integer, ItemStack> bundleSlots) {
        for (int slot : sortableSlots) {
            ItemStack is = contents[slot];
            if (is == null || is.getType() == Material.AIR) continue;
            if (BundlePacker.isBundle(is.getType())) {
                bundleSlots.put(slot, is.clone());
            } else {
                // Non-stackables use a unique key per slot so they are never merged across slots.
                SortKey key = SortKey.poolKey(is);
                lanes.computeIfAbsent(key, k -> new Lane(is)).addSlot(slot, is.getAmount());
            }
        }
    }

    /**
     * Run the bundle-packing pass: pool bundleable lanes, call {@link BundlePacker}, write mutated
     * bundles to {@code placement}, update lane totals from leftover amounts, and collect displaced
     * stacks for bundle-only keys (types that came out of a bundle but have no loose lane).
     */
    private static List<ItemStack> runBundlePass(Map<SortKey, Lane> lanes,
                                                  LinkedHashMap<Integer, ItemStack> bundleSlots,
                                                  Map<Integer, ItemStack> placement,
                                                  int stackLimit, BundleBlacklist blacklist) {
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
        // the sortable region) are not in `lanes`; they are collected as displaced below.
        for (SortKey key : loosePool.keySet()) {
            Lane lane = lanes.get(key);
            if (lane != null) {
                lane.total = leftoverAmounts.getOrDefault(key, 0L);
            }
        }

        // Displaced: bundle-only keys whose leftover has no lane to absorb them.
        List<ItemStack> displaced = new ArrayList<>();
        for (var entry : leftoverAmounts.entrySet()) {
            if (!lanes.containsKey(entry.getKey())) {
                ItemStack sample = samples.get(entry.getKey());
                if (sample != null) {
                    displaced.addAll(splitIntoStacks(sample, entry.getValue()));
                }
            }
        }
        return displaced;
    }

    /**
     * Write each lane's total across its own slots (full stacks first, remainder last, trailing
     * slots absent → caller clears them). Returns any amount exceeding the lane's slot capacity as
     * displaced stacks (can only happen when bundle unpacking injected extra items into a lane
     * whose slots are already fully packed).
     */
    private static List<ItemStack> distributeLanes(Map<SortKey, Lane> lanes,
                                                    Map<Integer, ItemStack> placement) {
        List<ItemStack> displaced = new ArrayList<>();
        for (Lane lane : lanes.values()) {
            long remaining = lane.total;
            int maxStack = lane.sample.getMaxStackSize();
            for (int slot : lane.slots) {
                if (remaining <= 0) {
                    // Slot is absent from placement → caller will clear it.
                    continue;
                }
                int amt = (int) Math.min(remaining, maxStack);
                ItemStack out = lane.sample.clone();
                out.setAmount(amt);
                placement.put(slot, out);
                remaining -= amt;
            }
            if (remaining > 0) {
                displaced.addAll(splitIntoStacks(lane.sample, remaining));
            }
        }
        return displaced;
    }

    /**
     * Assign each displaced stack to the next free slot (ascending), returning whatever has no
     * slot left as overflow. Free slots are those in {@code sortableSlots} not yet in
     * {@code placement} — including slots emptied by lane consolidation or bundle packing.
     */
    private static List<ItemStack> fillFreeSlots(Map<Integer, ItemStack> placement,
                                                  Set<Integer> sortableSlots,
                                                  List<ItemStack> displaced) {
        if (displaced.isEmpty()) return List.of();

        // sortableSlots is a TreeSet, so iteration is ascending — free slots come out in order.
        Iterator<ItemStack> dispIter = displaced.iterator();
        ItemStack next = dispIter.next();
        List<ItemStack> overflow = new ArrayList<>();
        for (int slot : sortableSlots) {
            if (!placement.containsKey(slot)) {
                placement.put(slot, next);
                if (!dispIter.hasNext()) return overflow; // all placed
                next = dispIter.next();
            }
        }
        // Ran out of free slots; remaining displaced are true overflow.
        overflow.add(next);
        dispIter.forEachRemaining(overflow::add);
        return overflow;
    }

    /** Split {@code amount} into max-stack-sized {@link ItemStack}s cloned from {@code sample}. */
    private static List<ItemStack> splitIntoStacks(ItemStack sample, long amount) {
        List<ItemStack> result = new ArrayList<>();
        int maxStack = sample.getMaxStackSize();
        while (amount > 0) {
            int amt = (int) Math.min(amount, maxStack);
            ItemStack stack = sample.clone();
            stack.setAmount(amt);
            result.add(stack);
            amount -= amt;
        }
        return result;
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
