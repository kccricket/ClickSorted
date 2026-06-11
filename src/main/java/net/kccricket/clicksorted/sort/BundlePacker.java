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
import org.bukkit.inventory.meta.BundleMeta;

import java.util.*;

/**
 * Consolidates a player's main storage by the <em>pool-and-repack</em> model: every bundle-eligible
 * item — loose in the inventory <em>and</em> already inside any bundle — is dissolved into a pool
 * keyed by item type, then repacked from scratch.
 *
 * <p>Because the result is a pure function of the item multiset (not of where items currently sit),
 * a second run on an unchanged inventory is a no-op: there is no oscillation and no eviction search,
 * just {@code O(n log n)} work. For each item type the total amount {@code T} is split into full
 * stacks (which always belong in the inventory) and a single remainder; the remainder goes into one
 * bundle when its weight is at or below {@link #MAX_PACK_WEIGHT}, otherwise it stays loose.
 *
 * <p>Invariants:
 * <ul>
 *   <li>Each bundle's total weight stays ≤ 64 (item weight = 64 / maxStackSize per item).</li>
 *   <li>Distinct-entry count stays ≤ {@code entryCap} per bundle (0 = weight-only limit).</li>
 *   <li>Remainders heavier than {@link #MAX_PACK_WEIGHT} are never bundled (inefficient trade).</li>
 *   <li>Ineligible bundle contents (non-stackable, nested shulkers) are never pooled or moved.</li>
 *   <li>Bundles (policy) and shulker boxes (hard limit) are never packed into bundles.</li>
 * </ul>
 */
public final class BundlePacker {

    /**
     * Upper weight bound for a remainder to be bundle-eligible. A bundle holds 64 weight, so spending
     * more than half of it on a single stack — to reclaim one slot — is never an efficient trade;
     * such remainders are left loose. Remainders at or below this weight are the small odds-and-ends
     * bundles are meant for.
     */
    public static final int MAX_PACK_WEIGHT = 32;

    private BundlePacker() {}

    /**
     * Repack all eligible items across the given main-inventory slots and bundles by the
     * pool-and-redistribute model. Mutates {@code inv} (slot-aligned, {@code null} = empty/free slot)
     * and the bundle {@link ItemStack}s in {@code inv} and {@code hotbarBundles} in place.
     *
     * <p>Bins are every {@code BUNDLE} entry in {@code inv} plus every entry in {@code hotbarBundles}.
     * Pool sources are {@code inv}'s non-bundle eligible entries plus every bundle's eligible contents;
     * free slots are {@code inv}'s {@code null} entries. Ineligible items (loose or in a bundle) are
     * never touched.
     *
     * @param inv            slot-aligned main-storage items ({@code null} = empty); mutated in place
     * @param hotbarBundles  bundle ItemStacks from the hotbar to use as bins; mutated in place
     * @param entryCap       maximum distinct entries per bundle; ≤ 0 means weight-only limit
     * @return net main-inventory slots freed (≥ 0)
     */
    public static int repack(List<ItemStack> inv, List<ItemStack> hotbarBundles, int entryCap) {
        Map<SortKey, Long> totals = new LinkedHashMap<>();
        Map<SortKey, ItemStack> samples = new LinkedHashMap<>();
        Map<SortKey, Set<Integer>> originBins = new HashMap<>();
        Map<SortKey, List<int[]>> invPositions = new HashMap<>();

        // 1. Bins = every bundle in main storage, then every hotbar bundle. Pool each bundle's
        //    eligible contents (ineligible contents are retained in the bin and never moved).
        List<Bin> bins = new ArrayList<>();
        for (ItemStack is : inv) {
            if (is != null && is.getType() == Material.BUNDLE) {
                addBin(bins, is, totals, samples, originBins);
            }
        }
        if (hotbarBundles != null) {
            for (ItemStack b : hotbarBundles) {
                if (b != null && b.getType() == Material.BUNDLE) {
                    addBin(bins, b, totals, samples, originBins);
                }
            }
        }

        // 2. Pool eligible loose items from the inventory, recording their slots, then clear those
        //    slots so layout can re-place from scratch. Bundles and ineligible items are left alone.
        int eligibleInvSlots = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack is = inv.get(i);
            if (is == null || is.getType() == Material.BUNDLE || !canBundle(is)) continue;
            SortKey key = new SortKey(is, SortingMethod.NAME);
            totals.merge(key, (long) is.getAmount(), Long::sum);
            samples.putIfAbsent(key, is);
            invPositions.computeIfAbsent(key, k -> new ArrayList<>()).add(new int[]{i, is.getAmount()});
            inv.set(i, null);
            eligibleInvSlots++;
        }

        if (totals.isEmpty()) {
            return 0;
        }

        // 3. Merge by type: split each total into full stacks (inventory) and a single remainder.
        Map<SortKey, TypePlan> plans = new LinkedHashMap<>();
        for (Map.Entry<SortKey, Long> e : totals.entrySet()) {
            int maxStack = samples.get(e.getKey()).getType().getMaxStackSize();
            if (maxStack <= 0) maxStack = 64;
            plans.put(e.getKey(), new TypePlan(maxStack, e.getValue()));
        }

        // Place bundleable remainders lightest-first (best-fit, current-bundle preference).
        List<SortKey> remainders = new ArrayList<>();
        for (Map.Entry<SortKey, TypePlan> e : plans.entrySet()) {
            if (e.getValue().bundleable) remainders.add(e.getKey());
        }
        remainders.sort(Comparator.comparingInt((SortKey k) -> plans.get(k).remWeight).thenComparing(k -> k));
        for (SortKey key : remainders) {
            TypePlan plan = plans.get(key);
            Bin target = chooseBin(bins, plan.remWeight, entryCap, originBins.getOrDefault(key, Set.of()));
            if (target != null) {
                ItemStack stack = samples.get(key).clone();
                stack.setAmount(plan.remAmt);
                target.place(stack, plan.remWeight);
                plan.remPlaced = true;
                Log.debug("BundlePacker: placed remainder " + key.getMaterial() + " x" + plan.remAmt
                        + " (weight " + plan.remWeight + ") into bundle #" + target.id);
            }
        }

        // 4. Lay out the inventory: keep the largest existing stack in its slot, spill the rest into
        //    free slots, and fall back to a bundle for any stack that cannot find a slot.
        int filled = 0;
        for (Map.Entry<SortKey, TypePlan> e : plans.entrySet()) {
            SortKey key = e.getKey();
            TypePlan plan = e.getValue();

            List<Integer> targets = new ArrayList<>();
            for (long f = 0; f < plan.fullStacks; f++) targets.add(plan.maxStack);
            if (plan.remAmt > 0 && !(plan.bundleable && plan.remPlaced)) targets.add(plan.remAmt);
            if (targets.isEmpty()) continue;

            // Largest target into the slot that held the largest stack of this type — minimizing churn.
            List<int[]> positions = new ArrayList<>(invPositions.getOrDefault(key, List.of()));
            positions.sort((a, b) -> a[1] != b[1] ? b[1] - a[1] : a[0] - b[0]);

            int ti = 0;
            for (int[] pos : positions) {
                if (ti >= targets.size()) break;
                inv.set(pos[0], stackOf(samples.get(key), targets.get(ti++)));
                filled++;
            }
            for (; ti < targets.size(); ti++) {
                int slot = nextFreeSlot(inv);
                if (slot >= 0) {
                    inv.set(slot, stackOf(samples.get(key), targets.get(ti)));
                    filled++;
                } else {
                    // Graceful fallback: no free slot (a bundle over-filled beyond 64 can produce
                    // more full stacks than there are slots). Leave the excess in a bundle.
                    spillIntoBins(bins, key, samples.get(key), targets.get(ti),
                            originBins.getOrDefault(key, Set.of()));
                }
            }
        }

        for (Bin bin : bins) {
            bin.flush();
        }

        return Math.max(0, eligibleInvSlots - filled);
    }

    /** Construct a {@link Bin} for {@code bundleItem}, pooling its eligible contents into the pool. */
    private static void addBin(List<Bin> bins, ItemStack bundleItem, Map<SortKey, Long> totals,
                               Map<SortKey, ItemStack> samples, Map<SortKey, Set<Integer>> originBins) {
        Bin bin = Bin.of(bins.size(), bundleItem);
        if (bin == null) return;
        for (ItemStack is : bin.pooled) {
            SortKey key = new SortKey(is, SortingMethod.NAME);
            totals.merge(key, (long) is.getAmount(), Long::sum);
            samples.putIfAbsent(key, is);
            originBins.computeIfAbsent(key, k -> new HashSet<>()).add(bin.id);
        }
        bins.add(bin);
    }

    /**
     * Best-fit bin for a remainder of the given weight: the fullest bundle that still has weight and
     * entry-cap room. Bundles the remainder already came from are preferred over all others, so an
     * unchanged layout repacks to itself without churn.
     */
    private static Bin chooseBin(List<Bin> bins, int weight, int entryCap, Set<Integer> origins) {
        Bin best = null;
        boolean bestIsOrigin = false;
        for (Bin bin : bins) {
            if (!bin.canAccept(weight, entryCap)) continue;
            boolean isOrigin = origins.contains(bin.id);
            if (best == null
                    || (isOrigin && !bestIsOrigin)
                    || (isOrigin == bestIsOrigin && bin.usedWeight > best.usedWeight)) {
                best = bin;
                bestIsOrigin = isOrigin;
            }
        }
        return best;
    }

    /** Fallback for an inventory stack that found no free slot: pour it back into bundles by weight. */
    private static void spillIntoBins(List<Bin> bins, SortKey key, ItemStack sample, int amount, Set<Integer> origins) {
        int maxStack = sample.getType().getMaxStackSize();
        if (maxStack <= 0) maxStack = 64;
        // Origin bins first, then any bin with room.
        int remaining = amount;
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (Bin bin : bins) {
                if (remaining <= 0) break;
                boolean isOrigin = origins.contains(bin.id);
                if (pass == 0 ? !isOrigin : isOrigin) continue;
                int roomWeight = 64 - bin.usedWeight;
                if (roomWeight <= 0) continue;
                int roomAmt = roomWeight * maxStack / 64;
                int add = Math.min(remaining, roomAmt);
                if (add <= 0) continue;
                ItemStack stack = sample.clone();
                stack.setAmount(add);
                bin.place(stack, add * 64 / maxStack);
                remaining -= add;
            }
        }
        if (remaining > 0) {
            Log.warning("BundlePacker: no room to place " + remaining + " of " + key.getMaterial()
                    + "; items left unplaced");
        }
    }

    /** A fresh stack of {@code sample}'s type/meta with the given amount. */
    private static ItemStack stackOf(ItemStack sample, int amount) {
        ItemStack stack = sample.clone();
        stack.setAmount(amount);
        return stack;
    }

    /** First {@code null} (free) slot in {@code inv}, or -1 if the list is full. */
    private static int nextFreeSlot(List<ItemStack> inv) {
        for (int i = 0; i < inv.size(); i++) {
            if (inv.get(i) == null) return i;
        }
        return -1;
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
        return distinctEntriesOf(meta.getItems());
    }

    /** Number of distinct item types/meta among a list of stacks. */
    private static int distinctEntriesOf(Iterable<ItemStack> items) {
        List<ItemStack> seen = new ArrayList<>();
        for (ItemStack is : items) {
            if (is != null && !containsSimilar(seen, is)) {
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
        return containsSimilar(meta.getItems(), candidate);
    }

    /** Shared similarity predicate: true if any non-null item in {@code items} isSimilar to {@code candidate}. */
    private static boolean containsSimilar(Iterable<ItemStack> items, ItemStack candidate) {
        for (ItemStack is : items) {
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

    /**
     * Per-type repack plan: how a pooled total splits into inventory full stacks and a single
     * bundle-or-loose remainder.
     */
    private static final class TypePlan {
        final int maxStack;
        final long fullStacks;
        final int remAmt;
        final int remWeight;
        final boolean bundleable;
        boolean remPlaced;

        TypePlan(int maxStack, long total) {
            this.maxStack = maxStack;
            this.fullStacks = total / maxStack;
            this.remAmt = (int) (total % maxStack);
            this.remWeight = remAmt * 64 / maxStack;
            this.bundleable = remAmt > 0 && remWeight <= MAX_PACK_WEIGHT;
        }
    }

    /**
     * Mutable per-bundle accumulator. Pools the bundle's eligible contents up front (leaving its
     * ineligible contents as retained {@code items}), tracks the retained weight and distinct count,
     * and grows them as remainders are placed. Flushed back to the bundle ItemStack once at the end.
     */
    private static final class Bin {
        final int id;
        final ItemStack bundleItem;
        final BundleMeta meta;
        final List<ItemStack> items;   // retained (ineligible) contents + placed remainders
        final List<ItemStack> pooled;  // eligible contents removed for repacking
        int usedWeight;
        int distinct;
        boolean dirty;

        private Bin(int id, ItemStack bundleItem, BundleMeta meta) {
            this.id = id;
            this.bundleItem = bundleItem;
            this.meta = meta;
            this.items = new ArrayList<>();
            this.pooled = new ArrayList<>();
            for (ItemStack is : meta.getItems()) {
                if (is == null) continue;
                if (canBundle(is)) {
                    pooled.add(is);
                } else {
                    items.add(is);
                }
            }
            for (ItemStack is : items) usedWeight += stackWeight(is);
            this.distinct = distinctEntriesOf(items);
            // Removing pooled contents already changes the bundle; flush even if nothing is placed back.
            this.dirty = !pooled.isEmpty();
        }

        /** Wrap a bundle ItemStack, or {@code null} if it has no usable {@link BundleMeta}. */
        static Bin of(int id, ItemStack bundleItem) {
            if (!(bundleItem.getItemMeta() instanceof BundleMeta meta)) return null;
            return new Bin(id, bundleItem, meta);
        }

        /** Whether a remainder of {@code weight} fits within both the weight and entry-cap limits. */
        boolean canAccept(int weight, int entryCap) {
            if (64 - usedWeight < weight) return false;
            if (entryCap > 0 && distinct >= entryCap) return false;
            return true;
        }

        /** Append a new entry, updating the running weight and distinct count. */
        void place(ItemStack stack, int weight) {
            items.add(stack);
            usedWeight += weight;
            distinct++;
            dirty = true;
        }

        /** Write accumulated items back to the bundle ItemStack, once, if anything changed. */
        void flush() {
            if (!dirty) return;
            meta.setItems(items);
            bundleItem.setItemMeta(meta);
        }
    }
}
