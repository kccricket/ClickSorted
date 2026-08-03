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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pure assertion helpers for the self-test — no Bukkit server required, so this class is
 * unit-testable in isolation. Every check returns {@link Optional#empty()} on success or a
 * human-readable failure description otherwise; none of them consult {@link net.kccricket.clicksorted.sort.SortEngine}
 * or the other production sort algorithms, so a failure here can never be "the engine disagreeing
 * with itself" — it is always a comparison against an independently-derived golden layout, a raw
 * item count, or a structural property of the output alone.
 */
public final class SelfTestEvaluator {

    private SelfTestEvaluator() {
    }

    /**
     * Compares {@code actual} against a golden {@code expected} layout, slot by slot. Two stacks
     * are equal when both are empty, or when {@link ItemStack#isSimilar} and their amounts match
     * (meta-aware — a renamed/enchanted/damaged item is never conflated with a plain one).
     */
    public static Optional<String> compareLayout(ItemStack[] actual, ItemStack[] expected) {
        if (actual.length != expected.length) {
            return Optional.of("length mismatch: actual=" + actual.length + " expected=" + expected.length);
        }
        for (int i = 0; i < actual.length; i++) {
            if (!stacksEqual(actual[i], expected[i])) {
                return Optional.of("slot " + i + " mismatch: expected " + describe(expected[i])
                        + " but got " + describe(actual[i])
                        + "\n  expected: " + Layout.render(expected)
                        + "\n  actual:   " + Layout.render(actual));
            }
        }
        return Optional.empty();
    }

    private static boolean stacksEqual(ItemStack a, ItemStack b) {
        boolean aEmpty = isEmpty(a);
        boolean bEmpty = isEmpty(b);
        if (aEmpty || bEmpty) {
            return aEmpty == bEmpty;
        }
        return a.isSimilar(b) && a.getAmount() == b.getAmount();
    }

    private static boolean isEmpty(ItemStack is) {
        return is == null || is.getType() == Material.AIR;
    }

    private static String describe(ItemStack is) {
        return isEmpty(is) ? "empty" : is.getType() + " x" + is.getAmount();
    }

    /**
     * Compares a before/after {@link ItemCensus} pair: everything present before a sort must either
     * still be in the inventory afterward or have been dropped on the ground, i.e.
     * {@code before == after + dropped}. Empty result means every item is accounted for: nothing
     * lost, nothing duplicated.
     */
    public static Optional<String> compareCensus(ItemCensus before, ItemCensus after, List<ItemStack> dropped) {
        // Conservation: everything present before a sort is either still in the inventory afterward
        // or was dropped on the ground — before == after + dropped.
        ItemCensus afterPlusDropped = after.plusDropped(dropped);
        if (before.matches(afterPlusDropped)) {
            return Optional.empty();
        }
        return Optional.of("item conservation violated:\n" + before.diff(afterPlusDropped));
    }

    /**
     * Fails if any slot in {@code fillOrder} is non-empty after an earlier (in fill order) slot
     * was empty — a sorted/consolidated layout must never leave a gap before the tail.
     */
    public static Optional<String> noGapBeforeNonEmpty(ItemStack[] contents, List<Integer> fillOrder) {
        boolean sawEmpty = false;
        for (int slot : fillOrder) {
            boolean empty = isEmpty(contents[slot]);
            if (empty) {
                sawEmpty = true;
            } else if (sawEmpty) {
                return Optional.of("slot " + slot + " is non-empty after an earlier empty slot in fill order "
                        + fillOrder);
            }
        }
        return Optional.empty();
    }

    /**
     * Fails if two or more slots in {@code slots} hold a partial (below max-stack) stack of the
     * same {@link SortKey} — a merged/consolidated layout should never leave a type fragmented
     * across more than one non-full stack.
     */
    public static Optional<String> noDuplicatePartialStacks(ItemStack[] contents, Set<Integer> slots) {
        Map<SortKey, Integer> partialSlot = new HashMap<>();
        for (int slot : slots) {
            ItemStack is = contents[slot];
            if (isEmpty(is) || BundlePacker.isBundle(is.getType())) {
                continue;
            }
            if (is.getAmount() >= is.getMaxStackSize()) {
                continue;
            }
            SortKey key = SortKey.poolKey(is);
            Integer existing = partialSlot.putIfAbsent(key, slot);
            if (existing != null) {
                return Optional.of(key + " has partial stacks in both slot " + existing + " and slot " + slot);
            }
        }
        return Optional.empty();
    }

    /** Fails if {@code before[slot] != after[slot]} for any {@code slot} in {@code protectedSlots}. */
    public static Optional<String> protectedSlotsUnchanged(ItemStack[] before, ItemStack[] after,
                                                            Set<Integer> protectedSlots) {
        for (int slot : protectedSlots) {
            if (!stacksEqual(before[slot], after[slot])) {
                return Optional.of("protected slot " + slot + " changed: was " + describe(before[slot])
                        + ", now " + describe(after[slot]));
            }
        }
        return Optional.empty();
    }

    /**
     * Fails if any bundle in {@code contents} exceeds the 64-weight capacity or (when
     * {@code entryCap > 0}) holds more distinct entries than {@code entryCap}.
     */
    public static Optional<String> bundleCapacityValid(ItemStack[] contents, int entryCap) {
        for (int i = 0; i < contents.length; i++) {
            ItemStack is = contents[i];
            if (isEmpty(is) || !BundlePacker.isBundle(is.getType())) {
                continue;
            }
            if (!(is.getItemMeta() instanceof BundleMeta meta)) {
                continue;
            }
            int weight = 0;
            Set<SortKey> distinct = new HashSet<>();
            for (ItemStack inner : meta.getItems()) {
                if (inner == null) continue;
                weight += BundlePacker.stackWeight(inner);
                distinct.add(SortKey.poolKey(inner));
            }
            if (weight > BundlePacker.BUNDLE_WEIGHT_CAPACITY) {
                return Optional.of("slot " + i + " bundle is overweight: " + weight + " > "
                        + BundlePacker.BUNDLE_WEIGHT_CAPACITY);
            }
            if (entryCap > 0 && distinct.size() > entryCap) {
                return Optional.of("slot " + i + " bundle has " + distinct.size()
                        + " distinct entries, exceeding the cap of " + entryCap);
            }
        }
        return Optional.empty();
    }

    /**
     * Fails if re-running an operation over its own output changes anything — {@code BundlePacker}
     * and the consolidation paths are documented as idempotent (a second pass over an unchanged
     * inventory is a no-op).
     */
    public static Optional<String> idempotent(ItemStack[] firstPass, ItemStack[] secondPass) {
        Optional<String> mismatch = compareLayout(secondPass, firstPass);
        return mismatch.map(s -> "not idempotent: " + s);
    }
}
