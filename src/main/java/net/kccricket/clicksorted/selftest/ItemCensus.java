package net.kccricket.clicksorted.selftest;

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
import net.kccricket.clicksorted.sort.BundlePacker;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A meta-aware conservation multiset over an inventory (plus, optionally, a cursor and offhand
 * stack): every item is counted at its innermost, non-container level, recursing into any
 * {@link BundlePacker#isBundle bundle} (undyed or any dyed {@code *_BUNDLE} variant) and any
 * shulker box's block-state inventory. Leaf items are keyed by {@link SortKey#poolKey}, so a
 * renamed/enchanted/damaged item is never silently conflated with a plain one. Container objects
 * themselves (bundles, shulker boxes) are counted separately by {@link Material}, so "a bundle was
 * created or destroyed" is its own, independently checkable invariant from "an item inside a
 * bundle was created or destroyed".
 *
 * <p>Two censuses taken before and after a sort should be {@link #matches equal} — modulo any
 * items the sort legitimately dropped on the ground, which the caller folds in via
 * {@link #plusDropped}. A mismatch is exactly item duplication or item loss.
 */
public record ItemCensus(Map<SortKey, Long> leafCounts, Map<Material, Long> containerCounts) {

    /** Census of {@code slots} plus a cursor stack and an offhand stack (either may be null). */
    public static ItemCensus of(ItemStack[] slots, ItemStack cursor, ItemStack offhand) {
        Map<SortKey, Long> leaf = new LinkedHashMap<>();
        Map<Material, Long> containers = new LinkedHashMap<>();
        for (ItemStack is : slots) {
            add(leaf, containers, is);
        }
        add(leaf, containers, cursor);
        add(leaf, containers, offhand);
        return new ItemCensus(leaf, containers);
    }

    /** Census of {@code slots} alone (no cursor/offhand to consider). */
    public static ItemCensus of(ItemStack[] slots) {
        return of(slots, null, null);
    }

    private static void add(Map<SortKey, Long> leaf, Map<Material, Long> containers, ItemStack is) {
        if (is == null || is.getType() == Material.AIR) {
            return;
        }
        Material type = is.getType();
        if (BundlePacker.isBundle(type)) {
            containers.merge(type, (long) is.getAmount(), Long::sum);
            if (is.getItemMeta() instanceof BundleMeta meta) {
                for (ItemStack inner : meta.getItems()) {
                    add(leaf, containers, inner);
                }
            }
            return;
        }
        if (is.getItemMeta() instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox shulker) {
            containers.merge(type, (long) is.getAmount(), Long::sum);
            for (ItemStack inner : shulker.getInventory().getContents()) {
                add(leaf, containers, inner);
            }
            return;
        }
        leaf.merge(SortKey.poolKey(is), (long) is.getAmount(), Long::sum);
    }

    /** Returns a new census with {@code dropped} items (e.g. ground drops) folded in. */
    public ItemCensus plusDropped(List<ItemStack> dropped) {
        Map<SortKey, Long> leaf = new LinkedHashMap<>(leafCounts);
        Map<Material, Long> containers = new LinkedHashMap<>(containerCounts);
        for (ItemStack is : dropped) {
            add(leaf, containers, is);
        }
        return new ItemCensus(leaf, containers);
    }

    /** {@code true} if this census and {@code other} are identical: nothing lost, nothing duplicated. */
    public boolean matches(ItemCensus other) {
        return leafCounts.equals(other.leafCounts) && containerCounts.equals(other.containerCounts);
    }

    /** A human-readable per-entry diff against {@code other}; empty when {@link #matches}. */
    public String diff(ItemCensus other) {
        StringBuilder sb = new StringBuilder();
        diffLeaf(sb, other);
        diffContainers(sb, other);
        return sb.toString();
    }

    private void diffLeaf(StringBuilder sb, ItemCensus other) {
        Map<String, Long> before = new TreeMap<>();
        Map<String, Long> after = new TreeMap<>();
        for (var e : leafCounts.entrySet()) before.put(e.getKey().toString(), e.getValue());
        for (var e : other.leafCounts.entrySet()) after.put(e.getKey().toString(), e.getValue());
        var keys = new java.util.TreeSet<String>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            long b = before.getOrDefault(key, 0L);
            long a = after.getOrDefault(key, 0L);
            if (b != a) {
                sb.append(String.format("  %s: %d -> %d (%+d)%n", key, b, a, a - b));
            }
        }
    }

    private void diffContainers(StringBuilder sb, ItemCensus other) {
        var keys = new java.util.TreeSet<Material>();
        keys.addAll(containerCounts.keySet());
        keys.addAll(other.containerCounts.keySet());
        for (Material key : keys) {
            long b = containerCounts.getOrDefault(key, 0L);
            long a = other.containerCounts.getOrDefault(key, 0L);
            if (b != a) {
                sb.append(String.format("  %s (container): %d -> %d (%+d)%n", key, b, a, a - b));
            }
        }
    }
}
