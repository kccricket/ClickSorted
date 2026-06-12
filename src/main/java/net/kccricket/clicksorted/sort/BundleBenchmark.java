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
import net.kccricket.clicksorted.model.SortingMethod;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-situ micro-benchmark for the two hottest ClickSorted code paths:
 * {@link SortEngine#sortAndMerge} and the unified pack-and-sort path ({@link BundlePacker#packIntoBundles}
 * feeding {@link SortEngine#sortAndMerge}). It builds deliberately
 * worst-case fixtures (a fully-fragmented 54-slot container and a densely-pooled player inventory
 * with hotbar bundles) and times many iterations against the real server JVM, so an admin can see
 * the actual per-operation cost rather than guessing.
 *
 * <p>Pure and self-contained: it allocates {@link ItemStack}s and runs the algorithms directly, with
 * no Bukkit scheduling. The caller decides when to invoke it (the command runs it synchronously).
 */
public final class BundleBenchmark {

    /** A container worst case: a double-chest's worth of slots, all distinct, all partial stacks. */
    private static final int SORT_SLOTS = 54;
    /** Main-storage span for the repack fixture (player slots 9–44 ≈ 36 slots). */
    private static final int REPACK_MAIN_SLOTS = 36;
    /** Hotbar bundles used as bins in the repack fixture. */
    private static final int REPACK_HOTBAR_BUNDLES = 9;

    private BundleBenchmark() {}

    /** Timing distribution for one benchmarked path, in microseconds per operation. */
    public record Stats(double minUs, double medianUs, double p95Us, double maxUs,
                        long totalNanos, int iterations) {
    }

    /** Combined result for both benchmarked paths. */
    public record Result(Stats sort, Stats repack) {
    }

    /**
     * Run the benchmark for the given iteration count (a warm-up of ~10% is added on top to let the
     * JIT settle and is excluded from the reported timings).
     */
    public static Result run(int iterations) {
        int warmup = Math.max(50, iterations / 10);
        return new Result(
                benchSort(iterations, warmup),
                benchRepack(iterations, warmup));
    }

    // -------------------------------------------------------------------------
    // Sort path
    // -------------------------------------------------------------------------

    private static Stats benchSort(int iterations, int warmup) {
        ItemStack[] fixture = buildSortFixture();
        Set<Integer> slots = new LinkedHashSet<>();
        for (int i = 0; i < fixture.length; i++) {
            slots.add(i);
        }

        // sortAndMerge does not mutate the input array, so the same fixture is reused each pass.
        for (int i = 0; i < warmup; i++) {
            consume(SortEngine.sortAndMerge(fixture, slots, SortingMethod.NAME));
        }

        long[] samples = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long t0 = System.nanoTime();
            List<ItemStack> out = SortEngine.sortAndMerge(fixture, slots, SortingMethod.NAME);
            samples[i] = System.nanoTime() - t0;
            consume(out);
        }
        return summarize(samples);
    }

    /** A {@value #SORT_SLOTS}-slot array of distinct materials in partial stacks — maximal fragmentation. */
    private static ItemStack[] buildSortFixture() {
        List<Material> mats = bundleableMaterials(SORT_SLOTS);
        ItemStack[] items = new ItemStack[SORT_SLOTS];
        for (int i = 0; i < SORT_SLOTS; i++) {
            Material mat = mats.get(i % mats.size());
            int max = Math.max(1, mat.getMaxStackSize());
            // Partial stacks (a little over half a stack) so nothing merges away trivially.
            items[i] = new ItemStack(mat, Math.max(1, max / 2 + 1));
        }
        return items;
    }

    // -------------------------------------------------------------------------
    // Repack path
    // -------------------------------------------------------------------------

    private static Stats benchRepack(int iterations, int warmup) {
        Fixture template = buildRepackFixture();

        for (int i = 0; i < warmup; i++) {
            consume(runPack(template.copy()));
        }

        long[] samples = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            // packIntoBundles mutates its bundle inputs, so each iteration runs on a fresh deep copy.
            // The copy is made outside the timed region so only the algorithm is measured.
            Fixture f = template.copy();
            long t0 = System.nanoTime();
            List<ItemStack> out = runPack(f);
            samples[i] = System.nanoTime() - t0;
            consume(out);
        }
        return summarize(samples);
    }

    /** The unified pack-and-sort pipeline, mirroring {@code InventorySortService.packAndSort}. */
    private static List<ItemStack> runPack(Fixture f) {
        Map<SortKey, Long> loosePool = new LinkedHashMap<>();
        Map<SortKey, ItemStack> samples = new LinkedHashMap<>();
        List<ItemStack> toSort = new ArrayList<>();
        for (ItemStack is : f.inv) {
            if (is == null) continue;
            SortKey key = new SortKey(is, SortingMethod.NAME);
            loosePool.merge(key, (long) is.getAmount(), Long::sum);
            samples.putIfAbsent(key, is);
        }
        List<ItemStack> leftover = BundlePacker.packIntoBundles(loosePool, samples, f.hotbarBundles, 0);
        toSort.addAll(leftover);
        toSort.addAll(f.hotbarBundles);
        return SortEngine.sortAndMerge(toSort, SortingMethod.NAME);
    }

    /**
     * A pooling-heavy repack worst case: every main slot holds a distinct partial stack and every
     * hotbar bundle is pre-loaded with several more distinct partials, so the pool spans many types.
     */
    static Fixture buildRepackFixture() {
        // Enough distinct types to fill the main slots and seed the bundles without repeats.
        List<Material> mats = bundleableMaterials(REPACK_MAIN_SLOTS + REPACK_HOTBAR_BUNDLES * 4);
        int m = 0;

        List<ItemStack> inv = new ArrayList<>(REPACK_MAIN_SLOTS);
        for (int i = 0; i < REPACK_MAIN_SLOTS; i++) {
            Material mat = mats.get(m++ % mats.size());
            int max = Math.max(1, mat.getMaxStackSize());
            inv.add(new ItemStack(mat, Math.max(1, max / 2 + 1)));
        }

        List<ItemStack> hotbarBundles = new ArrayList<>(REPACK_HOTBAR_BUNDLES);
        for (int b = 0; b < REPACK_HOTBAR_BUNDLES; b++) {
            List<ItemStack> contents = new ArrayList<>(4);
            for (int c = 0; c < 4; c++) {
                Material mat = mats.get(m++ % mats.size());
                int max = Math.max(1, mat.getMaxStackSize());
                contents.add(new ItemStack(mat, Math.max(1, max / 4 + 1)));
            }
            hotbarBundles.add(bundleOf(contents));
        }
        return new Fixture(inv, hotbarBundles);
    }

    private static ItemStack bundleOf(List<ItemStack> contents) {
        ItemStack b = new ItemStack(Material.BUNDLE, 1);
        BundleMeta meta = (BundleMeta) b.getItemMeta();
        meta.setItems(contents);
        b.setItemMeta(meta);
        return b;
    }

    /** A deep-copyable repack input pair. */
    static final class Fixture {
        final List<ItemStack> inv;
        final List<ItemStack> hotbarBundles;

        Fixture(List<ItemStack> inv, List<ItemStack> hotbarBundles) {
            this.inv = inv;
            this.hotbarBundles = hotbarBundles;
        }

        Fixture copy() {
            return new Fixture(deepCopy(inv), deepCopy(hotbarBundles));
        }

        private static List<ItemStack> deepCopy(List<ItemStack> src) {
            List<ItemStack> out = new ArrayList<>(src.size());
            for (ItemStack is : src) {
                out.add(is == null ? null : is.clone());
            }
            return out;
        }
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    /**
     * Collect up to {@code count} distinct bundle-eligible materials (stackable, non-bundle,
     * non-shulker). Falls back to repeating the list if the registry yields fewer than requested.
     */
    private static List<Material> bundleableMaterials(int count) {
        List<Material> mats = new ArrayList<>();
        for (Material mat : Material.values()) {
            if (mats.size() >= count) break;
            if (!mat.isItem() || mat.isLegacy()) continue;
            if (BundlePacker.canBundle(new ItemStack(mat, 1))) {
                mats.add(mat);
            }
        }
        if (mats.isEmpty()) {
            // Extremely defensive: guarantee at least one usable type.
            mats.add(Material.COBBLESTONE);
        }
        return mats;
    }

    private static Stats summarize(long[] nanos) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        long total = 0;
        for (long n : nanos) total += n;
        int n = sorted.length;
        double min = sorted[0] / 1000.0;
        double max = sorted[n - 1] / 1000.0;
        double median = percentile(sorted, 50) / 1000.0;
        double p95 = percentile(sorted, 95) / 1000.0;
        return new Stats(min, median, p95, max, total, n);
    }

    private static long percentile(long[] sortedAsc, int pct) {
        if (sortedAsc.length == 0) return 0;
        int idx = (int) Math.ceil(pct / 100.0 * sortedAsc.length) - 1;
        idx = Math.max(0, Math.min(sortedAsc.length - 1, idx));
        return sortedAsc[idx];
    }

    /** Black-hole to keep the JIT from eliminating the benchmarked work. */
    private static final long[] SINK = new long[1];

    private static void consume(List<ItemStack> out) {
        SINK[0] += out.size();
    }
}
