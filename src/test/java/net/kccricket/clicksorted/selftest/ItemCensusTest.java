package net.kccricket.clicksorted.selftest;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ItemCensusTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ItemStack bundleOf(ItemStack... contents) {
        ItemStack b = new ItemStack(Material.BUNDLE, 1);
        BundleMeta meta = (BundleMeta) b.getItemMeta();
        meta.setItems(List.of(contents));
        b.setItemMeta(meta);
        return b;
    }

    @Test
    void of_countsLooseItemsAcrossSlots() {
        ItemStack[] slots = {new ItemStack(Material.STONE, 5), null, new ItemStack(Material.STONE, 3)};
        ItemCensus census = ItemCensus.of(slots);
        assertEquals(1, census.leafCounts().size());
        assertTrue(census.leafCounts().values().stream().anyMatch(v -> v == 8));
    }

    @Test
    void of_recursesIntoBundleContents_andCountsTheBundleAsAContainer() {
        ItemStack bundle = bundleOf(new ItemStack(Material.DIRT, 4));
        ItemStack[] slots = {bundle};
        ItemCensus census = ItemCensus.of(slots);
        assertEquals(1L, census.containerCounts().get(Material.BUNDLE));
        long dirtTotal = census.leafCounts().entrySet().stream()
                .filter(e -> e.getKey().getMaterial() == Material.DIRT)
                .mapToLong(java.util.Map.Entry::getValue).sum();
        assertEquals(4L, dirtTotal);
    }

    @Test
    void of_includesCursorAndOffhand() {
        ItemStack[] slots = {};
        ItemCensus census = ItemCensus.of(slots, new ItemStack(Material.DIAMOND, 1), new ItemStack(Material.STONE, 2));
        long diamond = census.leafCounts().entrySet().stream()
                .filter(e -> e.getKey().getMaterial() == Material.DIAMOND).mapToLong(java.util.Map.Entry::getValue).sum();
        long stone = census.leafCounts().entrySet().stream()
                .filter(e -> e.getKey().getMaterial() == Material.STONE).mapToLong(java.util.Map.Entry::getValue).sum();
        assertEquals(1L, diamond);
        assertEquals(2L, stone);
    }

    @Test
    void matches_trueForIdenticalCensuses() {
        ItemStack[] a = {new ItemStack(Material.STONE, 5)};
        ItemStack[] b = {new ItemStack(Material.STONE, 5)};
        assertTrue(ItemCensus.of(a).matches(ItemCensus.of(b)));
    }

    @Test
    void matches_falseWhenAmountChanges_detectsLossAndDuplication() {
        ItemStack[] before = {new ItemStack(Material.STONE, 5)};
        ItemStack[] lost = {new ItemStack(Material.STONE, 3)};
        ItemStack[] duped = {new ItemStack(Material.STONE, 10)};
        assertFalse(ItemCensus.of(before).matches(ItemCensus.of(lost)));
        assertFalse(ItemCensus.of(before).matches(ItemCensus.of(duped)));
    }

    @Test
    void plusDropped_foldsInGroundItems() {
        // before == after + dropped
        ItemStack[] before = {new ItemStack(Material.STONE, 5)};
        ItemStack[] after = {new ItemStack(Material.STONE, 2)};
        ItemCensus beforeCensus = ItemCensus.of(before);
        ItemCensus afterCensus = ItemCensus.of(after);
        assertFalse(beforeCensus.matches(afterCensus));
        List<ItemStack> dropped = List.of(new ItemStack(Material.STONE, 3));
        assertTrue(afterCensus.plusDropped(dropped).matches(beforeCensus));
    }

    @Test
    void diff_isEmptyWhenMatching() {
        ItemStack[] a = {new ItemStack(Material.STONE, 5)};
        assertTrue(ItemCensus.of(a).diff(ItemCensus.of(a)).isEmpty());
    }
}
