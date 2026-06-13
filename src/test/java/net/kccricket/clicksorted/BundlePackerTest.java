package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.SortKey;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.sort.BundlePacker;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BundlePacker}: weight math, the {@code canBundle} rules, and the pool-and-pack
 * core ({@link BundlePacker#packIntoBundles}) — full stacks returned loose, a ≤32-weight remainder
 * routed to the fullest bundle that fits, a heavier remainder left loose, duplicate consolidation,
 * lightest-first placement, and entry caps.
 *
 * Uses MockBukkit (via AbstractClickSortedTest) because BundleMeta is a server-side object.
 */
class BundlePackerTest extends AbstractClickSortedTest {

    // -------------------------------------------------------------------------
    // Test helpers
    // -------------------------------------------------------------------------

    /** Build a bundle ItemStack pre-loaded with the given contents. */
    private static ItemStack bundle(ItemStack... contents) {
        ItemStack b = new ItemStack(Material.BUNDLE, 1);
        if (contents.length > 0) {
            BundleMeta meta = (BundleMeta) b.getItemMeta();
            meta.setItems(Arrays.asList(contents));
            b.setItemMeta(meta);
        }
        return b;
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

    // -------------------------------------------------------------------------
    // bundleUsedWeight / distinctEntries / isExistingEntry
    // -------------------------------------------------------------------------

    @Test
    void bundleUsedWeight_withItems_sumsCorrectly() {
        ItemStack b = bundle(new ItemStack(Material.COBBLESTONE, 10), new ItemStack(Material.ENDER_PEARL, 3));
        assertEquals(22, BundlePacker.bundleUsedWeight((BundleMeta) b.getItemMeta()));
    }

    @Test
    void distinctEntries_twoSameType_countsAsOne() {
        ItemStack b = bundle(new ItemStack(Material.STONE, 5), new ItemStack(Material.STONE, 3));
        assertEquals(1, BundlePacker.distinctEntries((BundleMeta) b.getItemMeta()));
    }

    @Test
    void distinctEntries_twoDifferentTypes_countsAsTwo() {
        ItemStack b = bundle(new ItemStack(Material.STONE, 5), new ItemStack(Material.DIRT, 3));
        assertEquals(2, BundlePacker.distinctEntries((BundleMeta) b.getItemMeta()));
    }

    @Test
    void isExistingEntry_matchingType_true() {
        ItemStack b = bundle(new ItemStack(Material.GRAVEL, 10));
        assertTrue(BundlePacker.isExistingEntry((BundleMeta) b.getItemMeta(), new ItemStack(Material.GRAVEL, 5)));
    }

    @Test
    void isExistingEntry_differentType_false() {
        ItemStack b = bundle(new ItemStack(Material.GRAVEL, 10));
        assertFalse(BundlePacker.isExistingEntry((BundleMeta) b.getItemMeta(), new ItemStack(Material.SAND, 5)));
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

        assertEquals(40, BundlePacker.bundleUsedWeight((BundleMeta) bundles.get(0).getItemMeta()),
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

        assertEquals(40, BundlePacker.bundleUsedWeight((BundleMeta) bundles.get(0).getItemMeta()),
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

        assertEquals(2, BundlePacker.distinctEntries((BundleMeta) bundles.get(0).getItemMeta()),
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
}
