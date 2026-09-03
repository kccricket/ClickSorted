package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.SortKey;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.sort.BundlePacker;
import org.bukkit.Material;
import org.bukkit.block.Beehive;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.BeeMock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BundlePacker}: weight math, the {@code canBundle} rules, and the pool-and-pack
 * core ({@link BundlePacker#packIntoBundles}) — full stacks returned loose, a ≤32-weight remainder
 * routed to the fullest bundle that fits, a heavier remainder left loose, duplicate consolidation,
 * lightest-first placement, entry caps, nested bundles as bins, and bee-filled beehives.
 *
 * Uses MockBukkit (via AbstractClickSortedTest) because BundleMeta is a server-side object.
 *
 * <p>The bee-filled-hive tests ({@code beeHive}, which populates a {@code Beehive} block state via
 * {@link org.mockbukkit.mockbukkit.entity.BeeMock}) currently self-skip on this MockBukkit version —
 * bee entity construction throws {@code UnimplementedOperationException}, which JUnit reports as
 * skipped rather than failed. The bee-less counterparts ({@code *_emptyBeehive_*}) still exercise the
 * material-gate and {@code hasBlockState} guards unconditionally, so {@code isBeeFilled}'s false path
 * is covered either way; only the true (bees-present) path depends on MockBukkit's entity support.
 */
class BundlePackerTest extends AbstractClickSortedTest {

    // -------------------------------------------------------------------------
    // Test helpers
    // -------------------------------------------------------------------------

    /** Build a BUNDLE ItemStack pre-loaded with the given contents. */
    private static ItemStack bundle(ItemStack... contents) {
        return bundle(Material.BUNDLE, contents);
    }

    /**
     * Pool the given loose items and pack them into {@code bundles} (mutated in place), returning the
     * leftover loose stacks. Mirrors how {@code InventorySortService.packAndSort} drives the packer.
     */
    private static List<ItemStack> pack(List<ItemStack> loose, List<ItemStack> bundles, int stackLimit) {
        Map<SortKey, Long> pool = new LinkedHashMap<>();
        Map<SortKey, ItemStack> samples = new LinkedHashMap<>();
        for (ItemStack is : loose) {
            if (is == null) continue;
            SortKey key = new SortKey(is, SortingMethod.NAME);
            pool.merge(key, (long) is.getAmount(), (a, b) -> Long.sum(a, b));
            samples.putIfAbsent(key, is);
        }
        return BundlePacker.packIntoBundles(pool, samples, bundles, stackLimit);
    }

    /** The contents of a bundle ItemStack. */
    private static List<ItemStack> contents(ItemStack bundleItem) {
        return ((BundleMeta) bundleItem.getItemMeta()).getItems();
    }

    /** Total amount of {@code mat} held inside a bundle. */
    private static int bundleAmount(ItemStack bundleItem, Material mat) {
        int total = 0;
        for (ItemStack is : contents(bundleItem)) {
            if (is != null && is.getType() == mat) total += is.getAmount();
        }
        return total;
    }

    /** Total amount of {@code mat} loose in a slot-aligned inventory list. */
    private static int looseAmount(List<ItemStack> inv, Material mat) {
        int total = 0;
        for (ItemStack is : inv) {
            if (is != null && is.getType() == mat) total += is.getAmount();
        }
        return total;
    }

    private static int looseSlots(List<ItemStack> inv, Material mat) {
        int slots = 0;
        for (ItemStack is : inv) {
            if (is != null && is.getType() == mat) slots++;
        }
        return slots;
    }

    // -------------------------------------------------------------------------
    // stackWeight
    // -------------------------------------------------------------------------

    @Test
    void stackWeight_fullStack64MaxSize_equalsAmount() {
        assertEquals(10, BundlePacker.stackWeight(new ItemStack(Material.COBBLESTONE, 10)));
    }

    @Test
    void stackWeight_partialStack16MaxSize() {
        assertEquals(12, BundlePacker.stackWeight(new ItemStack(Material.ENDER_PEARL, 3)));
    }

    @Test
    void stackWeight_fullStack64MaxSizeEquals64() {
        assertEquals(64, BundlePacker.stackWeight(new ItemStack(Material.COBBLESTONE, 64)));
    }

    @Test
    void stackWeight_emptyNestedBundle_isSixteenthOfCapacity() {
        // Vanilla BUNDLE_IN_BUNDLE_WEIGHT = 1/16 of capacity (64/16 = 4), not a whole bundle (64).
        assertEquals(4, BundlePacker.stackWeight(bundle()));
    }

    @Test
    void stackWeight_nestedBundleWithContents_addsContentWeight() {
        // 4 (nested-bundle cost) + 32 (32 cobblestone at weight 1 each) = 36.
        assertEquals(36, BundlePacker.stackWeight(bundle(new ItemStack(Material.COBBLESTONE, 32))));
    }

    @Test
    void stackWeight_doublyNestedBundle_accumulates() {
        // Outer: 4 + (inner: 4 + 10) = 18.
        assertEquals(18, BundlePacker.stackWeight(bundle(bundle(new ItemStack(Material.COBBLESTONE, 10)))));
    }

    @Test
    void stackWeight_nestedBundleNeverExceedsCapacity() {
        // A nested bundle stuffed with a full 64-weight stack would compute to 4 + 64 = 68; clamps to 64.
        assertEquals(64, BundlePacker.stackWeight(bundle(new ItemStack(Material.COBBLESTONE, 64))));
    }

    @Test
    void stackWeight_maxStackSizeComponentHonoured() {
        // 3 units at a component max-stack-size of 16 → weight 3 × (64/16) = 12, not 3 × (64/64) = 3.
        ItemStack cobble = new ItemStack(Material.COBBLESTONE, 3);
        var meta = cobble.getItemMeta();
        meta.setMaxStackSize(16);
        cobble.setItemMeta(meta);
        assertEquals(12, BundlePacker.stackWeight(cobble));
    }

    @Test
    void stackWeight_beeFilledBeehive_isCapacity() {
        // A beehive/bee nest holding bees costs a whole bundle in vanilla, not the material's ordinary
        // per-item weight (64/64 = 1).
        assertEquals(64, BundlePacker.stackWeight(beeHive(Material.BEEHIVE, 1)));
        assertEquals(64, BundlePacker.stackWeight(beeHive(Material.BEE_NEST, 3)));
    }

    @Test
    void stackWeight_emptyBeehive_usesMaxStackSize() {
        // The bee check must not misfire on an ordinary, bee-less hive: back to the material formula.
        assertEquals(3, BundlePacker.stackWeight(new ItemStack(Material.BEEHIVE, 3)));
    }

    private static int usedWeight(ItemStack bundle) {
        int t = 0;
        for (ItemStack is : ((BundleMeta) bundle.getItemMeta()).getItems())
            if (is != null) t += BundlePacker.stackWeight(is);
        return t;
    }

    private static int distinctTypes(ItemStack bundle) {
        java.util.Set<Material> seen = new java.util.HashSet<>();
        for (ItemStack is : ((BundleMeta) bundle.getItemMeta()).getItems())
            if (is != null) seen.add(is.getType());
        return seen.size();
    }

    /** Build a bundle ItemStack of the given type pre-loaded with the given contents. */
    private static ItemStack bundle(Material type, ItemStack... contents) {
        ItemStack b = new ItemStack(type, 1);
        if (contents.length > 0) {
            BundleMeta meta = (BundleMeta) b.getItemMeta();
            meta.setItems(Arrays.asList(contents));
            b.setItemMeta(meta);
        }
        return b;
    }

    /** Build a BEEHIVE/BEE_NEST ItemStack whose stored block state holds {@code beeCount} bees. */
    private ItemStack beeHive(Material type, int beeCount) {
        ItemStack hive = new ItemStack(type, 1);
        if (!(hive.getItemMeta() instanceof BlockStateMeta meta)) {
            throw new IllegalStateException(type + " has no BlockStateMeta on this server");
        }
        if (!(meta.getBlockState() instanceof Beehive state)) {
            throw new IllegalStateException(type + " block state is not a Beehive");
        }
        for (int i = 0; i < beeCount; i++) {
            state.addEntity(new BeeMock(server, UUID.randomUUID()));
        }
        meta.setBlockState(state);
        hive.setItemMeta(meta);
        return hive;
    }

    // -------------------------------------------------------------------------
    // isBundle
    // -------------------------------------------------------------------------

    @Test
    void isBundle_bundle_true() {
        assertTrue(BundlePacker.isBundle(Material.BUNDLE));
    }

    @Test
    void isBundle_coloredBundle_true() {
        assertTrue(BundlePacker.isBundle(Material.RED_BUNDLE));
    }

    @Test
    void isBundle_cobblestone_false() {
        assertFalse(BundlePacker.isBundle(Material.COBBLESTONE));
    }

    @Test
    void isBundle_null_false() {
        assertFalse(BundlePacker.isBundle(null));
    }

    // -------------------------------------------------------------------------
    // canBundle
    // -------------------------------------------------------------------------

    @Test
    void canBundle_normalItem_true() {
        assertTrue(BundlePacker.canBundle(new ItemStack(Material.COBBLESTONE, 5)));
    }

    @Test
    void canBundle_bundle_false() {
        assertFalse(BundlePacker.canBundle(new ItemStack(Material.BUNDLE, 1)));
    }

    @Test
    void canBundle_coloredBundle_false() {
        assertFalse(BundlePacker.canBundle(new ItemStack(Material.RED_BUNDLE, 1)));
    }

    @Test
    void canBundle_shulkerBox_false() {
        assertFalse(BundlePacker.canBundle(new ItemStack(Material.SHULKER_BOX, 1)));
        assertFalse(BundlePacker.canBundle(new ItemStack(Material.WHITE_SHULKER_BOX, 1)));
    }

    @Test
    void canBundle_nonStackable_false() {
        assertFalse(BundlePacker.canBundle(new ItemStack(Material.DIAMOND_SWORD, 1)));
    }

    @Test
    void canBundle_null_false() {
        assertFalse(BundlePacker.canBundle(null));
    }

    @Test
    void canBundle_maxStackSizeComponentOne_false() {
        // Material default stack size is > 1, but a component pins it to 1 (non-stackable).
        ItemStack cobble = new ItemStack(Material.COBBLESTONE, 1);
        var meta = cobble.getItemMeta();
        meta.setMaxStackSize(1);
        cobble.setItemMeta(meta);
        assertFalse(BundlePacker.canBundle(cobble));
    }

    @Test
    void canBundle_maxStackSizeComponentSixteen_true() {
        ItemStack cobble = new ItemStack(Material.COBBLESTONE, 1);
        var meta = cobble.getItemMeta();
        meta.setMaxStackSize(16);
        cobble.setItemMeta(meta);
        assertTrue(BundlePacker.canBundle(cobble));
    }

    @Test
    void canBundle_beeFilledBeehive_false() {
        // Already costs a whole bundle and could never actually be repacked; pooling it would just
        // unpack it from wherever it currently sits for no benefit.
        assertFalse(BundlePacker.canBundle(beeHive(Material.BEEHIVE, 1)));
    }

    @Test
    void canBundle_emptyBeehive_true() {
        // The exclusion is bee-conditional, not a material-wide ban on beehives.
        assertTrue(BundlePacker.canBundle(new ItemStack(Material.BEEHIVE, 1)));
    }

    // -------------------------------------------------------------------------
    // packIntoBundles — remainder routing
    // -------------------------------------------------------------------------

    @Test
    void pack_remainderAtMost32_goesIntoBundle() {
        // 10 loose cobblestone (weight 10 ≤ 32) → the whole remainder lands in the bundle.
        List<ItemStack> bundles = new ArrayList<>(List.of(bundle()));
        List<ItemStack> leftover = pack(List.of(new ItemStack(Material.COBBLESTONE, 10)), bundles, 0);

        assertEquals(0, looseSlots(leftover, Material.COBBLESTONE), "Nothing left loose");
        assertEquals(10, bundleAmount(bundles.get(0), Material.COBBLESTONE), "Remainder is inside the bundle");
    }

    @Test
    void pack_remainderOver32_staysLoose() {
        // 44 loose cobblestone (weight 44 > 32) → stays loose; the bundle is left empty.
        List<ItemStack> bundles = new ArrayList<>(List.of(bundle()));
        List<ItemStack> leftover = pack(List.of(new ItemStack(Material.COBBLESTONE, 44)), bundles, 0);

        assertEquals(44, looseAmount(leftover, Material.COBBLESTONE), "Heavy remainder stays loose");
        assertTrue(contents(bundles.get(0)).isEmpty(), "Bundle should not be spent on a >32 remainder");
    }

    @Test
    void pack_topsUpToFullStackFromBundle() {
        // 50 loose + 20 in a bundle = 70 → one full loose stack (64) and a 6-weight remainder
        // pulled back into the bundle.
        List<ItemStack> bundles = new ArrayList<>(List.of(bundle(new ItemStack(Material.COBBLESTONE, 20))));
        List<ItemStack> leftover = pack(List.of(new ItemStack(Material.COBBLESTONE, 50)), bundles, 0);

        assertEquals(64, looseAmount(leftover, Material.COBBLESTONE), "Rounds up to a full loose stack");
        assertEquals(1, looseSlots(leftover, Material.COBBLESTONE), "Exactly one full loose stack");
        assertEquals(6, bundleAmount(bundles.get(0), Material.COBBLESTONE), "Bundle keeps the 6 remainder");
    }

    @Test
    void pack_consolidatesDuplicateBundleStacks_whenSumAtMost32() {
        // Two bundles holding 10 + 15 of the same item → one bundle holds 25, the other empties.
        List<ItemStack> bundles = new ArrayList<>(List.of(
                bundle(new ItemStack(Material.COBBLESTONE, 10)),
                bundle(new ItemStack(Material.COBBLESTONE, 15))));
        List<ItemStack> leftover = pack(List.of(), bundles, 0);

        assertEquals(0, looseSlots(leftover, Material.COBBLESTONE), "Nothing left loose");
        int total = bundleAmount(bundles.get(0), Material.COBBLESTONE) + bundleAmount(bundles.get(1), Material.COBBLESTONE);
        assertEquals(25, total, "All 25 retained across the bundles");
        boolean oneEmpty = contents(bundles.get(0)).isEmpty() || contents(bundles.get(1)).isEmpty();
        boolean oneHasAll = bundleAmount(bundles.get(0), Material.COBBLESTONE) == 25
                || bundleAmount(bundles.get(1), Material.COBBLESTONE) == 25;
        assertTrue(oneEmpty && oneHasAll, "25 should collapse into a single bundle stack");
    }

    @Test
    void pack_consolidatesDuplicateBundleStacks_toLooseWhenSumOver32() {
        // Two bundles holding 15 + 20 = 35 (> 32) → 35 left loose, both bundles empty.
        List<ItemStack> bundles = new ArrayList<>(List.of(
                bundle(new ItemStack(Material.COBBLESTONE, 15)),
                bundle(new ItemStack(Material.COBBLESTONE, 20))));
        List<ItemStack> leftover = pack(List.of(), bundles, 0);

        assertEquals(35, looseAmount(leftover, Material.COBBLESTONE), "Combined 35 is left loose");
        assertEquals(1, looseSlots(leftover, Material.COBBLESTONE), "As a single loose stack");
        assertTrue(contents(bundles.get(0)).isEmpty() && contents(bundles.get(1)).isEmpty(), "Both bundles emptied");
    }

    @Test
    void pack_loneBundleRemainderStaysPut() {
        // A lone 25-weight stack already in a bundle stays where it is (no churn).
        List<ItemStack> bundles = new ArrayList<>(List.of(bundle(new ItemStack(Material.COBBLESTONE, 25))));
        List<ItemStack> leftover = pack(List.of(), bundles, 0);

        assertEquals(0, looseAmount(leftover, Material.COBBLESTONE), "Nothing loose");
        assertEquals(25, bundleAmount(bundles.get(0), Material.COBBLESTONE), "Stays in the bundle");
    }

    // -------------------------------------------------------------------------
    // packIntoBundles — best-fit / lightest-first / current-bundle preference
    // -------------------------------------------------------------------------

    @Test
    void pack_bestFitPicksFullerBundleThenFallsBackWhenItCannotFit() {
        // Two empty bundles; three remainders of distinct 64-stack types: 20, 20, 30.
        // Lightest-first: two 20s pile into bin #0 (best-fit prefers the fuller bin), then the 30
        // no longer fits bin #0 (room 24 < 30) and lands in bin #1.
        List<ItemStack> bundles = new ArrayList<>(List.of(bundle(), bundle()));
        pack(List.of(
                new ItemStack(Material.DIRT, 20),
                new ItemStack(Material.SAND, 20),
                new ItemStack(Material.COBBLESTONE, 30)), bundles, 0);

        assertEquals(40, usedWeight(bundles.get(0)),
                "The two light remainders consolidate into the first bundle");
        assertEquals(30, bundleAmount(bundles.get(1), Material.COBBLESTONE),
                "The heavy remainder spills into the second bundle when the first can't hold it");
    }

    @Test
    void pack_lightestFirstAdmitsTwoLightOverOneHeavy() {
        // One empty bundle (64 room). Remainders 30, 20, 20 (distinct types). Lightest-first packs
        // both 20s (40), leaving no room for the 30, which stays loose — maximizing slots freed.
        List<ItemStack> bundles = new ArrayList<>(List.of(bundle()));
        List<ItemStack> leftover = pack(List.of(
                new ItemStack(Material.COBBLESTONE, 30),
                new ItemStack(Material.DIRT, 20),
                new ItemStack(Material.SAND, 20)), bundles, 0);

        assertEquals(40, usedWeight(bundles.get(0)),
                "Two 20-weight remainders fit");
        assertEquals(30, looseAmount(leftover, Material.COBBLESTONE), "The heavy 30 stays loose");
    }

    @Test
    void pack_prefersCurrentBundle_noChurn() {
        // Two bundles each holding a different item ≤32. Each remainder returns to its own bundle,
        // even though after placing the first the second bundle could best-fit elsewhere.
        List<ItemStack> bundles = new ArrayList<>(List.of(
                bundle(new ItemStack(Material.COBBLESTONE, 10)),
                bundle(new ItemStack(Material.DIRT, 25))));
        pack(List.of(), bundles, 0);

        assertEquals(10, bundleAmount(bundles.get(0), Material.COBBLESTONE), "Cobblestone stays in its bundle");
        assertEquals(25, bundleAmount(bundles.get(1), Material.DIRT), "Dirt stays in its bundle");
        assertEquals(0, bundleAmount(bundles.get(0), Material.DIRT), "No churn into the other bundle");
    }

    // -------------------------------------------------------------------------
    // packIntoBundles — caps, full stacks, empty pool
    // -------------------------------------------------------------------------

    @Test
    void pack_entryCapFull_leavesRemainderLoose() {
        // stackLimit = 2; three distinct light remainders. Two fit; the third exceeds the cap → loose.
        List<ItemStack> bundles = new ArrayList<>(List.of(bundle()));
        List<ItemStack> leftover = pack(List.of(
                new ItemStack(Material.COBBLESTONE, 5),
                new ItemStack(Material.DIRT, 5),
                new ItemStack(Material.SAND, 5)), bundles, 2);

        assertEquals(2, distinctTypes(bundles.get(0)),
                "Only two entries fit under the cap");
        int looseTotal = looseAmount(leftover, Material.COBBLESTONE)
                + looseAmount(leftover, Material.DIRT) + looseAmount(leftover, Material.SAND);
        assertEquals(5, looseTotal, "The capped-out remainder stays loose");
    }

    @Test
    void pack_bundleFullStacks_emittedLoose() {
        // A bundle holding two full stacks (128) and no loose copies → two full loose stacks,
        // bundle emptied (the sort lays the loose stacks out).
        List<ItemStack> bundles = new ArrayList<>(List.of(
                bundle(new ItemStack(Material.COBBLESTONE, 64), new ItemStack(Material.COBBLESTONE, 64))));
        List<ItemStack> leftover = pack(List.of(), bundles, 0);

        assertEquals(128, looseAmount(leftover, Material.COBBLESTONE), "Both full stacks are returned loose");
        assertEquals(2, looseSlots(leftover, Material.COBBLESTONE), "Spread across two stacks");
        assertTrue(contents(bundles.get(0)).isEmpty(), "Bundle emptied of full stacks");
    }

    @Test
    void pack_emptyPoolNoBundles_returnsEmpty() {
        List<ItemStack> leftover = pack(List.of(), new ArrayList<>(), 0);
        assertTrue(leftover.isEmpty(), "Nothing to pack → no leftover stacks");
    }

    @Test
    void pack_coloredBundleUsedAsBin() {
        // A RED_BUNDLE should be recognized as a bin and receive the remainder, just like BUNDLE.
        List<ItemStack> bundles = new ArrayList<>(List.of(bundle(Material.RED_BUNDLE)));
        List<ItemStack> leftover = pack(List.of(new ItemStack(Material.COBBLESTONE, 10)), bundles, 0);

        assertEquals(0, looseSlots(leftover, Material.COBBLESTONE), "Nothing left loose");
        assertEquals(10, bundleAmount(bundles.get(0), Material.COBBLESTONE), "Remainder is inside the colored bundle");
    }

    // -------------------------------------------------------------------------
    // packIntoBundles — a bundle containing another bundle is still a usable bin
    // -------------------------------------------------------------------------

    @Test
    void pack_bundleContainingBundle_stillAcceptsRemainder() {
        // The bin holds one empty nested bundle (weight 4 of 64) — before the fix a nested bundle was
        // charged the full 64, so this bin looked completely full and every remainder stayed loose.
        List<ItemStack> bundles = new ArrayList<>(List.of(bundle(bundle())));
        List<ItemStack> leftover = pack(List.of(new ItemStack(Material.DIRT, 20)), bundles, 0);

        assertEquals(0, looseSlots(leftover, Material.DIRT), "Nothing left loose");
        assertEquals(20, bundleAmount(bundles.get(0), Material.DIRT), "Remainder packed into the outer bundle");
    }

    @Test
    void pack_nestedBundleIsNeverPooledOrUnpacked() {
        // The nested bundle already holds 10 cobblestone; packing 20 more loose cobblestone must not
        // dissolve the nested bundle's contents into the outer bundle's top-level entries.
        ItemStack inner = bundle(new ItemStack(Material.COBBLESTONE, 10));
        List<ItemStack> bundles = new ArrayList<>(List.of(bundle(inner)));
        pack(List.of(new ItemStack(Material.COBBLESTONE, 20)), bundles, 0);

        ItemStack outer = bundles.get(0);
        assertEquals(20, bundleAmount(outer, Material.COBBLESTONE),
                "Only the loose 20 lands as a top-level entry in the outer bundle");
        List<ItemStack> outerContents = contents(outer);
        assertEquals(1, outerContents.stream().filter(is -> BundlePacker.isBundle(is.getType())).count(),
                "The nested bundle is still present, untouched");
        ItemStack nested = outerContents.stream().filter(is -> BundlePacker.isBundle(is.getType())).findFirst().orElseThrow();
        assertEquals(10, bundleAmount(nested, Material.COBBLESTONE), "The nested bundle's own contents are unchanged");
    }

    @Test
    void pack_fullNestedBundle_blocksFurtherPacking() {
        // A nested bundle stuffed to capacity (weight 64) leaves the outer bundle with zero room.
        List<ItemStack> bundles = new ArrayList<>(List.of(bundle(bundle(new ItemStack(Material.COBBLESTONE, 64)))));
        List<ItemStack> leftover = pack(List.of(new ItemStack(Material.DIRT, 10)), bundles, 0);

        assertEquals(10, looseAmount(leftover, Material.DIRT), "No room left; the fix must not over-admit");
        assertEquals(0, bundleAmount(bundles.get(0), Material.DIRT), "Outer bundle unchanged");
    }

    @Test
    void pack_maxStackSizeComponentLimitsLooseStackSize() {
        // 40 units of an item whose component caps its stack at 16: 2 full loose stacks of 16 (not 64)
        // plus an 8-unit remainder (weight 8*(64/16)=32 ≤ MAX_PACK_WEIGHT) that gets bundled.
        ItemStack sample = new ItemStack(Material.COBBLESTONE, 1);
        var meta = sample.getItemMeta();
        meta.setMaxStackSize(16);
        sample.setItemMeta(meta);

        List<ItemStack> bundles = new ArrayList<>(List.of(bundle()));
        ItemStack loose = sample.clone();
        loose.setAmount(40);
        List<ItemStack> leftover = pack(List.of(loose), bundles, 0);

        assertEquals(2, looseSlots(leftover, Material.COBBLESTONE), "Two full stacks");
        assertEquals(32, looseAmount(leftover, Material.COBBLESTONE), "2 × 16 = 32 loose");
        assertEquals(8, bundleAmount(bundles.get(0), Material.COBBLESTONE), "8-unit remainder bundled");
    }

    @Test
    void pack_nestedBundleBinIsIdempotent() {
        // Re-running pack over its own output must be a no-op, including on the newly-reachable
        // nested-bundle-as-bin path.
        List<ItemStack> bundles = new ArrayList<>(List.of(bundle(bundle())));
        List<ItemStack> firstLeftover = pack(List.of(new ItemStack(Material.DIRT, 20)), bundles, 0);

        List<ItemStack> secondLeftover = pack(firstLeftover, bundles, 0);

        assertEquals(0, looseAmount(secondLeftover, Material.DIRT), "Second pass changes nothing");
        assertEquals(20, bundleAmount(bundles.get(0), Material.DIRT), "Dirt stays put across passes");
    }

    @Test
    void pack_beeFilledBeehiveInBundle_isRetainedAndChargedFull() {
        // A bin already holding a bee-filled hive (weight 64) has no room left, and the hive itself
        // must survive the pass untouched — the whole point of the retain-not-pool exclusion.
        List<ItemStack> bundles = new ArrayList<>(List.of(bundle(beeHive(Material.BEEHIVE, 1))));
        List<ItemStack> leftover = pack(List.of(new ItemStack(Material.DIRT, 10)), bundles, 0);

        assertEquals(10, looseAmount(leftover, Material.DIRT), "No room left in the bin");
        assertEquals(1, contents(bundles.get(0)).stream().filter(is -> is.getType() == Material.BEEHIVE).count(),
                "The hive is still present in the bundle");
    }
}
