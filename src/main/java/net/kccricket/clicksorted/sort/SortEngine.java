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
            emitStacks(sorted, sortKey, amounts.get(sortKey));
        }

        return sorted;
    }

    /**
     * Combine same-item stacks <em>in place</em>, position-indexed. {@code items} is treated as a
     * slot-aligned list (index = slot position, {@code null} = empty). For each group of like
     * items, the smallest stacks are drained into the largest ones until only the minimum number
     * of stacks remain: the largest stacks keep their positions (and grow), and the emptied
     * positions are set to {@code null}. Stacks that already can't be consolidated are left
     * untouched — nothing is moved or rebalanced needlessly.
     *
     * <p>{@code BUNDLE} items are skipped (they are non-stackable and serve as bins). Non-stackable
     * items never group.
     *
     * @param items slot-aligned items, mutated in place
     */
    public static void mergeStacks(List<ItemStack> items) {
        // Group like, stackable, non-bundle items by identity, in encounter order.
        Map<SortKey, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            ItemStack is = items.get(i);
            if (is == null || is.getType() == Material.BUNDLE) continue;
            if (is.getType().getMaxStackSize() <= 1) continue;
            groups.computeIfAbsent(new SortKey(is, SortingMethod.NAME), k -> new ArrayList<>()).add(i);
        }

        for (List<Integer> positions : groups.values()) {
            if (positions.size() < 2) continue;

            int max = items.get(positions.get(0)).getType().getMaxStackSize();
            int total = 0;
            for (int p : positions) total += items.get(p).getAmount();
            int needed = (total + max - 1) / max; // ceil
            if (needed == positions.size()) continue; // nothing to drain — leave as-is

            // Largest stacks first: the first `needed` survive (keep their slots), the rest drain.
            List<Integer> byAmountDesc = new ArrayList<>(positions);
            byAmountDesc.sort((a, b) -> items.get(b).getAmount() - items.get(a).getAmount());

            int drained = 0;
            for (int i = needed; i < byAmountDesc.size(); i++) {
                int pos = byAmountDesc.get(i);
                drained += items.get(pos).getAmount();
                items.set(pos, null);
            }

            // Pour the drained amount into the surviving stacks, largest first.
            for (int i = 0; i < needed && drained > 0; i++) {
                ItemStack survivor = items.get(byAmountDesc.get(i));
                int space = max - survivor.getAmount();
                int add = Math.min(space, drained);
                if (add > 0) {
                    survivor.setAmount(survivor.getAmount() + add);
                    drained -= add;
                }
            }
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

    private static <T extends Comparable<? super T>> List<T> asSortedList(Collection<T> c) {
        List<T> list = new ArrayList<>(c);
        Collections.sort(list);
        return list;
    }
}
