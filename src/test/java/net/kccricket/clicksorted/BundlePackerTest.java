package net.kccricket.clicksorted;

import net.kccricket.clicksorted.sort.BundlePacker;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BundlePacker}: weight math, FFD packing, entry-cap enforcement,
 * canBundle rules, and atomicity (no stack splitting).
 *
 * Uses MockBukkit (via AbstractClickSortedTest) because BundleMeta is a server-side object.
 */
class BundlePackerTest extends AbstractClickSortedTest {

    // -------------------------------------------------------------------------
    // stackWeight
    // -------------------------------------------------------------------------

    @Test
    void stackWeight_fullStack64MaxSize_equalsAmount() {
        // 64-stackable: 1 unit each → a stack of 10 = weight 10
        assertEquals(10, BundlePacker.stackWeight(new ItemStack(Material.COBBLESTONE, 10)));
    }

    @Test
    void stackWeight_partialStack16MaxSize() {
        // 16-stackable (e.g. ender pearls): 4 units each → stack of 3 = weight 12
        assertEquals(12, BundlePacker.stackWeight(new ItemStack(Material.ENDER_PEARL, 3)));
    }

    @Test
    void stackWeight_fullStack64MaxSizeEquals64() {
        // A full stack of cobblestone = 64 weight = fills exactly one bundle
        assertEquals(64, BundlePacker.stackWeight(new ItemStack(Material.COBBLESTONE, 64)));
    }

    // -------------------------------------------------------------------------
    // bundleUsedWeight
    // -------------------------------------------------------------------------

    @Test
    void bundleUsedWeight_emptyBundle_zero() {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        assertNotNull(meta, "MockBukkit should provide BundleMeta for BUNDLE items");
        assertEquals(0, BundlePacker.bundleUsedWeight(meta));
    }

    @Test
    void bundleUsedWeight_withItems_sumsCorrectly() {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        assertNotNull(meta);

        // 10 cobblestone (weight 10) + 3 ender pearl (weight 12) = 22
        meta.setItems(List.of(new ItemStack(Material.COBBLESTONE, 10), new ItemStack(Material.ENDER_PEARL, 3)));
        assertEquals(22, BundlePacker.bundleUsedWeight(meta));
    }

    // -------------------------------------------------------------------------
    // distinctEntries
    // -------------------------------------------------------------------------

    @Test
    void distinctEntries_emptyBundle_zero() {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        assertNotNull(meta);
        assertEquals(0, BundlePacker.distinctEntries(meta));
    }

    @Test
    void distinctEntries_twoSameType_countsAsOne() {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        assertNotNull(meta);
        meta.setItems(List.of(new ItemStack(Material.STONE, 5), new ItemStack(Material.STONE, 3)));
        assertEquals(1, BundlePacker.distinctEntries(meta));
    }

    @Test
    void distinctEntries_twoDifferentTypes_countsAsTwo() {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        assertNotNull(meta);
        meta.setItems(List.of(new ItemStack(Material.STONE, 5), new ItemStack(Material.DIRT, 3)));
        assertEquals(2, BundlePacker.distinctEntries(meta));
    }

    // -------------------------------------------------------------------------
    // isExistingEntry
    // -------------------------------------------------------------------------

    @Test
    void isExistingEntry_matchingType_true() {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        assertNotNull(meta);
        meta.setItems(List.of(new ItemStack(Material.GRAVEL, 10)));
        assertTrue(BundlePacker.isExistingEntry(meta, new ItemStack(Material.GRAVEL, 5)));
    }

    @Test
    void isExistingEntry_differentType_false() {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        assertNotNull(meta);
        meta.setItems(List.of(new ItemStack(Material.GRAVEL, 10)));
        assertFalse(BundlePacker.isExistingEntry(meta, new ItemStack(Material.SAND, 5)));
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
        // Tools have maxStackSize = 1 → excluded (no slot savings possible)
        assertFalse(BundlePacker.canBundle(new ItemStack(Material.DIAMOND_SWORD, 1)));
    }

    @Test
    void canBundle_null_false() {
        assertFalse(BundlePacker.canBundle(null));
    }

    // -------------------------------------------------------------------------
    // pack — integration
    // -------------------------------------------------------------------------

    @Test
    void pack_noBundles_returnsUnchanged() {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Material.COBBLESTONE, 10));
        items.add(new ItemStack(Material.DIRT, 5));

        List<ItemStack> result = BundlePacker.pack(items, 12);

        assertSame(items, result, "Without bundles the original list should be returned");
    }

    @Test
    void pack_noPartials_returnsUnchanged() {
        // Full stack of cobblestone = amount 64 = maxStackSize → not a partial
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Material.BUNDLE, 1));
        items.add(new ItemStack(Material.COBBLESTONE, 64));

        List<ItemStack> result = BundlePacker.pack(items, 12);

        assertSame(items, result, "Without partial stacks the original list should be returned");
    }

    @Test
    void pack_partialAbsorbedIntoEmptyBundle() {
        // 1 empty bundle + 1 partial stack of cobblestone → partial is packed, list shrinks by 1
        ItemStack bundle = new ItemStack(Material.BUNDLE, 1);
        ItemStack partial = new ItemStack(Material.COBBLESTONE, 10); // partial: 10 < 64

        List<ItemStack> items = new ArrayList<>();
        items.add(bundle);
        items.add(partial);

        List<ItemStack> result = BundlePacker.pack(items, 12);

        assertEquals(1, result.size(), "Partial should have been absorbed; list should shrink to 1 (the bundle)");
        assertEquals(Material.BUNDLE, result.get(0).getType());

        // Verify the bundle now contains the cobblestone
        BundleMeta meta = (BundleMeta) result.get(0).getItemMeta();
        assertNotNull(meta);
        assertFalse(meta.getItems().isEmpty(), "Bundle should contain the packed cobblestone");
        assertEquals(Material.COBBLESTONE, meta.getItems().get(0).getType());
        assertEquals(10, meta.getItems().get(0).getAmount());
    }

    @Test
    void pack_multiplePartialsAbsorbed() {
        ItemStack bundle = new ItemStack(Material.BUNDLE, 1);
        ItemStack partial1 = new ItemStack(Material.COBBLESTONE, 10); // weight 10
        ItemStack partial2 = new ItemStack(Material.DIRT, 5);         // weight 5

        List<ItemStack> items = new ArrayList<>();
        items.add(bundle);
        items.add(partial1);
        items.add(partial2);

        List<ItemStack> result = BundlePacker.pack(items, 12);

        // Both partials should fit (total weight 15 << 64)
        assertEquals(1, result.size(), "Both partials should be absorbed into the bundle");
        BundleMeta meta = (BundleMeta) result.get(0).getItemMeta();
        assertNotNull(meta);
        assertEquals(2, BundlePacker.distinctEntries(meta));
    }

    @Test
    void pack_bundleWeightLimitPreventsOverfill() {
        // Bundle already has 60 weight consumed; try to pack a partial of weight 10 → won't fit
        ItemStack bundle = new ItemStack(Material.BUNDLE, 1);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        assertNotNull(meta);
        // 60 cobblestone = weight 60
        meta.setItems(List.of(new ItemStack(Material.COBBLESTONE, 60)));
        bundle.setItemMeta(meta);

        ItemStack partial = new ItemStack(Material.DIRT, 10); // weight 10 — needs 10 free, only 4 left

        List<ItemStack> items = new ArrayList<>();
        items.add(bundle);
        items.add(partial);

        List<ItemStack> result = BundlePacker.pack(items, 12);

        assertSame(items, result, "Partial should not fit — weight would exceed 64");
    }

    @Test
    void pack_entryCap_preventsExceedingLimit() {
        // Bundle has 12 distinct entries already; a 13th entry with cap=12 must not be added
        ItemStack bundle = new ItemStack(Material.BUNDLE, 1);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        assertNotNull(meta);

        // Fill bundle with 12 distinct items, 1 of each (weight 12 total)
        List<ItemStack> bundleContents = new ArrayList<>();
        Material[] mats = {
            Material.COBBLESTONE, Material.DIRT, Material.SAND, Material.GRAVEL,
            Material.OAK_LOG, Material.STONE, Material.DIORITE, Material.GRANITE,
            Material.ANDESITE, Material.CLAY, Material.NETHERRACK, Material.SOUL_SAND
        };
        for (Material m : mats) {
            bundleContents.add(new ItemStack(m, 1));
        }
        meta.setItems(bundleContents);
        bundle.setItemMeta(meta);

        ItemStack newPartial = new ItemStack(Material.GRAVEL, 5); // same type as one existing entry
        // Wait: GRAVEL is already in the bundle, so it's an "existing entry" and should merge in
        // Let's use a type NOT in the bundle
        ItemStack newDistinctPartial = new ItemStack(Material.ENDER_PEARL, 2); // 13th distinct type

        List<ItemStack> items = new ArrayList<>();
        items.add(bundle);
        items.add(newDistinctPartial);

        List<ItemStack> result = BundlePacker.pack(items, 12);

        assertSame(items, result, "13th distinct entry should be rejected when cap=12");
    }

    @Test
    void pack_existingEntryMergesWithoutCountingAgainstCap() {
        // Bundle already has 12 entries; adding more of a type already in the bundle is allowed
        ItemStack bundle = new ItemStack(Material.BUNDLE, 1);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        assertNotNull(meta);

        List<ItemStack> bundleContents = new ArrayList<>();
        Material[] mats = {
            Material.COBBLESTONE, Material.DIRT, Material.SAND, Material.GRAVEL,
            Material.OAK_LOG, Material.STONE, Material.DIORITE, Material.GRANITE,
            Material.ANDESITE, Material.CLAY, Material.NETHERRACK, Material.SOUL_SAND
        };
        for (Material m : mats) {
            bundleContents.add(new ItemStack(m, 1));
        }
        meta.setItems(bundleContents);
        bundle.setItemMeta(meta);

        // Partial of COBBLESTONE — already an entry, should merge in even though cap=12 is reached
        ItemStack sameTypePart = new ItemStack(Material.COBBLESTONE, 5);

        List<ItemStack> items = new ArrayList<>();
        items.add(bundle);
        items.add(sameTypePart);

        List<ItemStack> result = BundlePacker.pack(items, 12);

        assertEquals(1, result.size(), "Same-type partial should merge into existing entry");
        BundleMeta resultMeta = (BundleMeta) result.get(0).getItemMeta();
        assertNotNull(resultMeta);
        // distinctEntries should still be 12
        assertEquals(12, BundlePacker.distinctEntries(resultMeta));
    }

    @Test
    void pack_entryCapZero_unlimitedEntries() {
        // entryCap = 0 → weight-only limit; many distinct types should all pack in
        ItemStack bundle = new ItemStack(Material.BUNDLE, 1);

        List<ItemStack> items = new ArrayList<>();
        items.add(bundle);
        // 13 distinct partial stacks, each weight 1 (total weight 13 << 64)
        Material[] mats = {
            Material.COBBLESTONE, Material.DIRT, Material.SAND, Material.GRAVEL,
            Material.OAK_LOG, Material.STONE, Material.DIORITE, Material.GRANITE,
            Material.ANDESITE, Material.CLAY, Material.NETHERRACK, Material.SOUL_SAND, Material.NETHER_BRICK
        };
        for (Material m : mats) {
            items.add(new ItemStack(m, 1));
        }

        List<ItemStack> result = BundlePacker.pack(items, 0); // cap = 0 → unlimited

        assertEquals(1, result.size(), "All 13 partials should be packed when cap=0");
        BundleMeta meta = (BundleMeta) result.get(0).getItemMeta();
        assertNotNull(meta);
        assertEquals(13, BundlePacker.distinctEntries(meta));
    }

    @Test
    void pack_stacksNotSplit() {
        // A partial that cannot fit is left intact — it should not have its amount reduced
        ItemStack bundle = new ItemStack(Material.BUNDLE, 1);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        assertNotNull(meta);
        meta.setItems(List.of(new ItemStack(Material.COBBLESTONE, 60))); // 4 weight remaining
        bundle.setItemMeta(meta);

        ItemStack tooBig = new ItemStack(Material.DIRT, 10); // weight 10 > 4 remaining

        List<ItemStack> items = new ArrayList<>();
        items.add(bundle);
        items.add(tooBig);

        List<ItemStack> result = BundlePacker.pack(items, 12);

        // The partial must remain in the list with its original amount
        boolean found = result.stream().anyMatch(
                is -> is != null && is.getType() == Material.DIRT && is.getAmount() == 10);
        assertTrue(found, "Partial that cannot fit must remain whole (never split)");
    }

    @Test
    void pack_partialAbsorbedIntoPartiallyFilledBundle() {
        // Bundle already has some content; we top it up without emptying it first
        ItemStack bundle = new ItemStack(Material.BUNDLE, 1);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        assertNotNull(meta);
        meta.setItems(List.of(new ItemStack(Material.COBBLESTONE, 20))); // weight 20 used
        bundle.setItemMeta(meta);

        ItemStack partial = new ItemStack(Material.DIRT, 5); // weight 5

        List<ItemStack> items = new ArrayList<>();
        items.add(bundle);
        items.add(partial);

        List<ItemStack> result = BundlePacker.pack(items, 12);

        assertEquals(1, result.size(), "Partial should be absorbed into the partially-filled bundle");

        BundleMeta resultMeta = (BundleMeta) result.get(0).getItemMeta();
        assertNotNull(resultMeta);
        // Original cobblestone must still be there
        boolean hasCobble = resultMeta.getItems().stream()
                .anyMatch(is -> is != null && is.getType() == Material.COBBLESTONE);
        assertTrue(hasCobble, "Existing bundle contents must be preserved");
        // Dirt must have been added
        boolean hasDirt = resultMeta.getItems().stream()
                .anyMatch(is -> is != null && is.getType() == Material.DIRT);
        assertTrue(hasDirt, "New partial must have been added to the bundle");
    }
}
