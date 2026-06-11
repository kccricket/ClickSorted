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
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.*;

/**
 * Post-pass over a sorted item list that folds partial stacks into bundles already present
 * in the list, reclaiming inventory slots.
 *
 * <p>Uses a First-Fit-Decreasing greedy heuristic for the bin-packing step. Bin packing is
 * NP-hard to solve optimally; FFD gets within ~22% of the theoretical minimum worst-case and
 * is typically much closer at inventory scale (~40 slots). Results are "very good", not
 * provably minimal — worth a note but not a blocker.
 *
 * <p>Invariants:
 * <ul>
 *   <li>Each bundle's total weight stays ≤ 64 (item weight = 64 / maxStackSize per item).</li>
 *   <li>Distinct-entry count stays ≤ {@code entryCap} per bundle (0 = weight-only limit).</li>
 *   <li>Stacks are never split — each partial moves whole or not at all.</li>
 *   <li>Existing bundle contents are never removed; we only add to them.</li>
 *   <li>Bundles (policy) and shulker boxes (hard limit) are never packed into bundles.</li>
 * </ul>
 */
public final class BundlePacker {

    private BundlePacker() {}

    /**
     * Pack partial stacks from {@code sorted} into bundles that are already present in the list.
     * Returns a new list with absorbed partials removed and the bundle items' meta updated.
     *
     * @param sorted   sorted, stack-merged item list (typically from {@link SortEngine#sortAndMerge})
     * @param entryCap maximum distinct entries per bundle; ≤ 0 means weight-only limit
     * @return the (possibly shorter) modified list
     */
    public static List<ItemStack> pack(List<ItemStack> sorted, int entryCap) {
        // Locate bundles (bins) by index
        List<Integer> bundleIndices = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            ItemStack is = sorted.get(i);
            if (is != null && is.getType() == Material.BUNDLE) {
                bundleIndices.add(i);
            }
        }
        if (bundleIndices.isEmpty()) {
            Log.debug("BundlePacker: no bundles present, skipping");
            return sorted;
        }

        // Locate packable partial stacks (candidates) by index
        List<Integer> candidateIndices = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            ItemStack is = sorted.get(i);
            if (is != null && is.getAmount() < is.getType().getMaxStackSize() && canBundle(is)) {
                candidateIndices.add(i);
            }
        }
        if (candidateIndices.isEmpty()) {
            Log.debug("BundlePacker: no packable partial stacks found");
            return sorted;
        }

        // Sort candidates descending by weight (First-Fit-Decreasing)
        candidateIndices.sort((a, b) -> stackWeight(sorted.get(b)) - stackWeight(sorted.get(a)));

        // Work on a mutable copy so originals are untouched until we know at least one was packed
        List<ItemStack> result = new ArrayList<>(sorted);
        Set<Integer> absorbed = new HashSet<>();

        for (int ci : candidateIndices) {
            ItemStack candidate = result.get(ci);
            int weight = stackWeight(candidate);

            for (int bi : bundleIndices) {
                ItemStack bundleItem = result.get(bi);
                BundleMeta meta = (BundleMeta) bundleItem.getItemMeta();
                if (meta == null) continue;

                if (64 - bundleUsedWeight(meta) < weight) continue;

                // Entry-cap check: merging into an existing entry costs no new entry slot
                if (entryCap > 0 && !isExistingEntry(meta, candidate) && distinctEntries(meta) >= entryCap) continue;

                // Fits — pack it. Prepend so Minecraft shows the most-recently-added item first.
                List<ItemStack> items = new ArrayList<>();
                items.add(candidate.clone());
                items.addAll(meta.getItems());
                meta.setItems(items);
                bundleItem.setItemMeta(meta);
                absorbed.add(ci);
                Log.debug("BundlePacker: packed " + candidate.getType() + " x" + candidate.getAmount()
                        + " into bundle (slot index " + bi + ")");
                break;
            }
        }

        if (absorbed.isEmpty()) {
            return sorted;
        }

        List<ItemStack> out = new ArrayList<>(result.size() - absorbed.size());
        for (int i = 0; i < result.size(); i++) {
            if (!absorbed.contains(i)) {
                out.add(result.get(i));
            }
        }
        Log.debug("BundlePacker: absorbed " + absorbed.size() + " partial stack(s)");
        return out;
    }

    // -------------------------------------------------------------------------
    // Public helpers (tested directly in BundlePackerTest)
    // -------------------------------------------------------------------------

    /**
     * Weight a single ItemStack occupies inside a bundle.
     * Formula: {@code amount × (64 / maxStackSize)}, integer division.
     */
    public static int stackWeight(ItemStack is) {
        int maxStack = is.getType().getMaxStackSize();
        if (maxStack <= 0) return 64;
        return is.getAmount() * 64 / maxStack;
    }

    /** Total weight already consumed by all items inside a bundle. */
    public static int bundleUsedWeight(BundleMeta meta) {
        int total = 0;
        for (ItemStack is : meta.getItems()) {
            if (is != null) total += stackWeight(is);
        }
        return total;
    }

    /** Number of distinct item types/meta already stored in the bundle. */
    public static int distinctEntries(BundleMeta meta) {
        List<ItemStack> seen = new ArrayList<>();
        for (ItemStack is : meta.getItems()) {
            if (is != null && seen.stream().noneMatch(s -> s.isSimilar(is))) {
                seen.add(is);
            }
        }
        return seen.size();
    }

    /**
     * Returns true if the candidate item is similar (same type + meta) to any item already in
     * the bundle, meaning it would merge into an existing entry rather than adding a new one.
     */
    public static boolean isExistingEntry(BundleMeta meta, ItemStack candidate) {
        for (ItemStack is : meta.getItems()) {
            if (is != null && is.isSimilar(candidate)) return true;
        }
        return false;
    }

    /**
     * Returns true if {@code is} may be placed into a bundle.
     *
     * <p>Shulker boxes cannot go in bundles (Minecraft hard limit). Bundles are excluded by
     * policy — keeping them empty as containers is more useful than nesting them. Non-stackable
     * items (maxStackSize ≤ 1) consume an entire bundle for a single item with no net slot
     * saving, so they are also excluded.
     */
    public static boolean canBundle(ItemStack is) {
        if (is == null) return false;
        Material mat = is.getType();
        if (mat == Material.BUNDLE) return false;
        if (mat.name().contains("SHULKER_BOX")) return false;
        if (mat.getMaxStackSize() <= 1) return false;
        return true;
    }
}
