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

import net.kccricket.clicksorted.logging.Log;
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
        Map<SortKey, Integer> amounts = new HashMap<>();

        // Phase 1: extract unique item keys and accumulate quantities
        Log.debug("sortAndMerge: sortable = " + sortableSlots + ", size = " + items.length);
        for (int i : sortableSlots) {
            ItemStack is = items[i];
            if (is != null) {
                SortKey key = new SortKey(is, sortMethod);
                if (amounts.containsKey(key)) {
                    amounts.put(key, amounts.get(key) + is.getAmount());
                } else {
                    amounts.put(key, is.getAmount());
                }
            }
        }

        // Phase 2: sort the extracted keys and reconstruct stacks respecting max stack size
        List<ItemStack> sorted = new LinkedList<>();
        for (SortKey sortKey : asSortedList(amounts.keySet())) {
            int amount = amounts.get(sortKey);
            Log.trace("Process item [" + sortKey + "], amount = " + amount);
            Material mat = sortKey.getMaterial();
            int maxStack = mat.getMaxStackSize();
            Log.trace("max stack size for " + mat + " = " + maxStack);
            if (maxStack == 0) {
                Log.severe("Item with zero max stack size will be dropped: " + mat + " (amount=" + amount + ")");
                sorted.add(sortKey.toItemStack(amount));
            } else {
                while (amount > maxStack) {
                    sorted.add(sortKey.toItemStack(maxStack));
                    amount -= maxStack;
                }
                sorted.add(sortKey.toItemStack(amount));
            }
        }

        return sorted;
    }

    private static <T extends Comparable<? super T>> List<T> asSortedList(Collection<T> c) {
        List<T> list = new ArrayList<>(c);
        Collections.sort(list);
        return list;
    }
}
