package net.kccricket.clicksorted;

import net.kccricket.clicksorted.sort.BundlePacker;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BundlePacker}: weight math, the {@code canBundle} rules, and the pool-and-repack
 * model — full stacks to the inventory, a ≤32-weight remainder to the fullest bundle that fits, a
 * heavier remainder left loose, duplicate consolidation, lightest-first placement, and idempotency.
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
    // repack — remainder routing
    // -------------------------------------------------------------------------

    @Test
    void repack_remainderAtMost32_goesIntoBundle() {
        // 10 loose cobblestone (weight 10 ≤ 32) → the whole remainder lands in the bundle.
        ItemStack b = bundle();
        List<ItemStack> inv = new ArrayList<>(Arrays.asList(b, new ItemStack(Material.COBBLESTONE, 10)));

        int freed = BundlePacker.repack(inv, List.of(), 0);

        assertEquals(1, freed, "The loose stack's slot is freed");
        assertNull(inv.get(1), "Loose cobblestone slot is cleared");
        assertEquals(10, bundleAmount(inv.get(0), Material.COBBLESTONE), "Remainder is inside the bundle");
    }

    @Test
    void repack_remainderOver32_staysLoose() {
        // 44 loose cobblestone (weight 44 > 32) → stays loose; the bundle is left empty.
        ItemStack b = bundle();
        List<ItemStack> inv = new ArrayList<>(Arrays.asList(b, new ItemStack(Material.COBBLESTONE, 44)));

        int freed = BundlePacker.repack(inv, List.of(), 0);

        assertEquals(0, freed);
        assertEquals(44, looseAmount(inv, Material.COBBLESTONE), "Heavy remainder stays loose");
        assertTrue(contents(inv.get(0)).isEmpty(), "Bundle should not be spent on a >32 remainder");
    }

    @Test
    void repack_topsUpInventoryToFullStackFromBundle() {
        // 50 loose + 20 in a bundle = 70 → one full inventory stack (64) and a 6-weight remainder
        // pulled back into the bundle. (Requirement #3.)
        ItemStack b = bundle(new ItemStack(Material.COBBLESTONE, 20));
        List<ItemStack> inv = new ArrayList<>(Arrays.asList(b, new ItemStack(Material.COBBLESTONE, 50)));

        BundlePacker.repack(inv, List.of(), 0);

        assertEquals(64, looseAmount(inv, Material.COBBLESTONE), "Inventory rounds up to a full stack");
        assertEquals(1, looseSlots(inv, Material.COBBLESTONE), "Exactly one full loose stack");
        assertEquals(6, bundleAmount(inv.get(0), Material.COBBLESTONE), "Bundle keeps the 6 remainder");
    }

    @Test
    void repack_consolidatesDuplicateBundleStacks_whenSumAtMost32() {
        // Two bundles holding 10 + 15 of the same item → one bundle holds 25, the other empties.
        ItemStack a = bundle(new ItemStack(Material.COBBLESTONE, 10));
        ItemStack b = bundle(new ItemStack(Material.COBBLESTONE, 15));
        List<ItemStack> inv = new ArrayList<>(Arrays.asList(a, b));

        BundlePacker.repack(inv, List.of(), 0);

        int total = bundleAmount(inv.get(0), Material.COBBLESTONE) + bundleAmount(inv.get(1), Material.COBBLESTONE);
        assertEquals(25, total, "All 25 retained across the bundles");
        boolean oneEmpty = contents(inv.get(0)).isEmpty() || contents(inv.get(1)).isEmpty();
        boolean oneHasAll = bundleAmount(inv.get(0), Material.COBBLESTONE) == 25
                || bundleAmount(inv.get(1), Material.COBBLESTONE) == 25;
        assertTrue(oneEmpty && oneHasAll, "25 should collapse into a single bundle stack");
    }

    @Test
    void repack_consolidatesDuplicateBundleStacks_toInventoryWhenSumOver32() {
        // Two bundles holding 15 + 20 = 35 (> 32) → 35 loose in the inventory, both bundles empty.
        ItemStack a = bundle(new ItemStack(Material.COBBLESTONE, 15));
        ItemStack b = bundle(new ItemStack(Material.COBBLESTONE, 20));
        List<ItemStack> inv = new ArrayList<>(Arrays.asList(a, b, null, null));

        BundlePacker.repack(inv, List.of(), 0);

        assertEquals(35, looseAmount(inv, Material.COBBLESTONE), "Combined 35 lands loose");
        assertEquals(1, looseSlots(inv, Material.COBBLESTONE), "As a single loose stack");
        assertTrue(contents(inv.get(0)).isEmpty() && contents(inv.get(1)).isEmpty(), "Both bundles emptied");
    }

    @Test
    void repack_loneBundleRemainderStaysPut() {
        // A lone 25-weight stack already in a bundle stays where it is (no churn).
        ItemStack b = bundle(new ItemStack(Material.COBBLESTONE, 25));
        List<ItemStack> inv = new ArrayList<>(Arrays.asList(b, null));

        int freed = BundlePacker.repack(inv, List.of(), 0);

        assertEquals(0, freed);
        assertEquals(25, bundleAmount(inv.get(0), Material.COBBLESTONE), "Stays in the bundle");
        assertEquals(0, looseAmount(inv, Material.COBBLESTONE), "Nothing loose");
    }

    // -------------------------------------------------------------------------
    // repack — best-fit / lightest-first / current-bundle preference
    // -------------------------------------------------------------------------

    @Test
    void repack_bestFitPicksFullerBundleThenFallsBackWhenItCannotFit() {
        // Two empty bundles; three remainders of distinct 64-stack types: 20, 20, 30.
        // Lightest-first: two 20s pile into bin #0 (best-fit prefers the fuller bin), then the 30
        // no longer fits bin #0 (room 24 < 30) and lands in bin #1.
        ItemStack a = bundle();
        ItemStack b = bundle();
        List<ItemStack> inv = new ArrayList<>(Arrays.asList(a, b,
                new ItemStack(Material.DIRT, 20),
                new ItemStack(Material.SAND, 20),
                new ItemStack(Material.COBBLESTONE, 30)));

        BundlePacker.repack(inv, List.of(), 0);

        assertEquals(40, BundlePacker.bundleUsedWeight((BundleMeta) inv.get(0).getItemMeta()),
                "The two light remainders consolidate into the first bundle");
        assertEquals(30, bundleAmount(inv.get(1), Material.COBBLESTONE),
                "The heavy remainder spills into the second bundle when the first can't hold it");
    }

    @Test
    void repack_lightestFirstAdmitsTwoLightOverOneHeavy() {
        // One empty bundle (64 room). Remainders 30, 20, 20 (distinct types). Lightest-first packs
        // both 20s (40), leaving no room for the 30, which stays loose — maximizing slots freed.
        ItemStack b = bundle();
        List<ItemStack> inv = new ArrayList<>(Arrays.asList(b,
                new ItemStack(Material.COBBLESTONE, 30),
                new ItemStack(Material.DIRT, 20),
                new ItemStack(Material.SAND, 20),
                null));

        BundlePacker.repack(inv, List.of(), 0);

        assertEquals(40, BundlePacker.bundleUsedWeight((BundleMeta) inv.get(0).getItemMeta()),
                "Two 20-weight remainders fit");
        assertEquals(30, looseAmount(inv, Material.COBBLESTONE), "The heavy 30 stays loose");
    }

    @Test
    void repack_prefersCurrentBundle_noChurn() {
        // Two bundles each holding a different item ≤32. Each remainder returns to its own bundle,
        // even though after placing the first the second bundle could best-fit elsewhere.
        ItemStack a = bundle(new ItemStack(Material.COBBLESTONE, 10));
        ItemStack b = bundle(new ItemStack(Material.DIRT, 25));
        List<ItemStack> inv = new ArrayList<>(Arrays.asList(a, b));

        BundlePacker.repack(inv, List.of(), 0);

        assertEquals(10, bundleAmount(inv.get(0), Material.COBBLESTONE), "Cobblestone stays in its bundle");
        assertEquals(25, bundleAmount(inv.get(1), Material.DIRT), "Dirt stays in its bundle");
        assertEquals(0, bundleAmount(inv.get(0), Material.DIRT), "No churn into the other bundle");
    }

    // -------------------------------------------------------------------------
    // repack — caps, idempotency, overflow
    // -------------------------------------------------------------------------

    @Test
    void repack_entryCapFull_leavesRemainderLoose() {
        // entryCap = 2; three distinct light remainders. Two fit; the third exceeds the cap → loose.
        ItemStack b = bundle();
        List<ItemStack> inv = new ArrayList<>(Arrays.asList(b,
                new ItemStack(Material.COBBLESTONE, 5),
                new ItemStack(Material.DIRT, 5),
                new ItemStack(Material.SAND, 5),
                null));

        BundlePacker.repack(inv, List.of(), 2);

        assertEquals(2, BundlePacker.distinctEntries((BundleMeta) inv.get(0).getItemMeta()),
                "Only two entries fit under the cap");
        int looseTotal = looseAmount(inv, Material.COBBLESTONE)
                + looseAmount(inv, Material.DIRT) + looseAmount(inv, Material.SAND);
        assertEquals(5, looseTotal, "The capped-out remainder stays loose");
    }

    @Test
    void repack_isIdempotent() {
        // A mixed scenario: a second run on the repacked layout changes nothing.
        ItemStack a = bundle(new ItemStack(Material.COBBLESTONE, 10));
        ItemStack b = bundle();
        List<ItemStack> inv = new ArrayList<>(Arrays.asList(a, b,
                new ItemStack(Material.COBBLESTONE, 50),
                new ItemStack(Material.DIRT, 15),
                null, null));

        BundlePacker.repack(inv, List.of(), 0);
        List<ItemStack> snapshot = new ArrayList<>(inv);

        int freedSecond = BundlePacker.repack(inv, List.of(), 0);

        assertEquals(0, freedSecond, "A second run frees nothing");
        assertEquals(snapshot.size(), inv.size());
        for (int i = 0; i < snapshot.size(); i++) {
            assertEquals(snapshot.get(i), inv.get(i), "Slot " + i + " unchanged on the second run");
        }
    }

    @Test
    void repack_overflowSpillsIntoEmptySlot() {
        // A bundle holding two full stacks (128) and no loose copies → two full inventory stacks,
        // each needing a free slot.
        ItemStack b = bundle(new ItemStack(Material.COBBLESTONE, 64), new ItemStack(Material.COBBLESTONE, 64));
        List<ItemStack> inv = new ArrayList<>(Arrays.asList(b, null, null));

        BundlePacker.repack(inv, List.of(), 0);

        assertEquals(128, looseAmount(inv, Material.COBBLESTONE), "Both full stacks land in the inventory");
        assertEquals(2, looseSlots(inv, Material.COBBLESTONE), "Spread across two slots");
        assertTrue(contents(inv.get(0)).isEmpty(), "Bundle emptied of full stacks");
    }

    @Test
    void repack_overflowFallsBackToBundleWhenNoFreeSlot() {
        // Same 128 in a bundle, but only one free inventory slot: one full stack goes loose, the
        // excess stack falls back into the bundle rather than being dropped.
        ItemStack b = bundle(new ItemStack(Material.COBBLESTONE, 64), new ItemStack(Material.COBBLESTONE, 64));
        List<ItemStack> inv = new ArrayList<>(Arrays.asList(b, null));

        BundlePacker.repack(inv, List.of(), 0);

        assertEquals(64, looseAmount(inv, Material.COBBLESTONE), "One full stack fits the lone free slot");
        assertEquals(64, bundleAmount(inv.get(0), Material.COBBLESTONE), "The excess stays in the bundle");
    }

    // -------------------------------------------------------------------------
    // repack — hotbar bundles
    // -------------------------------------------------------------------------

    @Test
    void repack_hotbarBundleAbsorbsMainItem() {
        // A hotbar bundle is a valid bin: a 10-weight main remainder lands in it.
        ItemStack hotbar = bundle();
        List<ItemStack> inv = new ArrayList<>(Arrays.asList((ItemStack) null, new ItemStack(Material.COBBLESTONE, 10)));
        List<ItemStack> hotbarBundles = new ArrayList<>(List.of(hotbar));

        int freed = BundlePacker.repack(inv, hotbarBundles, 0);

        assertEquals(1, freed, "Main slot freed");
        assertEquals(10, bundleAmount(hotbarBundles.get(0), Material.COBBLESTONE), "Hotbar bundle absorbed it");
        assertEquals(0, looseAmount(inv, Material.COBBLESTONE));
    }

    @Test
    void repack_noBundlesNoEligibleItems_returnsZero() {
        List<ItemStack> inv = new ArrayList<>(Arrays.asList(
                new ItemStack(Material.DIAMOND_SWORD, 1), null));

        assertEquals(0, BundlePacker.repack(inv, List.of(), 0));
        assertEquals(Material.DIAMOND_SWORD, inv.get(0).getType(), "Ineligible item untouched");
    }
}
