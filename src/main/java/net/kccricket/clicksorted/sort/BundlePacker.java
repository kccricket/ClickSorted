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
import org.bukkit.Material;
import org.bukkit.block.Beehive;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

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
 *   <li>Each bundle's total weight stays ≤ {@link #BUNDLE_WEIGHT_CAPACITY}. Weight follows vanilla:
 *       an ordinary item costs {@code 64 / maxStackSize} (honouring a per-item {@code max_stack_size}
 *       component where set), a nested bundle costs {@link #NESTED_BUNDLE_WEIGHT} plus its own
 *       contents' weight, and a beehive or bee nest holding bees costs a whole bundle.</li>
 *   <li>Distinct-entry count stays ≤ {@code entryCap} per bundle (0 = weight-only limit).</li>
 *   <li>Remainders heavier than {@link #MAX_PACK_WEIGHT} are never bundled (inefficient trade).</li>
 *   <li>Ineligible bundle contents (non-stackable, nested shulkers, nested bundles, bee-filled
 *       beehives/nests) are never pooled or moved — they are priced but always retained in place.</li>
 *   <li>Bundles (policy) and shulker boxes (hard limit) are never packed into bundles.</li>
 *   <li>A bundle whose own material or display name is blocked by the blacklist is never used as a
 *       bin: its contents are not unpacked and no new items are packed into it.</li>
 * </ul>
 */
public final class BundlePacker {

    /** Total weight a single bundle can hold. See {@link #stackWeight} for how an item's weight is computed. */
    public static final int BUNDLE_WEIGHT_CAPACITY = 64;

    /**
     * Weight a nested bundle costs on top of its own contents' weight — vanilla's
     * {@code BUNDLE_IN_BUNDLE_WEIGHT} fraction of {@code 1/16}, on this class's 64-unit scale.
     */
    public static final int NESTED_BUNDLE_WEIGHT = BUNDLE_WEIGHT_CAPACITY / 16;

    /**
     * Deepest bundle nesting priced exactly; each level already costs at least
     * {@link #NESTED_BUNDLE_WEIGHT}, so beyond this depth the true weight is already ≥ capacity.
     * Anything nested deeper (only reachable via hand-crafted NBT, not normal play) is charged as a
     * full bundle rather than recursed into further, mirroring vanilla's {@code BundleItem} collapsing
     * an unrepresentable weight to "full".
     */
    private static final int MAX_NEST_DEPTH = BUNDLE_WEIGHT_CAPACITY / NESTED_BUNDLE_WEIGHT;

    /**
     * Ceiling for a single stack's computed weight — far above {@link #BUNDLE_WEIGHT_CAPACITY} but low
     * enough that summing every entry a bundle could ever hold cannot overflow an {@code int}. Only
     * reachable via a pathological (hand-crafted NBT) amount or nesting; ordinary play never approaches it.
     */
    private static final int MAX_STACK_WEIGHT = BUNDLE_WEIGHT_CAPACITY * BUNDLE_WEIGHT_CAPACITY;

    /**
     * Upper weight bound for a remainder to be bundle-eligible. Spending more than half a bundle's
     * capacity on a single stack — to reclaim one slot — is never an efficient trade; such remainders
     * are left loose. Remainders at or below this weight are the small odds-and-ends bundles are meant
     * for.
     */
    public static final int MAX_PACK_WEIGHT = BUNDLE_WEIGHT_CAPACITY / 2;

    private BundlePacker() {}

    /**
     * Convenience overload with no blacklist; preserves existing callers (tests included).
     *
     * @see #packIntoBundles(Map, Map, List, int, BundleBlacklist)
     */
    public static List<ItemStack> packIntoBundles(Map<SortKey, Long> loosePool,
                                                  Map<SortKey, ItemStack> samples,
                                                  List<ItemStack> bundles, int entryCap) {
        return packIntoBundles(loosePool, samples, bundles, entryCap, BundleBlacklist.EMPTY);
    }

    /**
     * Pack the bundleable remainders of a pooled item multiset into the given bundles, returning the
     * loose stacks that should stay out in the inventory. Mutates the bundle {@link ItemStack}s in
     * {@code bundles} in place; layout of the returned loose stacks is left to the caller (the sort).
     *
     * <p>Entries in {@code blacklist} are treated as ineligible for bundling: loose blacklisted items
     * are not pooled by the caller and blacklisted items already inside bundles are retained (not
     * unpacked) by this method. Additionally, a bundle whose <em>own</em> material or display name is
     * blocked by the blacklist is skipped entirely as a bin — its contents are left untouched and no
     * new items are packed into it; it is still sorted normally by the caller.
     *
     * @param loosePool  pooled amounts per item type for the loose eligible items; bundle contents are
     *                   merged in by this method (mutated)
     * @param samples    a representative ItemStack per type (mutated: bundle-only types are added)
     * @param bundles    the bundle ItemStacks to use as bins; mutated in place ({@code null}/empty ok)
     * @param entryCap   maximum distinct entries per bundle; ≤ 0 means weight-only limit
     * @param blacklist  materials and display names that must not be packed into or unpacked from bundles
     * @return the leftover loose stacks (full stacks plus any un-bundled remainder) for every type
     */
    public static List<ItemStack> packIntoBundles(Map<SortKey, Long> loosePool,
                                                  Map<SortKey, ItemStack> samples,
                                                  List<ItemStack> bundles, int entryCap,
                                                  BundleBlacklist blacklist) {
        Map<SortKey, Set<Integer>> originBins = new HashMap<>();

        // Bins = each provided bundle; pool its eligible contents into the loose pool.
        List<Bin> bins = new ArrayList<>();
        if (bundles != null) {
            for (ItemStack b : bundles) {
                if (b != null && isBundle(b.getType()) && !blacklist.blocks(b)) {
                    addBin(bins, b, loosePool, samples, originBins, blacklist);
                }
            }
        }

        if (loosePool.isEmpty()) {
            return new ArrayList<>();
        }

        // Split each total into full stacks plus a single remainder.
        Map<SortKey, TypePlan> plans = new LinkedHashMap<>();
        for (Map.Entry<SortKey, Long> e : loosePool.entrySet()) {
            plans.put(e.getKey(), new TypePlan(samples.get(e.getKey()), e.getValue()));
        }

        // Place bundleable remainders lightest-first (best-fit, origin-bundle preference).
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

        for (Bin bin : bins) {
            bin.flush();
        }

        // Leftover loose stacks: full stacks plus any un-bundled remainder, per type.
        List<ItemStack> leftover = new ArrayList<>();
        for (Map.Entry<SortKey, TypePlan> e : plans.entrySet()) {
            SortKey key = e.getKey();
            TypePlan plan = e.getValue();
            for (long f = 0; f < plan.fullStacks; f++) {
                leftover.add(stackOf(samples.get(key), plan.maxStack));
            }
            if (plan.remAmt > 0 && !(plan.bundleable && plan.remPlaced)) {
                leftover.add(stackOf(samples.get(key), plan.remAmt));
            }
        }
        return leftover;
    }

    /** Construct a {@link Bin} for {@code bundleItem}, pooling its eligible contents into the pool. */
    private static void addBin(List<Bin> bins, ItemStack bundleItem, Map<SortKey, Long> totals,
                               Map<SortKey, ItemStack> samples, Map<SortKey, Set<Integer>> originBins,
                               BundleBlacklist blacklist) {
        Bin bin = Bin.of(bins.size(), bundleItem, blacklist);
        if (bin == null) return;
        for (ItemStack is : bin.pooled) {
            SortKey key = SortKey.poolKey(is);
            // Lambda, not Long::sum: a method ref binds the boxed map values straight to
            // primitive params, tripping JDT's "needs unchecked conversion" null warning.
            totals.merge(key, (long) is.getAmount(), (a, b) -> Long.sum(a, b));
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

    /** A fresh stack of {@code sample}'s type/meta with the given amount. */
    private static ItemStack stackOf(ItemStack sample, int amount) {
        ItemStack stack = sample.clone();
        stack.setAmount(amount);
        return stack;
    }

    /**
     * Weight a single ItemStack occupies inside a bundle, mirroring vanilla's
     * {@code BundleContents#getWeight}: a nested bundle costs {@link #NESTED_BUNDLE_WEIGHT} plus its
     * own contents' weight (its contents are never unpacked to compute this — they stay opaque), a
     * beehive or bee nest currently holding bees costs a whole bundle, and everything else costs
     * {@code amount × (64 / maxStackSize)} — honouring a per-item {@code max_stack_size} component
     * where one is set, exactly as vanilla does.
     */
    public static int stackWeight(ItemStack is) {
        return weightOf(is, is.getAmount(), 0);
    }

    /** {@link #stackWeight}, but for an arbitrary {@code amount} of {@code sample}'s item/meta. */
    private static int weightOf(ItemStack sample, int amount) {
        return weightOf(sample, amount, 0);
    }

    private static int weightOf(ItemStack sample, int amount, int depth) {
        if (sample == null || amount <= 0) return 0;
        Material mat = sample.getType();
        if (isBundle(mat)) {
            // Depth guard: only reachable via hand-crafted NBT, never normal play. Charge as full
            // rather than recurse further, mirroring vanilla collapsing an unrepresentable weight to
            // "full" (BundleItem#getWeightSafe).
            if (depth >= MAX_NEST_DEPTH) {
                return saturate((long) amount * BUNDLE_WEIGHT_CAPACITY);
            }
            int contents = NESTED_BUNDLE_WEIGHT;
            if (sample.getItemMeta() instanceof BundleMeta bm) {
                for (ItemStack inner : bm.getItems()) {
                    if (inner != null) contents += weightOf(inner, inner.getAmount(), depth + 1);
                }
            }
            // A single bundle can never occupy more than one full bundle's worth of its parent's
            // capacity, however it was actually filled — clamp per unit before multiplying by amount.
            return saturate((long) amount * Math.min(contents, BUNDLE_WEIGHT_CAPACITY));
        }
        if (isBeeFilled(sample)) {
            return saturate((long) amount * BUNDLE_WEIGHT_CAPACITY);
        }
        return weight(amount, maxStackOf(sample));
    }

    /** Bundle weight {@code amount} of a stack occupies given its {@code maxStack} size. */
    private static int weight(int amount, int maxStack) {
        if (maxStack <= 0) return BUNDLE_WEIGHT_CAPACITY;
        return saturate((long) amount * BUNDLE_WEIGHT_CAPACITY / maxStack);
    }

    /** Clamp a computed weight into {@code [0, MAX_STACK_WEIGHT]}, tolerating a pathological input. */
    private static int saturate(long w) {
        return (int) Math.min(MAX_STACK_WEIGHT, Math.max(0L, w));
    }

    /**
     * True for a beehive/bee nest whose stored block state currently holds at least one bee —
     * vanilla charges it a whole bundle regardless of how many bees, rather than the material's
     * ordinary per-item weight. The material check runs first since reading the block state snapshot
     * is comparatively costly and must not run for every ordinary item.
     */
    private static boolean isBeeFilled(ItemStack is) {
        Material mat = is.getType();
        if (mat != Material.BEEHIVE && mat != Material.BEE_NEST) return false;
        return is.getItemMeta() instanceof BlockStateMeta bsm
                && bsm.hasBlockState()
                && bsm.getBlockState() instanceof Beehive hive
                && hive.getEntityCount() > 0;
    }

    /**
     * Max stack size for {@code is}: the per-item {@code max_stack_size} component when one is set
     * (what vanilla's bundle-weight formula divides by), otherwise the item's Material default.
     */
    private static int maxStackOf(ItemStack is) {
        ItemMeta meta = is.getItemMeta();
        if (meta != null && meta.hasMaxStackSize()) {
            int n = meta.getMaxStackSize();
            if (n > 0) return n;
        }
        int n = is.getType().getMaxStackSize();
        return n > 0 ? n : BUNDLE_WEIGHT_CAPACITY;
    }

    /** Number of distinct item types/meta among a list of stacks. */
    private static int distinctEntriesOf(Iterable<ItemStack> items) {
        Set<SortKey> seen = new HashSet<>();
        for (ItemStack is : items) {
            if (is != null) {
                seen.add(SortKey.poolKey(is));
            }
        }
        return seen.size();
    }

    /**
     * Returns true if {@code mat} is a bundle (the undyed {@code BUNDLE} or any dyed color
     * variant such as {@code WHITE_BUNDLE}, {@code RED_BUNDLE}, etc., added in 1.21.2).
     */
    public static boolean isBundle(Material mat) {
        return mat != null && (mat == Material.BUNDLE || mat.name().endsWith("_BUNDLE"));
    }

    /**
     * Returns true if {@code is} may be placed into a bundle.
     *
     * <p>Shulker boxes cannot go in bundles (Minecraft hard limit). Bundles are excluded by
     * policy — keeping them empty as containers is more useful than nesting them. Non-stackable
     * items (maxStackSize ≤ 1, honouring a per-item {@code max_stack_size} component where one is
     * set) consume an entire bundle for a single item with no net slot saving, so they are also
     * excluded. A beehive or bee nest currently holding bees is excluded too — it already costs a
     * whole bundle (see {@link #stackWeight}) and could never actually be repacked, so pooling it
     * would only unpack it from wherever it already sits for no benefit.
     */
    public static boolean canBundle(ItemStack is) {
        if (is == null) return false;
        Material mat = is.getType();
        if (isBundle(mat)) return false;
        if (mat.name().contains("SHULKER_BOX")) return false;
        if (maxStackOf(is) <= 1) return false;
        if (isBeeFilled(is)) return false;
        return true;
    }

    /**
     * Returns true if {@code is} may be placed into a bundle and is not blocked by the blacklist.
     *
     * @param blacklist materials and display names excluded from bundle packing/unpacking; not null
     */
    public static boolean canBundle(ItemStack is, BundleBlacklist blacklist) {
        return canBundle(is) && !blacklist.blocks(is);
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

        // sample is never a bundle or a bee-filled hive (canBundle excludes both from loosePool), so
        // remWeight only ever needs weightOf's ordinary-item case in practice; it still goes through
        // the shared dispatcher so this stays true even if canBundle's exclusions ever change.
        TypePlan(ItemStack sample, long total) {
            this.maxStack = maxStackOf(sample);
            this.fullStacks = total / maxStack;
            this.remAmt = (int) (total % maxStack);
            this.remWeight = weightOf(sample, remAmt);
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

        private Bin(int id, ItemStack bundleItem, BundleMeta meta, BundleBlacklist blacklist) {
            this.id = id;
            this.bundleItem = bundleItem;
            this.meta = meta;
            this.items = new ArrayList<>();
            this.pooled = new ArrayList<>();
            for (ItemStack is : meta.getItems()) {
                if (is == null) continue;
                if (canBundle(is, blacklist)) {
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
        static Bin of(int id, ItemStack bundleItem, BundleBlacklist blacklist) {
            if (!(bundleItem.getItemMeta() instanceof BundleMeta meta)) return null;
            return new Bin(id, bundleItem, meta, blacklist);
        }

        /** Whether a remainder of {@code weight} fits within both the weight and entry-cap limits. */
        boolean canAccept(int weight, int entryCap) {
            if (BUNDLE_WEIGHT_CAPACITY - usedWeight < weight) return false;
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
