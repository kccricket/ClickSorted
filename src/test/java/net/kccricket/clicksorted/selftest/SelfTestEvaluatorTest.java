package net.kccricket.clicksorted.selftest;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SelfTestEvaluatorTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void compareLayout_passesForIdenticalLayouts() {
        ItemStack[] a = {new ItemStack(Material.STONE, 5), null};
        ItemStack[] b = {new ItemStack(Material.STONE, 5), null};
        assertTrue(SelfTestEvaluator.compareLayout(a, b).isEmpty());
    }

    @Test
    void compareLayout_failsOnAmountMismatch() {
        ItemStack[] a = {new ItemStack(Material.STONE, 5)};
        ItemStack[] b = {new ItemStack(Material.STONE, 6)};
        assertTrue(SelfTestEvaluator.compareLayout(a, b).isPresent());
    }

    @Test
    void compareLayout_treatsNullAndAirAsEquallyEmpty() {
        ItemStack[] a = {null};
        ItemStack[] b = {new ItemStack(Material.AIR)};
        assertTrue(SelfTestEvaluator.compareLayout(a, b).isEmpty());
    }

    @Test
    void compareCensus_passesWhenConserved() {
        ItemCensus before = ItemCensus.of(new ItemStack[]{new ItemStack(Material.STONE, 5)});
        ItemCensus after = ItemCensus.of(new ItemStack[]{new ItemStack(Material.STONE, 5)});
        assertTrue(SelfTestEvaluator.compareCensus(before, after, List.of()).isEmpty());
    }

    @Test
    void compareCensus_failsOnLoss() {
        ItemCensus before = ItemCensus.of(new ItemStack[]{new ItemStack(Material.STONE, 5)});
        ItemCensus after = ItemCensus.of(new ItemStack[]{new ItemStack(Material.STONE, 3)});
        assertTrue(SelfTestEvaluator.compareCensus(before, after, List.of()).isPresent());
    }

    @Test
    void compareCensus_droppedItemsAreCredited() {
        ItemCensus before = ItemCensus.of(new ItemStack[]{new ItemStack(Material.STONE, 5)});
        ItemCensus after = ItemCensus.of(new ItemStack[]{new ItemStack(Material.STONE, 3)});
        List<ItemStack> dropped = List.of(new ItemStack(Material.STONE, 2));
        assertTrue(SelfTestEvaluator.compareCensus(before, after, dropped).isEmpty());
    }

    @Test
    void noGapBeforeNonEmpty_passesForContiguousLayout() {
        ItemStack[] contents = {new ItemStack(Material.STONE, 1), new ItemStack(Material.STONE, 1), null};
        assertTrue(SelfTestEvaluator.noGapBeforeNonEmpty(contents, List.of(0, 1, 2)).isEmpty());
    }

    @Test
    void noGapBeforeNonEmpty_failsWhenNonEmptyFollowsEmpty() {
        ItemStack[] contents = {new ItemStack(Material.STONE, 1), null, new ItemStack(Material.STONE, 1)};
        assertTrue(SelfTestEvaluator.noGapBeforeNonEmpty(contents, List.of(0, 1, 2)).isPresent());
    }

    @Test
    void noDuplicatePartialStacks_failsWhenSameKeySplitAcrossTwoPartialSlots() {
        ItemStack a1 = new ItemStack(Material.STONE, 10);
        ItemStack a2 = new ItemStack(Material.STONE, 20);
        ItemStack[] contents = {a1, a2};
        assertTrue(SelfTestEvaluator.noDuplicatePartialStacks(contents, Set.of(0, 1)).isPresent());
    }

    @Test
    void noDuplicatePartialStacks_passesWhenOneStackIsFull() {
        ItemStack full = new ItemStack(Material.STONE, 64);
        ItemStack partial = new ItemStack(Material.STONE, 10);
        ItemStack[] contents = {full, partial};
        assertTrue(SelfTestEvaluator.noDuplicatePartialStacks(contents, Set.of(0, 1)).isEmpty());
    }

    @Test
    void protectedSlotsUnchanged_failsWhenProtectedSlotDiffers() {
        ItemStack[] before = {new ItemStack(Material.STONE, 5)};
        ItemStack[] after = {new ItemStack(Material.STONE, 6)};
        assertTrue(SelfTestEvaluator.protectedSlotsUnchanged(before, after, Set.of(0)).isPresent());
    }

    @Test
    void bundleCapacityValid_failsWhenOverweight() {
        ItemStack bundle = new ItemStack(Material.BUNDLE, 1);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        // 64 non-stackable-weight items -> weight 64 each easily exceeds capacity when doubled up
        meta.setItems(List.of(new ItemStack(Material.STONE, 64), new ItemStack(Material.STONE, 64)));
        bundle.setItemMeta(meta);
        ItemStack[] contents = {bundle};
        assertTrue(SelfTestEvaluator.bundleCapacityValid(contents, 0).isPresent());
    }

    @Test
    void bundleCapacityValid_passesForEmptyBundle() {
        ItemStack bundle = new ItemStack(Material.BUNDLE, 1);
        ItemStack[] contents = {bundle};
        assertTrue(SelfTestEvaluator.bundleCapacityValid(contents, 0).isEmpty());
    }

    @Test
    void idempotent_passesWhenSecondPassMatchesFirst() {
        ItemStack[] first = {new ItemStack(Material.STONE, 5)};
        ItemStack[] second = {new ItemStack(Material.STONE, 5)};
        assertTrue(SelfTestEvaluator.idempotent(first, second).isEmpty());
    }

    @Test
    void idempotent_failsWhenSecondPassDiffers() {
        ItemStack[] first = {new ItemStack(Material.STONE, 5)};
        ItemStack[] second = {new ItemStack(Material.STONE, 4)};
        Optional<String> result = SelfTestEvaluator.idempotent(first, second);
        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("not idempotent"));
    }
}
