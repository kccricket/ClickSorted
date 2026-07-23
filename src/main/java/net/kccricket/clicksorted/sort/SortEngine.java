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

import net.kccricket.kcmclib.logging.Log;
import net.kccricket.clicksorted.model.SortKey;
import net.kccricket.clicksorted.model.SortingMethod;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Pure sort algorithm with no plugin-instance state. Collapses a set of item stacks into a
 * de-duplicated, sorted list ready to be written back to inventory slots.
 */
public final class SortEngine {

    private SortEngine() {}

    /**
     * Sort and merge the given inventory contents, considering only the specified sortable slots.
     *
     * @param items         the full inventory contents array
     * @param sortableSlots the slot indices to extract from and write back to
     * @param sortMethod    the ordering strategy
     * @return a sorted, stack-merged list of items
     */
    public static List<ItemStack> sortAndMerge(ItemStack[] items, Set<Integer> sortableSlots, SortingMethod sortMethod) {
        Log.debug("sortAndMerge: sortable = " + sortableSlots + ", size = " + items.length);
        List<ItemStack> extracted = new ArrayList<>(sortableSlots.size());
        for (int i : sortableSlots) {
            if (items[i] != null) {
                extracted.add(items[i]);
            }
        }
        return sortAndMerge(extracted, sortMethod);
    }

    /**
     * Sort and merge a flat collection of item stacks (no slot indexing).
     *
     * @param items      the item stacks to pool, merge, and sort ({@code null} entries are ignored)
     * @param sortMethod the ordering strategy
     * @return a sorted, stack-merged list of items
     */
    public static List<ItemStack> sortAndMerge(Collection<ItemStack> items, SortingMethod sortMethod) {
        // Fungible (stackable, non-bundle) items are merged by key and quantity-summed; non-fungible
        // items (bundles and any maxStackSize <= 1 item) carry their own contents/meta and must never
        // be collapsed — each is kept as a discrete stack, ordered by key but emitted verbatim.
        Map<SortKey, Integer> amounts = new HashMap<>();
        List<Entry> discretes = new ArrayList<>();

        for (ItemStack is : items) {
            if (is == null) {
                continue;
            }
            SortKey key = new SortKey(is, sortMethod);
            if (isFungible(is)) {
                // Lambda, not Integer::sum: a method ref binds the boxed map values straight to
                // primitive params, tripping JDT's "needs unchecked conversion" null warning.
                amounts.merge(key, is.getAmount(), (a, b) -> Integer.sum(a, b));
            } else {
                discretes.add(new Entry(key, is));
            }
        }

        // Phase 2: order fungible keys and discrete items together by SortKey, then emit.
        for (Map.Entry<SortKey, Integer> e : amounts.entrySet()) {
            discretes.add(new Entry(e.getKey(), null));
        }
        Collections.sort(discretes);

        List<ItemStack> sorted = new ArrayList<>(discretes.size());
        for (Entry entry : discretes) {
            if (entry.stack != null) {
                sorted.add(entry.stack);
            } else {
                emitStacks(sorted, entry.key, amounts.get(entry.key));
            }
        }

        return sorted;
    }

    /** Whether an item is fungible: stackable and not a bundle, so it may be quantity-merged. */
    private static boolean isFungible(ItemStack is) {
        return !BundlePacker.isBundle(is.getType()) && is.getType().getMaxStackSize() > 1;
    }

    /**
     * One orderable output entry: a discrete non-fungible {@code stack} (emitted verbatim), or a
     * fungible group when {@code stack} is {@code null} (emitted via {@link #emitStacks} using the
     * accumulated amount for {@code key}). Ordered solely by {@code key}.
     */
    private record Entry(SortKey key, ItemStack stack) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry other) {
            return key.compareTo(other.key);
        }
    }

    /**
     * Reconstruct {@code amount} of {@code sortKey}'s item as full max-size stacks followed by a
     * single remainder stack, appending them to {@code out}.
     */
    private static void emitStacks(List<ItemStack> out, SortKey sortKey, int amount) {
        Log.trace("Process item [" + sortKey + "], amount = " + amount);
        Material mat = sortKey.getMaterial();
        int maxStack = mat.getMaxStackSize();
        Log.trace("max stack size for " + mat + " = " + maxStack);
        if (maxStack == 0) {
            Log.severe("Item with zero max stack size will be dropped: " + mat + " (amount=" + amount + ")");
            out.add(sortKey.toItemStack(amount));
            return;
        }
        while (amount > maxStack) {
            out.add(sortKey.toItemStack(maxStack));
            amount -= maxStack;
        }
        out.add(sortKey.toItemStack(amount));
    }
}
